package com.medireport.portal.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.medireport.portal.util.Backup
import com.medireport.portal.util.FileStore
import com.medireport.portal.util.Mailer
import com.medireport.portal.util.PdfPreview
import com.medireport.portal.util.ReportData
import com.medireport.portal.util.ReportDsl
import com.medireport.portal.util.ReportPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

enum class SaveResult { OK, DUPLICATE_CODE, ERROR }

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    val store: FileStore,
    val prefs: Prefs
) {
    private val patientDao = db.patientDao()
    private val reportDao = db.reportDao()
    private val templateDao = db.templateDao()
    private val setupDao = db.pageSetupDao()
    val smsDao = db.smsDao()
    suspend fun patientByCode(code: String) = patientDao.byCode(code.trim())
    private val backup = Backup(context, db, store)

    val patients: Flow<List<Patient>> = patientDao.observeAll()
    val patientCount: Flow<Int> = patientDao.observeCount()
    val reportCount: Flow<Int> = reportDao.observeCount()
    val templateCount: Flow<Int> = templateDao.observeCount()
    val allReports: Flow<List<ReportWithPatient>> = reportDao.observeAllWithPatient()
    val templates: Flow<List<Template>> = templateDao.observeAll()
    val pageSetup: Flow<PageSetup?> = setupDao.observe()

    suspend fun seed() {
        prefs.ensureAdmin()
        if (templateDao.count() == 0) templateDao.insertAll(DefaultTemplates.all())
        if (setupDao.get() == null) setupDao.upsert(PageSetup())
    }

    // ---------------------------------------------------------------- patients
    fun observePatient(id: Long) = patientDao.observe(id)
    fun observeReports(patientId: Long) = reportDao.observeByPatient(patientId)
    suspend fun getPatient(id: Long) = patientDao.getById(id)
    suspend fun patientLogin(code: String, phone: String) = patientDao.login(code.trim(), phone.trim())

    suspend fun nextPatientCode(): String {
        var n = patientDao.count() + 1
        while (true) {
            val code = "PT-" + n.toString().padStart(4, '0')
            if (patientDao.codeCount(code, 0) == 0) return code
            n++
        }
    }

    suspend fun savePatient(p: Patient): SaveResult = try {
        if (patientDao.codeCount(p.patientCode, p.id) > 0) SaveResult.DUPLICATE_CODE
        else {
            if (p.id == 0L) patientDao.insert(p) else patientDao.update(p)
            SaveResult.OK
        }
    } catch (e: Exception) {
        SaveResult.ERROR
    }

    suspend fun deletePatient(p: Patient) {
        reportDao.getByPatient(p.id).forEach { store.deleteReport(it.fileName) }
        patientDao.delete(p)
    }

    // ---------------------------------------------------------------- reports
    suspend fun getReport(id: Long) = reportDao.getById(id)
    suspend fun publishedReports(patientId: Long) = reportDao.getPublishedByPatient(patientId)

    suspend fun addUploadedReport(patientId: Long, title: String, uri: Uri, publish: Boolean): Boolean {
        val (name, mime) = store.importDocument(uri) ?: return false
        reportDao.insert(Report(patientId = patientId, title = title, fileName = name, mimeType = mime, published = publish))
        return true
    }

    suspend fun setPublished(reportId: Long, published: Boolean) = reportDao.setPublished(reportId, published)
    suspend fun setAllPublished(patientId: Long, published: Boolean) = reportDao.setAllPublished(patientId, published)

    suspend fun deleteReport(r: Report) {
        store.deleteReport(r.fileName)
        reportDao.delete(r)
    }

    /** Builds the lab-style PDF from a template and stores it for the patient. */
    suspend fun generateReport(
        patient: Patient,
        template: Template,
        specimen: String,
        consultedBy: String,
        received: String,
        printDate: String,
        values: Map<String, String>,
        title: String,
        letterhead: Boolean,
        publish: Boolean
    ): Report? = withContext(Dispatchers.Default) {
        try {
            val setup = setupDao.get() ?: PageSetup()
            val data = ReportData(
                title = template.title.ifBlank { template.name },
                specimen = specimen, consultedBy = consultedBy, received = received, printDate = printDate,
                items = ReportDsl.parse(template.content), values = values
            )
            val bytes = ReportPdf.build(patient, data, setup, ReportPdf.loadAssets(store, setup), letterhead)
            val out = store.newGeneratedReportFile()
            out.writeBytes(bytes)
            val report = Report(
                patientId = patient.id, title = title, fileName = out.name,
                source = Report.SOURCE_GENERATED, published = publish
            )
            report.copy(id = reportDao.insert(report))
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- templates
    suspend fun getTemplate(id: Long) = templateDao.getById(id)

    suspend fun saveTemplate(id: Long, name: String, title: String, specimen: String, content: String) {
        if (id == 0L) {
            templateDao.insert(Template(name = name.trim(), title = title.trim(), specimen = specimen.trim(), content = content))
        } else {
            val old = templateDao.getById(id) ?: return
            templateDao.update(old.copy(name = name.trim(), title = title.trim(), specimen = specimen.trim(), content = content))
        }
    }

    suspend fun deleteTemplate(t: Template) = templateDao.delete(t)

    suspend fun duplicateTemplate(t: Template) =
        templateDao.insert(t.copy(id = 0, name = t.name + " (copy)", createdAt = System.currentTimeMillis()))

    // ---------------------------------------------------------------- page setup
    suspend fun getPageSetup() = setupDao.get() ?: PageSetup()
    suspend fun savePageSetup(s: PageSetup) = setupDao.upsert(s)
    suspend fun importImage(uri: Uri, maxDim: Int = 1600): String? = store.importImage(uri, maxDim)

    /** Renders a sample report with the (possibly unsaved) setup; returns the PDF file. */
    suspend fun samplePdf(setup: PageSetup, letterhead: Boolean): File? = withContext(Dispatchers.Default) {
        try {
            val (patient, data) = ReportPdf.sample()
            val bytes = ReportPdf.build(patient, data, setup, ReportPdf.loadAssets(store, setup), letterhead)
            val out = File(context.cacheDir, "preview_${System.currentTimeMillis()}.pdf")
            context.cacheDir.listFiles { f -> f.name.startsWith("preview_") }?.forEach { it.delete() }
            out.writeBytes(bytes)
            out
        } catch (e: Exception) {
            null
        }
    }

    suspend fun samplePreview(setup: PageSetup, letterhead: Boolean): Bitmap? = withContext(Dispatchers.Default) {
        samplePdf(setup, letterhead)?.let { PdfPreview.firstPage(it) }
    }

    /** First page of the report as it would print (not saved). */
    suspend fun previewReport(
        patient: Patient, template: Template, specimen: String, consultedBy: String,
        received: String, printDate: String, values: Map<String, String>, letterhead: Boolean
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            val setup = setupDao.get() ?: PageSetup()
            val data = ReportData(
                template.title.ifBlank { template.name }, specimen, consultedBy, received, printDate,
                ReportDsl.parse(template.content), values
            )
            val bytes = ReportPdf.build(patient, data, setup, ReportPdf.loadAssets(store, setup), letterhead)
            val out = File(context.cacheDir, "preview_${System.currentTimeMillis()}.pdf")
            context.cacheDir.listFiles { f -> f.name.startsWith("preview_") }?.forEach { it.delete() }
            out.writeBytes(bytes)
            PdfPreview.firstPage(out)
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- e-mail
    suspend fun clinicName(): String = (setupDao.get()?.clinicName ?: "").ifBlank { "MediReport" }

    private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "report" }

    /** Sends a stored report as an attachment. Returns null on success, otherwise an error message. */
    suspend fun sendReportEmail(report: Report, patient: Patient, to: String, subject: String, body: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val cfg = prefs.getSmtp()
                if (!cfg.isComplete) return@withContext "Mail server is not configured"
                val ext = if (report.mimeType.contains("pdf")) "pdf" else report.fileName.substringAfterLast('.', "bin")
                Mailer.send(
                    cfg, listOf(to), subject, body,
                    store.reportFile(report.fileName), safeName(report.title) + "." + ext, report.mimeType
                )
                null
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
        }

    /** Sends a test message. When it goes to the recovery address and succeeds, recovery becomes active. */
    suspend fun sendTestMail(to: String): String? = withContext(Dispatchers.IO) {
        try {
            val cfg = prefs.getSmtp()
            Mailer.send(cfg, listOf(to), "MediReport test e-mail", "Your mail server settings work.")
            if (to.trim().equals(prefs.recoveryEmail.first(), ignoreCase = true)) {
                prefs.setMailVerified(true)
            }
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    suspend fun recoveryReady() = prefs.recoveryReady()

    /** Mails a 6-digit code to the recovery address. Returns null on success, otherwise an error message. */
    suspend fun sendRecoveryCode(): String? = withContext(Dispatchers.IO) {
        try {
            if (!prefs.recoveryReady()) return@withContext "NOT_READY"
            val cfg = prefs.getSmtp()
            val to = prefs.recoveryEmail.first()
            val code = prefs.createOtp()
            Mailer.send(
                cfg, listOf(to), "MediReport password recovery code",
                "Your MediReport admin password recovery code is: $code\n\nIt is valid for 10 minutes. " +
                    "If you did not request it, ignore this message."
            )
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    suspend fun resetPasswordWithCode(code: String, newPassword: String): OtpResult {
        val r = prefs.verifyOtp(code)
        if (r == OtpResult.OK) prefs.resetAdminPassword(newPassword)
        return r
    }

    // ---------------------------------------------------------------- backup
    suspend fun exportBackup(target: Uri) = backup.export(target)
    suspend fun restoreBackup(source: Uri) = backup.restore(source)
}
