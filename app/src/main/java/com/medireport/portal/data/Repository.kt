package com.medireport.portal.data

import android.content.Context
import android.net.Uri
import com.medireport.portal.util.Backup
import com.medireport.portal.util.FileStore
import com.medireport.portal.util.PdfGenerator
import com.medireport.portal.util.TemplateEngine
import kotlinx.coroutines.flow.Flow
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
    private val pdf = PdfGenerator(context, store)
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

    // ---------- patients ----------
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

    // ---------- reports ----------
    suspend fun addUploadedReport(patientId: Long, title: String, uri: Uri): Boolean {
        val (name, mime) = store.importDocument(uri) ?: return false
        reportDao.insert(Report(patientId = patientId, title = title, fileName = name, mimeType = mime))
        return true
    }

    suspend fun deleteReport(r: Report) {
        store.deleteReport(r.fileName)
        reportDao.delete(r)
    }

    /** Fills the template, renders it to PDF with the page setup and attaches it to the patient. */
    suspend fun generateReport(
        patient: Patient,
        template: Template,
        values: Map<String, String>,
        title: String
    ): Report? {
        val setup = setupDao.get() ?: PageSetup()
        val body = TemplateEngine.render(template.content, patient, values)
        val html = pdf.buildHtml(body, setup)
        val out = store.newGeneratedReportFile()
        val ok = pdf.generate(html, out)
        if (!ok || !out.exists() || out.length() == 0L) {
            out.delete()
            return null
        }
        val report = Report(
            patientId = patient.id,
            title = title,
            fileName = out.name,
            source = Report.SOURCE_GENERATED
        )
        return report.copy(id = reportDao.insert(report))
    }

    // ---------- templates ----------
    suspend fun getTemplate(id: Long) = templateDao.getById(id)

    suspend fun saveTemplate(id: Long, name: String, content: String) {
        val params = TemplateEngine.detectParams(content).joinToString(",")
        if (id == 0L) templateDao.insert(Template(name = name.trim(), content = content, parameters = params))
        else {
            val old = templateDao.getById(id) ?: return
            templateDao.update(old.copy(name = name.trim(), content = content, parameters = params))
        }
    }

    suspend fun deleteTemplate(t: Template) = templateDao.delete(t)

    suspend fun duplicateTemplate(t: Template) =
        templateDao.insert(t.copy(id = 0, name = t.name + " (copy)", createdAt = System.currentTimeMillis()))

    // ---------- page setup ----------
    suspend fun getPageSetup() = setupDao.get() ?: PageSetup()

    suspend fun savePageSetup(s: PageSetup) = setupDao.upsert(s)

    suspend fun importImage(uri: Uri): String? = store.importImage(uri)

    /** Renders a sample page with the given (possibly unsaved) setup so the admin can check the layout. */
    suspend fun generateSample(setup: PageSetup): File? {
        val body = "<h2 style=\"text-align:center;\">Sample Report</h2>" +
            "<p><strong>Patient Name:</strong> Sample Patient &nbsp;&nbsp; <strong>Age/Sex:</strong> 30/Male</p><hr>" +
            "<table><tr><th>Test</th><th>Result</th><th>Normal</th></tr>" +
            "<tr><td>Hemoglobin</td><td>13.5</td><td>12 - 16 g/dL</td></tr>" +
            "<tr><td>WBC</td><td>7,200</td><td>4,000 - 11,000</td></tr></table>" +
            "<p style=\"margin-top:16px;\">This is a preview of your header, footer and watermark.</p>"
        val out = File(context.cacheDir, "preview_${System.currentTimeMillis()}.pdf")
        val ok = pdf.generate(pdf.buildHtml(body, setup), out)
        return if (ok && out.exists() && out.length() > 0) out else null
    }

    // ---------- backup ----------
    suspend fun exportBackup(target: Uri) = backup.export(target)
    suspend fun restoreBackup(source: Uri) = backup.restore(source)
}
