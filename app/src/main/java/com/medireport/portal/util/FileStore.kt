package com.medireport.portal.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Private, app-internal storage for reports and page-setup images (never world readable). */
class FileStore(private val context: Context) {
    val reportsDir: File = File(context.filesDir, "reports").apply { mkdirs() }
    val imagesDir: File = File(context.filesDir, "images").apply { mkdirs() }

    fun reportFile(name: String) = File(reportsDir, name)
    fun imageFile(name: String) = File(imagesDir, name)

    fun newGeneratedReportFile(): File = File(reportsDir, "gen_${UUID.randomUUID()}.pdf")

    fun deleteReport(name: String) {
        runCatching { reportFile(name).delete() }
    }

    fun deleteImage(name: String?) {
        if (name.isNullOrBlank()) return
        runCatching { imageFile(name).delete() }
    }

    fun clearAll() {
        reportsDir.listFiles()?.forEach { it.delete() }
        imagesDir.listFiles()?.forEach { it.delete() }
    }

    fun displayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    /** Copies a picked document (PDF or image) into private storage. Returns (storedName, mimeType). */
    suspend fun importDocument(uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val mime = context.contentResolver.getType(uri) ?: "application/pdf"
            val ext = when {
                mime.contains("pdf") -> "pdf"
                mime.contains("png") -> "png"
                mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                else -> "bin"
            }
            val name = "up_${UUID.randomUUID()}.$ext"
            context.contentResolver.openInputStream(uri)?.use { input ->
                reportFile(name).outputStream().use { out -> input.copyTo(out) }
            } ?: return@runCatching null
            name to mime
        }.getOrNull()
    }

    /** Decodes, down-scales (max side [maxDim]) and stores an image. Returns stored file name. */
    suspend fun importImage(uri: Uri, maxDim: Int = 1600): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching null
            val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            val finalBmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            } else bmp
            val name = "img_${UUID.randomUUID()}.png"
            imageFile(name).outputStream().use { finalBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            name
        }.getOrNull()
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

object Intents {
    fun openFile(context: Context, file: File, mime: String, store: FileStore) {
        if (!file.exists()) {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(store.uriFor(file), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, null))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, file: File, mime: String, title: String, store: FileStore) {
        if (!file.exists()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, store.uriFor(file))
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, title)) }
    }

    fun dial(context: Context, phone: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
        }
    }

    fun sms(context: Context, phone: String, body: String = "") {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone))).putExtra("sms_body", body)
            )
        }
    }
}
