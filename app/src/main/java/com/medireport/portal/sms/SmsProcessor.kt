package com.medireport.portal.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.medireport.portal.App
import com.medireport.portal.data.Report
import com.medireport.portal.data.Repository
import com.medireport.portal.data.SmsRequest
import com.medireport.portal.web.PortalService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single-consumer queue for SMS questions.
 *  - every incoming SMS is stored first (survives a process restart),
 *  - one worker answers them oldest-first, with a pause between replies (carriers throttle bursts),
 *  - identical repeats, per-number floods, stale and over-capacity requests are dropped.
 */
object SmsProcessor {
    const val MAX_PENDING = 300
    const val MAX_AGE_MS = 60 * 60_000L
    const val RATE_WINDOW_MS = 10 * 60_000L
    const val RATE_MAX = 6
    const val DUP_WINDOW_MS = 2 * 60_000L
    const val SEND_GAP_MS = 2_500L
    private const val KEEP_LOG_MS = 30L * 24 * 60 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Stores a new request. Returns false when it was dropped because the queue is full. */
    suspend fun enqueue(repo: Repository, sender: String, body: String): Boolean {
        if (!SmsParser.looksLikeNumber(sender)) return false
        if (repo.smsDao.pendingCount() >= MAX_PENDING) return false
        repo.smsDao.insert(SmsRequest(sender = sender.trim(), body = body.trim().take(300)))
        return true
    }

    /** Starts (or continues) answering. Safe to call any number of times; only one worker runs. */
    fun kick(context: Context) {
        val app = context.applicationContext as App
        scope.launch {
            // a second call just waits for the running worker and then finds nothing left to do
            mutex.withLock { drain(app) }
        }
    }

    private suspend fun drain(app: App) {
        val repo = app.repository
        while (true) {
            val req = repo.smsDao.nextPending() ?: break
            val sent = try {
                handle(app, repo, req)
            } catch (e: Exception) {
                repo.smsDao.finish(req.id, SmsRequest.STATUS_FAILED, e.message.orEmpty().take(120), System.currentTimeMillis())
                false
            }
            if (sent) delay(SEND_GAP_MS)
        }
        repo.smsDao.deleteOlderThan(System.currentTimeMillis() - KEEP_LOG_MS)
    }

    /** @return true when an SMS was actually sent */
    private suspend fun handle(app: App, repo: Repository, req: SmsRequest): Boolean {
        val dao = repo.smsDao
        val now = System.currentTimeMillis()
        suspend fun ignore() = dao.finish(req.id, SmsRequest.STATUS_IGNORED, "", now)

        if (!repo.prefs.getSms().enabled || now - req.receivedAt > MAX_AGE_MS) { ignore(); return false }

        val recent = dao.countFromSince(req.sender, now - RATE_WINDOW_MS, req.id)
        if (recent > RATE_MAX) { ignore(); return false }
        if (recent == RATE_MAX) return reply(app, repo, req, "Too many requests. Please try again after 10 minutes.")

        if (dao.duplicates(req.sender, req.body, now - DUP_WINDOW_MS, req.id) > 0) { ignore(); return false }

        val q = SmsParser.parse(req.body)
        if (q == null) return reply(app, repo, req, USAGE)

        val patient = repo.patientByCode(q.code)
        val proof = q.claimedPhone ?: req.sender
        if (patient == null || !SmsParser.sameNumber(patient.phone, proof)) {
            // same text for unknown ID and wrong number, so nothing about patients is revealed
            return reply(app, repo, req, "Invalid request. " + USAGE)
        }

        val published = repo.publishedReports(patient.id)
        val matched = published.filter { SmsParser.matches(it.title, q.keyword) }
        val link = baseLink(repo)
        val text = when {
            published.isEmpty() -> "No report is published yet. Please contact the center."
            matched.isEmpty() -> "No published report matches '${q.keyword.take(30)}'. Available: " +
                published.take(4).joinToString(", ") { short(it.title) } + "."
            matched.size == 1 -> "Report '${short(matched[0].title)}' (${date(matched[0])}) is READY." + tail(link)
            else -> "${matched.size} reports are READY: " +
                matched.take(4).joinToString("; ") { short(it.title) + " (" + date(it) + ")" } + "." + tail(link)
        }
        return reply(app, repo, req, text)
    }

    private suspend fun reply(app: App, repo: Repository, req: SmsRequest, text: String): Boolean {
        val ok = send(app, req.sender, text)
        repo.smsDao.finish(
            req.id,
            if (ok) SmsRequest.STATUS_DONE else SmsRequest.STATUS_FAILED,
            text, System.currentTimeMillis()
        )
        return ok
    }

    private fun send(app: App, to: String, text: String): Boolean {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val sm = smsManager(app)
            val parts = sm.divideMessage(text)
            if (parts.size > 1) sm.sendMultipartTextMessage(to, null, parts, null, null)
            else sm.sendTextMessage(to, null, text, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(app: App): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) app.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    private suspend fun baseLink(repo: Repository): String? {
        val custom = repo.prefs.getSms().baseUrl
        if (custom.isNotBlank()) return custom
        val st = PortalService.state.value
        if (!st.running) return null
        val ip = PortalService.localAddresses().firstOrNull() ?: return null
        return "http://$ip:${st.port}"
    }

    private fun tail(link: String?) =
        if (link != null) " Download: $link (login with ID and phone number)." else " Please collect it from the center."

    private fun short(t: String) = if (t.length > 40) t.take(37) + "..." else t

    private fun date(r: Report) = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date(r.createdAt))

    private const val USAGE = "Send: ID REPORT (e.g. P-001 CBC). From another number: PHONE ID REPORT."
}
