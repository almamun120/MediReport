package com.medireport.portal.util

import android.content.Context
import android.print.PdfPrinter
import android.print.PrintAttributes
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import com.medireport.portal.data.PageSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Renders a template (HTML) into an A4 PDF with the clinic header, footer and watermark that are
 * configured in "Page Setup". Header/footer repeat on every page.
 */
class PdfGenerator(private val context: Context, private val store: FileStore) {

    fun buildHtml(body: String, setup: PageSetup): String {
        val headerImg = dataUri(setup.headerImage)
        val footerImg = dataUri(setup.footerImage)
        val bgImg = dataUri(setup.backgroundImage)
        val hh = setup.headerHeightMm.coerceIn(0, 80)
        val fh = setup.footerHeightMm.coerceIn(0, 60)
        val opacity = setup.backgroundOpacity.coerceIn(0f, 1f)

        val bgDiv = if (bgImg != null)
            "<div class=\"bg\" style=\"background-image:url('$bgImg');opacity:$opacity\"></div>" else ""
        val headerDiv = "<div class=\"hdr\"><div class=\"inner\">" +
            (headerImg?.let { "<img src=\"$it\" class=\"pic\">" } ?: "") +
            setup.headerHtml + "</div></div>"
        val footerDiv = "<div class=\"ftr\"><div class=\"inner\">" +
            (footerImg?.let { "<img src=\"$it\" class=\"pic\">" } ?: "") +
            setup.footerHtml + "</div></div>"

        return """
<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@page { margin: 0; }
html, body { margin: 0; padding: 0; }
body { font-family: sans-serif; font-size: 13px; color: #111; line-height: 1.45; }
.bg { position: fixed; top: 0; left: 0; width: 100%; height: 100%; z-index: -1;
      background-position: center center; background-repeat: no-repeat; background-size: contain; }
.hdr { position: fixed; top: 0; left: 0; right: 0; height: ${hh}mm; overflow: hidden; text-align: center; }
.ftr { position: fixed; bottom: 0; left: 0; right: 0; height: ${fh}mm; overflow: hidden; text-align: center; }
.inner { padding: 4mm 12mm; }
.pic { max-width: 100%; max-height: ${maxOf(hh, fh)}mm; }
.hs { height: ${hh}mm; } .fs { height: ${fh}mm; }
.layout { width: 100%; border-collapse: collapse; }
.layout > thead > tr > td, .layout > tfoot > tr > td, .layout > tbody > tr > td { padding: 0; }
.content { padding: 4mm 14mm; }
h2 { margin: 6px 0 10px; } h3 { margin: 10px 0 6px; }
table { border-collapse: collapse; }
.content table[border], .content table { width: 100%; }
.content th, .content td { border: 1px solid #888; padding: 5px 7px; text-align: left; }
hr { border: 0; border-top: 1px solid #999; margin: 10px 0; }
</style></head>
<body>
$bgDiv
$headerDiv
$footerDiv
<table class="layout">
<thead><tr><td><div class="hs"></div></td></tr></thead>
<tfoot><tr><td><div class="fs"></div></td></tr></tfoot>
<tbody><tr><td><div class="content">$body</div></td></tr></tbody>
</table>
</body></html>
""".trimIndent()
    }

    private fun dataUri(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val f = store.imageFile(name)
        if (!f.exists()) return null
        val mime = if (name.endsWith(".png")) "image/png" else "image/jpeg"
        return "data:$mime;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }

    /** Must be called from any coroutine; WebView work is hopped to the main thread. */
    suspend fun generate(html: String, out: File): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val webView = WebView(context.applicationContext)
            webView.settings.javaScriptEnabled = false
            webView.settings.allowFileAccess = false
            var started = false
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (started) return
                    started = true
                    // give the layout engine a moment to settle before printing
                    view.postDelayed({
                        val attrs = PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build()
                        val adapter = view.createPrintDocumentAdapter("report")
                        PdfPrinter(attrs).print(adapter, out) { ok ->
                            runCatching { view.destroy() }
                            if (cont.isActive) cont.resume(ok)
                        }
                    }, 600)
                }
            }
            cont.invokeOnCancellation { runCatching { webView.destroy() } }
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    }
}
