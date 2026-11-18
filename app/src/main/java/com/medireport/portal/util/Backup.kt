package com.medireport.portal.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.medireport.portal.data.AppDatabase
import com.medireport.portal.data.PageSetup
import com.medireport.portal.data.Patient
import com.medireport.portal.data.Report
import com.medireport.portal.data.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Full backup / restore (database as JSON + all stored files) in a single .zip. */
class Backup(
    private val context: Context,
    private val db: AppDatabase,
    private val store: FileStore
) {
    suspend fun export(target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject()
            root.put("version", 2)
            root.put("patients", JSONArray().apply {
                db.patientDao().getAll().forEach {
                    put(JSONObject().put("id", it.id).put("code", it.patientCode).put("name", it.name)
                        .put("sex", it.sex).put("age", it.age).put("phone", it.phone)
                        .put("refBy", it.refBy).put("createdAt", it.createdAt)
                        .put("ageMonths", it.ageMonths).put("ageDays", it.ageDays).put("email", it.email))
                }
            })
            root.put("reports", JSONArray().apply {
                db.reportDao().getAll().forEach {
                    put(JSONObject().put("id", it.id).put("patientId", it.patientId).put("title", it.title)
                        .put("fileName", it.fileName).put("mime", it.mimeType).put("source", it.source)
                        .put("createdAt", it.createdAt).put("published", it.published))
                }
            })
            root.put("templates", JSONArray().apply {
                db.templateDao().getAll().forEach {
                    put(JSONObject().put("id", it.id).put("name", it.name).put("title", it.title)
                        .put("specimen", it.specimen).put("content", it.content).put("createdAt", it.createdAt))
                }
            })
            db.pageSetupDao().get()?.let {
                root.put("pageSetup", JSONObject()
                    .put("clinicName", it.clinicName).put("headerHtml", it.headerHtml).put("footerHtml", it.footerHtml)
                    .put("headerImage", it.headerImage ?: JSONObject.NULL)
                    .put("footerImage", it.footerImage ?: JSONObject.NULL)
                    .put("backgroundImage", it.backgroundImage ?: JSONObject.NULL)
                    .put("backgroundOpacity", it.backgroundOpacity.toDouble())
                    .put("watermarkImage", it.watermarkImage ?: JSONObject.NULL)
                    .put("watermarkOpacity", it.watermarkOpacity.toDouble())
                    .put("watermarkSizeMm", it.watermarkSizeMm).put("topMm", it.topMm).put("bottomMm", it.bottomMm)
                    .put("marginMm", it.marginMm).put("fontSize", it.fontSize.toDouble())
                    .put("letterhead", it.letterhead).put("showBarcode", it.showBarcode)
                    .put("sig1Html", it.sig1Html).put("sig2Html", it.sig2Html).put("sig3Html", it.sig3Html)
                    .put("sig1Image", it.sig1Image ?: JSONObject.NULL)
                    .put("sig2Image", it.sig2Image ?: JSONObject.NULL)
                    .put("sig3Image", it.sig3Image ?: JSONObject.NULL))
            }

            context.contentResolver.openOutputStream(target)?.use { os ->
                ZipOutputStream(os.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry("data.json"))
                    zip.write(root.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    store.reportsDir.listFiles()?.forEach { f -> addFile(zip, "reports/${f.name}", f) }
                    store.imagesDir.listFiles()?.forEach { f -> addFile(zip, "images/${f.name}", f) }
                }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    private fun addFile(zip: ZipOutputStream, path: String, f: File) {
        zip.putNextEntry(ZipEntry(path))
        f.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    suspend fun restore(source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            var json: String? = null
            val tmpReports = File(context.cacheDir, "restore_reports").apply { deleteRecursively(); mkdirs() }
            val tmpImages = File(context.cacheDir, "restore_images").apply { deleteRecursively(); mkdirs() }

            context.contentResolver.openInputStream(source)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var e = zip.nextEntry
                    while (e != null) {
                        if (!e.isDirectory) {
                            val leaf = File(e.name).name // strips any path => no zip-slip
                            when {
                                e.name == "data.json" -> json = zip.readBytes().toString(Charsets.UTF_8)
                                e.name.startsWith("reports/") -> File(tmpReports, leaf).outputStream().use { zip.copyTo(it) }
                                e.name.startsWith("images/") -> File(tmpImages, leaf).outputStream().use { zip.copyTo(it) }
                            }
                        }
                        zip.closeEntry()
                        e = zip.nextEntry
                    }
                }
            } ?: return@runCatching false

            val root = JSONObject(json ?: return@runCatching false)

            val v2 = root.optInt("version", 1) >= 2
            fun optStr(o: JSONObject, k: String): String? =
                if (o.isNull(k)) null else o.optString(k).takeIf { it.isNotEmpty() }

            val patients = root.getJSONArray("patients").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    Patient(
                        id = it.getLong("id"), patientCode = it.getString("code"), name = it.getString("name"),
                        sex = it.getString("sex"), age = it.getInt("age"), phone = it.getString("phone"),
                        refBy = it.optString("refBy", ""), createdAt = it.getLong("createdAt"),
                        ageMonths = it.optInt("ageMonths", 0), ageDays = it.optInt("ageDays", 0),
                        email = it.optString("email", "")
                    )
                }
            }
            val reports = root.getJSONArray("reports").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    Report(
                        id = it.getLong("id"), patientId = it.getLong("patientId"), title = it.getString("title"),
                        fileName = it.getString("fileName"), mimeType = it.optString("mime", "application/pdf"),
                        source = it.optString("source", Report.SOURCE_UPLOAD), createdAt = it.getLong("createdAt"),
                        published = it.optBoolean("published", false)
                    )
                }
            }
            val templates = if (!v2) emptyList() else root.getJSONArray("templates").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    Template(
                        id = it.getLong("id"), name = it.getString("name"), title = it.optString("title", ""),
                        specimen = it.optString("specimen", ""), content = it.getString("content"),
                        createdAt = it.getLong("createdAt")
                    )
                }
            }
            val setup = if (!v2) null else root.optJSONObject("pageSetup")?.let {
                PageSetup(
                    id = 1, clinicName = it.optString("clinicName", ""), headerHtml = it.optString("headerHtml", ""),
                    footerHtml = it.optString("footerHtml", ""),
                    headerImage = optStr(it, "headerImage"), footerImage = optStr(it, "footerImage"),
                    backgroundImage = optStr(it, "backgroundImage"),
                    backgroundOpacity = it.optDouble("backgroundOpacity", 0.12).toFloat(),
                    watermarkImage = optStr(it, "watermarkImage"),
                    watermarkOpacity = it.optDouble("watermarkOpacity", 0.10).toFloat(),
                    watermarkSizeMm = it.optInt("watermarkSizeMm", 110), topMm = it.optInt("topMm", 45),
                    bottomMm = it.optInt("bottomMm", 28), marginMm = it.optInt("marginMm", 12),
                    fontSize = it.optDouble("fontSize", 9.5).toFloat(),
                    letterhead = it.optBoolean("letterhead", true), showBarcode = it.optBoolean("showBarcode", true),
                    sig1Html = it.optString("sig1Html", ""), sig2Html = it.optString("sig2Html", ""),
                    sig3Html = it.optString("sig3Html", ""),
                    sig1Image = optStr(it, "sig1Image"), sig2Image = optStr(it, "sig2Image"),
                    sig3Image = optStr(it, "sig3Image")
                )
            }

            db.withTransaction {
                db.reportDao().deleteAll()
                db.patientDao().deleteAll()
                db.patientDao().insertAll(patients)
                db.reportDao().insertAll(reports)
                if (v2) {
                    db.templateDao().deleteAll()
                    db.templateDao().insertAll(templates)
                }
                if (setup != null) db.pageSetupDao().upsert(setup)
            }

            if (v2) store.clearAll() else store.reportsDir.listFiles()?.forEach { it.delete() }
            tmpReports.listFiles()?.forEach { it.copyTo(store.reportFile(it.name), overwrite = true) }
            tmpImages.listFiles()?.forEach { it.copyTo(store.imageFile(it.name), overwrite = true) }
            tmpReports.deleteRecursively(); tmpImages.deleteRecursively()
            true
        }.getOrDefault(false)
    }
}
