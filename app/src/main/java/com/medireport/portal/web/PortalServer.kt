package com.medireport.portal.web

import com.medireport.portal.data.Repository
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny HTTP server for patients: log in with Patient ID + phone number (the password) and
 * view / download the reports the admin has published. Nothing else is exposed.
 */
class PortalServer(private val repo: Repository, port: Int) : NanoHTTPD(port) {

    private class Sess(val patientId: Long, @Volatile var expires: Long)
    private class Attempts(@Volatile var fails: Int = 0, @Volatile var lockUntil: Long = 0L)

    private val sessions = ConcurrentHashMap<String, Sess>()
    private val attempts = ConcurrentHashMap<String, Attempts>()
    private val random = SecureRandom()

    private fun now() = System.currentTimeMillis()

    private fun newToken(): String {
        val b = ByteArray(32)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun cookieToken(s: IHTTPSession): String? =
        s.headers["cookie"]?.split(";")?.map { it.trim() }
            ?.firstOrNull { it.startsWith("sid=") }?.removePrefix("sid=")

    private fun currentSession(s: IHTTPSession): Sess? {
        val t = cookieToken(s) ?: return null
        val sess = sessions[t] ?: return null
        if (sess.expires < now()) {
            sessions.remove(t)
            return null
        }
        sess.expires = now() + SESSION_MS
        return sess
    }

    private fun secure(r: Response): Response {
        r.addHeader("Cache-Control", "no-store")
        r.addHeader("X-Content-Type-Options", "nosniff")
        r.addHeader("X-Frame-Options", "DENY")
        r.addHeader("Referrer-Policy", "no-referrer")
        return r
    }

    private fun html(body: String, status: Response.IStatus = Response.Status.OK): Response =
        secure(newFixedLengthResponse(status, "text/html; charset=utf-8", body))

    private fun redirect(to: String): Response {
        val r = newFixedLengthResponse(Response.Status.REDIRECT_SEE_OTHER, MIME_HTML, "")
        r.addHeader("Location", to)
        return secure(r)
    }

    override fun serve(session: IHTTPSession): Response = try {
        route(session)
    } catch (e: Exception) {
        secure(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Server error"))
    }

    private fun route(s: IHTTPSession): Response {
        val uri = s.uri
        val clinic = runBlocking { repo.clinicName() }
        val sess = currentSession(s)

        if (uri == "/" && s.method == Method.GET) {
            return if (sess != null) redirect("/dashboard") else html(PortalPages.login(clinic))
        }

        if (uri == "/login" && s.method == Method.POST) return login(s, clinic)

        if (uri == "/logout" && s.method == Method.POST) {
            cookieToken(s)?.let { sessions.remove(it) }
            val r = redirect("/")
            r.addHeader("Set-Cookie", "sid=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax")
            return r
        }

        if (uri == "/dashboard" && s.method == Method.GET) {
            if (sess == null) return redirect("/")
            val patient = runBlocking { repo.getPatient(sess.patientId) } ?: return redirect("/")
            val reports = runBlocking { repo.publishedReports(patient.id) }
            return html(PortalPages.dashboard(clinic, patient, reports))
        }

        if (uri.startsWith("/report/") && s.method == Method.GET) {
            if (sess == null) return redirect("/")
            val parts = uri.removePrefix("/report/").split("/")
            val id = parts.getOrNull(0)?.toLongOrNull()
            val mode = parts.getOrNull(1)
            if (id == null || (mode != "view" && mode != "download")) return html(PortalPages.notFound(clinic), Response.Status.NOT_FOUND)
            val report = runBlocking { repo.getReport(id) }
            // only the owner can read it, and only while it is published
            if (report == null || report.patientId != sess.patientId || !report.published) {
                return html(PortalPages.notFound(clinic), Response.Status.NOT_FOUND)
            }
            val file = repo.store.reportFile(report.fileName)
            if (!file.exists()) return html(PortalPages.notFound(clinic), Response.Status.NOT_FOUND)
            val ext = if (report.mimeType.contains("pdf")) "pdf" else file.extension.ifEmpty { "bin" }
            val ascii = report.title.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "report" } + "." + ext
            val utf8 = URLEncoder.encode(report.title + "." + ext, "UTF-8").replace("+", "%20")
            val r = newFixedLengthResponse(Response.Status.OK, report.mimeType, FileInputStream(file), file.length())
            val disp = if (mode == "download") "attachment" else "inline"
            r.addHeader("Content-Disposition", "$disp; filename=\"$ascii\"; filename*=UTF-8''$utf8")
            return secure(r)
        }

        return html(PortalPages.notFound(clinic), Response.Status.NOT_FOUND)
    }

    private fun login(s: IHTTPSession, clinic: String): Response {
        val ip = s.remoteIpAddress ?: "unknown"
        val att = attempts.getOrPut(ip) { Attempts() }
        if (att.lockUntil > now()) {
            val secs = (att.lockUntil - now() + 999) / 1000
            return html(PortalPages.login(clinic, "Too many attempts. Try again in $secs seconds. / অনেকবার ভুল হয়েছে, $secs সেকেন্ড পরে চেষ্টা করুন।"))
        }
        val files = HashMap<String, String>()
        s.parseBody(files)
        val id = s.parms["id"]?.trim().orEmpty()
        val phone = s.parms["phone"]?.trim().orEmpty()
        val patient = if (id.isNotEmpty() && phone.isNotEmpty()) runBlocking { repo.patientLogin(id, phone) } else null
        if (patient == null) {
            att.fails++
            if (att.fails >= MAX_FAILS) {
                att.fails = 0
                att.lockUntil = now() + LOCK_MS
            }
            return html(PortalPages.login(clinic, "Invalid Patient ID or phone number. / রোগীর আইডি বা ফোন নম্বর ভুল।"))
        }
        att.fails = 0
        // drop expired sessions
        val t = now()
        sessions.entries.removeIf { it.value.expires < t }
        val token = newToken()
        sessions[token] = Sess(patient.id, t + SESSION_MS)
        val r = redirect("/dashboard")
        r.addHeader("Set-Cookie", "sid=$token; Path=/; Max-Age=${SESSION_MS / 1000}; HttpOnly; SameSite=Lax")
        return r
    }

    companion object {
        const val SESSION_MS = 30 * 60_000L
        const val MAX_FAILS = 5
        const val LOCK_MS = 60_000L
    }
}
