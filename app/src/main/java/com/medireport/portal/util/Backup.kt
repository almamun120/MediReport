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
            root.put("version", 1)
            root.put("patients", JSONArray().apply {
                db.patientDao().getAll().forEach {
                    put(JSONObject().put("id", it.id).put("code", it.patientCode).put("name", it.name)
                        .put("sex", it.sex).put("age", it.age).put("phone", it.phone)
                        .put("refBy", it.refBy).put("createdAt", it.createdAt))
                }
            })
            root.put("reports", JSONArray().apply {
                db.reportDao().getAll().forEach {
                    put(JSONObject().put("id", it.id).put("patientId", it.patientId).put("title", it.title)
                        .put("fileName", it.fileName).put("mime", it.mimeType).put("source", it.source)
                        .put("createdAt", it.createdAt))
                }
            })
            root.put("templates", JSONArray().apply {
                db.templateDao().getAll().forEach {
                    put(JSONObject().put("id", it.id).put("name", it.name).put("content", it.content)
                        .put("parameters", it.parameters).put("createdAt", it.createdAt))
                }
            })
            db.pageSetupDao().get()?.let {
                root.put("pageSetup", JSONObject().put("headerHtml", it.headerHtml).put("footerHtml", it.footerHtml)
                    .put("headerImage", it.headerImage ?: JSONObject.NULL)
                    .put("footerImage", it.footerImage ?: JSONObject.NULL)
                    .put("backgroundImage", it.backgroundImage ?: JSONObject.NULL)
                    .put("opacity", it.backgroundOpacity.toDouble())
                    .put("hh", it.headerHeightMm).put("fh", it.footerHeightMm))
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

            val patients = root.getJSONArray("patients").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    Patient(it.getLong("id"), it.getString("code"), it.getString("name"), it.getString("sex"),
                        it.getInt("age"), it.getString("phone"), it.optString("refBy", ""), it.getLong("createdAt"))
                }
            }
            val reports = root.getJSONArray("reports").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    Report(it.getLong("id"), it.getLong("patientId"), it.getString("title"), it.getString("fileName"),
                        it.optString("mime", "application/pdf"), it.optString("source", Report.SOURCE_UPLOAD),
                        it.getLong("createdAt"))
                }
            }
            val templates = root.getJSONArray("templates").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    Template(it.getLong("id"), it.getString("name"), it.getString("content"),
                        it.optString("parameters", ""), it.getLong("createdAt"))
                }
            }
            val setup = root.optJSONObject("pageSetup")?.let {
                PageSetup(
                    1, it.optString("headerHtml", ""), it.optString("footerHtml", ""),
                    it.optString("headerImage").takeIf { s -> s.isNotEmpty() && s != "null" },
                    it.optString("footerImage").takeIf { s -> s.isNotEmpty() && s != "null" },
                    it.optString("backgroundImage").takeIf { s -> s.isNotEmpty() && s != "null" },
                    it.optDouble("opacity", 0.1).toFloat(), it.optInt("hh", 30), it.optInt("fh", 20)
                )
            }

            db.withTransaction {
                db.reportDao().deleteAll()
                db.patientDao().deleteAll()
                db.templateDao().deleteAll()
                db.patientDao().insertAll(patients)
                db.reportDao().insertAll(reports)
                db.templateDao().insertAll(templates)
                if (setup != null) db.pageSetupDao().upsert(setup)
            }

            store.clearAll()
            tmpReports.listFiles()?.forEach { it.copyTo(store.reportFile(it.name), overwrite = true) }
            tmpImages.listFiles()?.forEach { it.copyTo(store.imageFile(it.name), overwrite = true) }
            tmpReports.deleteRecursively(); tmpImages.deleteRecursively()
            true
        }.getOrDefault(false)
    }
}
