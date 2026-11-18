package com.medireport.portal.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.text.HtmlCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer
import com.medireport.portal.data.PageSetup
import com.medireport.portal.data.Patient
import java.io.ByteArrayOutputStream

data class ReportData(
    val title: String,
    val specimen: String,
    val consultedBy: String,
    val received: String,
    val printDate: String,
    val items: List<ReportItem>,
    val values: Map<String, String>
)

/**
 * Draws a laboratory style report on A4 pages:
 * letterhead (header / footer / background / watermark pictures), barcode + title, patient box,
 * Test | Result | Reference table, free text fields and three signature blocks.
 * Everything is drawn with the phone's fonts so Bangla and English both render correctly.
 */
object ReportPdf {
    private const val MM = 72f / 25.4f
    private const val W = 595f
    private const val H = 842f

    class Assets(
        val header: Bitmap?, val footer: Bitmap?, val background: Bitmap?,
        val watermark: Bitmap?, val sigs: List<Bitmap?>
    )

    fun loadAssets(store: FileStore, s: PageSetup): Assets {
        fun load(name: String?): Bitmap? {
            if (name.isNullOrBlank()) return null
            val f = store.imageFile(name)
            return if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() else null
        }
        return Assets(
            load(s.headerImage), load(s.footerImage), load(s.backgroundImage), load(s.watermarkImage),
            listOf(load(s.sig1Image), load(s.sig2Image), load(s.sig3Image))
        )
    }

    private class Blk(val h: Float, val keepWithNext: Boolean, val draw: (Canvas, Float) -> Unit)

    fun build(patient: Patient, d: ReportData, s: PageSetup, a: Assets, art: Boolean): ByteArray {
        val mL = s.marginMm.coerceIn(5, 30) * MM
        val cw = W - 2 * mL
        val base = s.fontSize.coerceIn(7f, 14f)

        fun tp(size: Float, bold: Boolean = false, serif: Boolean = false, italic: Boolean = false, color: Int = Color.BLACK) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                val style = when {
                    bold && italic -> Typeface.BOLD_ITALIC
                    bold -> Typeface.BOLD
                    italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, style)
            }

        fun lay(text: CharSequence, p: TextPaint, w: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) =
            StaticLayout.Builder.obtain(text, 0, text.length, p, w.toInt().coerceAtLeast(10))
                .setAlignment(align).setIncludePad(false).build()

        fun html(t: String): Spanned =
            HtmlCompat.fromHtml(t.replace("\r", "").replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_COMPACT)

        fun drawLay(c: Canvas, l: StaticLayout, x: Float, y: Float) {
            c.save(); c.translate(x, y); l.draw(c); c.restore()
        }

        // ---------- zones ----------
        val headerBmp = if (art) a.header else null
        val footerBmp = if (art) a.footer else null
        val headerH = (headerBmp?.let { W * it.height / it.width } ?: (s.topMm * MM)).coerceAtMost(90f * MM)
        val footerH = (footerBmp?.let { W * it.height / it.width } ?: (s.bottomMm * MM)).coerceAtMost(70f * MM)
        val contentTop = headerH + 3f * MM

        val sigHtml = listOf(s.sig1Html, s.sig2Html, s.sig3Html)
        val hasSig = sigHtml.any { it.isNotBlank() } || a.sigs.any { it != null }
        val colW = cw / 3f
        val sigLayouts = sigHtml.map { if (it.isBlank()) null else lay(html(it), tp(base * 0.95f), colW - 8f) }
        val sigImgH = 13f * MM
        val sigH = if (hasSig) sigImgH + (sigLayouts.maxOfOrNull { it?.height ?: 0 } ?: 0) + 4f else 0f
        val sigTop = H - footerH - 4f * MM - sigH
        val bottomLimit = if (hasSig) sigTop - 3f * MM else H - footerH - 6f * MM

        // ---------- table geometry ----------
        val hasTests = d.items.any { it is ReportItem.Test }
        val wTest = cw * 0.36f
        val wRes = cw * 0.27f
        val wRef = cw - wTest - wRes
        val thinStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f; color = Color.rgb(90, 90, 90) }
        val boxStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.BLACK }

        val thH = base * 1.9f
        fun drawTableHeader(c: Canvas, y: Float): Float {
            c.drawRoundRect(RectF(mL, y, mL + cw, y + thH), 7f, 7f, boxStroke)
            c.drawLine(mL + wTest, y, mL + wTest, y + thH, thinStroke)
            c.drawLine(mL + wTest + wRes, y, mL + wTest + wRes, y + thH, thinStroke)
            val p = tp(base * 1.05f, bold = true, serif = true)
            val ty = y + (thH - (p.descent() - p.ascent())) / 2f - p.ascent()
            c.drawText("Test", mL + 6f, ty, p)
            c.drawText("Result", mL + wTest + 6f, ty, p)
            c.drawText("Reference Value", mL + wTest + wRes + 6f, ty, p)
            return y + thH + 2f
        }

        // ---------- table rows ----------
        val blocks = ArrayList<Blk>()
        var pendingSection: ReportItem.Section? = null
        for (item in d.items) {
            when (item) {
                is ReportItem.Section -> pendingSection = item
                is ReportItem.Test -> {
                    val result = d.values[item.key].orEmpty().trim()
                    if (result.isEmpty() || item.name.isBlank()) continue
                    pendingSection?.let { sec ->
                        val l = lay(sec.name, tp(base * 1.25f, bold = true), cw)
                        blocks.add(Blk(l.height + 9f, true) { c, y -> drawLay(c, l, mL, y + 6f) })
                        pendingSection = null
                    }
                    val flag = RangeCheck.flag(result, item.ref, patient.sex)
                    val resText = buildString {
                        append(result)
                        if (item.unit.isNotBlank()) append(' ').append(item.unit)
                        if (flag != null) append("  ").append(flag)
                    }
                    val nl = lay(item.name, tp(base), wTest - 14f)
                    val rl = lay(resText, tp(base, bold = true), wRes - 12f)
                    val fl = lay(item.ref, tp(base * 0.95f), wRef - 12f)
                    val h = maxOf(nl.height, rl.height, fl.height) + 7f
                    blocks.add(Blk(h, false) { c, y ->
                        c.drawRect(mL, y, mL + cw, y + h, thinStroke)
                        drawLay(c, nl, mL + 9f, y + 3.5f)
                        drawLay(c, rl, mL + wTest + 5f, y + 3.5f)
                        drawLay(c, fl, mL + wTest + wRes + 5f, y + 3.5f)
                    })
                }
                is ReportItem.Field -> {
                    val v = d.values[item.key].orEmpty().trim()
                    if (v.isEmpty()) continue
                    val ll = lay(item.label, tp(base * 1.1f, bold = true), cw)
                    val vl = lay(v, tp(base), cw)
                    blocks.add(Blk(ll.height + vl.height + 10f, false) { c, y ->
                        drawLay(c, ll, mL, y + 4f)
                        drawLay(c, vl, mL, y + 4f + ll.height + 2f)
                    })
                }
                is ReportItem.Note -> {
                    val l = lay(item.text, tp(base * 0.9f, italic = true, color = Color.DKGRAY), cw)
                    blocks.add(Blk(l.height + 8f, false) { c, y -> drawLay(c, l, mL, y + 4f) })
                }
            }
        }

        // ---------- first page head: barcode + title + patient box ----------
        val code = patient.patientCode
        val titleP = tp(base * 1.6f, bold = true, serif = true)
        val titleL = lay(d.title.uppercase().ifBlank { "REPORT" }, titleP, cw * 0.5f, Layout.Alignment.ALIGN_CENTER)
        val barH = 8f * MM
        val titleRowH = maxOf(titleL.height.toFloat(), if (s.showBarcode) barH else 0f)
        val lineH = base * 1.6f
        val boxH = lineH * 4 + 10f
        val headH = titleRowH + 4f + boxH + 4f + (if (hasTests) thH + 2f else 0f)

        fun drawHead(c: Canvas, y0: Float) {
            var y = y0
            if (s.showBarcode) {
                drawBarcode(c, code, mL, y + (titleRowH - barH) / 2f, 38f * MM, barH)
                drawBarcode(c, code, W - mL - 38f * MM, y + (titleRowH - barH) / 2f, 38f * MM, barH)
            }
            drawLay(c, titleL, mL + cw * 0.25f, y + (titleRowH - titleL.height) / 2f)
            y += titleRowH + 4f

            c.drawRoundRect(RectF(mL, y, mL + cw, y + boxH), 10f, 10f, boxStroke)
            val ix = mL + 9f
            val iw = cw - 18f
            var ly = y + 5f
            val normal = tp(base)
            val bold = tp(base * 1.1f, bold = true)
            fun seg(text: String, xf: Float, wf: Float, p: TextPaint) {
                val str = TextUtils.ellipsize(text, p, wf * iw - 4f, TextUtils.TruncateAt.END).toString()
                c.drawText(str, ix + xf * iw, ly - p.ascent(), p)
            }
            seg("Lab ID : $code", 0f, 0.28f, bold)
            seg("Patient ID : $code", 0.28f, 0.24f, normal)
            seg("Received : ${d.received}", 0.52f, 0.22f, normal)
            seg("Print Date : ${d.printDate}", 0.74f, 0.26f, normal)
            ly += lineH
            seg("Patient Name: ${patient.name}", 0f, 0.52f, normal)
            seg("Sex : ${patient.sex}", 0.52f, 0.22f, normal)
            seg("Age : ${patient.ageText}", 0.74f, 0.26f, normal)
            ly += lineH
            seg("Consulted By : ${d.consultedBy}", 0f, 1f, normal)
            ly += lineH
            seg("Specimen : ${d.specimen}", 0f, 1f, normal)
            y += boxH + 4f
            if (hasTests) drawTableHeader(c, y)
        }

        // ---------- pagination ----------
        val firstY = contentTop + headH
        val nextY = contentTop + (if (hasTests) thH + 2f else 0f)
        val pages = ArrayList<MutableList<Pair<Blk, Float>>>()
        var cur = ArrayList<Pair<Blk, Float>>()
        var y = firstY
        for ((i, b) in blocks.withIndex()) {
            val need = b.h + (if (b.keepWithNext && i + 1 < blocks.size) blocks[i + 1].h else 0f)
            if (y + need > bottomLimit && cur.isNotEmpty()) {
                pages.add(cur); cur = ArrayList(); y = nextY
            }
            cur.add(b to y)
            y += b.h
        }
        pages.add(cur)

        // ---------- draw ----------
        val doc = PdfDocument()
        val artPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        for ((pi, rows) in pages.withIndex()) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pi + 1).create())
            val c = page.canvas

            if (art) {
                a.background?.let {
                    artPaint.alpha = (s.backgroundOpacity.coerceIn(0f, 1f) * 255).toInt()
                    c.drawBitmap(it, null, RectF(0f, 0f, W, H), artPaint)
                }
                a.watermark?.let {
                    val ww = s.watermarkSizeMm.coerceIn(20, 200) * MM
                    val wh = ww * it.height / it.width
                    artPaint.alpha = (s.watermarkOpacity.coerceIn(0f, 1f) * 255).toInt()
                    c.drawBitmap(it, null, RectF((W - ww) / 2f, (H - wh) / 2f, (W + ww) / 2f, (H + wh) / 2f), artPaint)
                }
                artPaint.alpha = 255
                if (headerBmp != null) {
                    c.drawBitmap(headerBmp, null, RectF(0f, 0f, W, headerH), artPaint)
                } else if (s.headerHtml.isNotBlank()) {
                    drawLay(c, lay(html(s.headerHtml), tp(base * 1.1f), cw, Layout.Alignment.ALIGN_CENTER), mL, 6f * MM)
                }
                if (footerBmp != null) {
                    c.drawBitmap(footerBmp, null, RectF(0f, H - footerH, W, H), artPaint)
                } else if (s.footerHtml.isNotBlank()) {
                    drawLay(c, lay(html(s.footerHtml), tp(base * 0.9f), cw, Layout.Alignment.ALIGN_CENTER), mL, H - footerH + 3f * MM)
                }
            }

            if (pi == 0) drawHead(c, contentTop) else if (hasTests) drawTableHeader(c, contentTop)
            rows.forEach { (b, yy) -> b.draw(c, yy) }

            if (hasSig) {
                for (i in 0 until 3) {
                    val x = mL + colW * i
                    a.sigs[i]?.let { bm ->
                        val maxW = colW * 0.7f
                        var ih = sigImgH
                        var iw = ih * bm.width / bm.height
                        if (iw > maxW) { iw = maxW; ih = iw * bm.height / bm.width }
                        c.drawBitmap(bm, null, RectF(x, sigTop + sigImgH - ih, x + iw, sigTop + sigImgH), artPaint)
                    }
                    sigLayouts[i]?.let { drawLay(c, it, x, sigTop + sigImgH + 2f) }
                }
            }
            if (pages.size > 1) {
                val p = tp(base * 0.8f, color = Color.DKGRAY)
                val t = "Page ${pi + 1} / ${pages.size}"
                c.drawText(t, W - mL - p.measureText(t), H - footerH - 1.5f * MM, p)
            }
            doc.finishPage(page)
        }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun drawBarcode(c: Canvas, text: String, x: Float, y: Float, w: Float, h: Float) {
        val m = try {
            Code128Writer().encode(text, BarcodeFormat.CODE_128, 0, 0)
        } catch (e: Exception) {
            return
        }
        val cell = w / m.width
        val p = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        for (i in 0 until m.width) {
            if (m.get(i, 0)) c.drawRect(x + i * cell, y, x + (i + 1) * cell, y + h, p)
        }
    }

    /** Demonstration data used by the page-setup preview. */
    fun sample(): Pair<Patient, ReportData> {
        val patient = Patient(
            patientCode = "26112085", name = "Nitu", sex = "Female", age = 33, phone = "01700000000", refBy = "Asst.Prof.Dr. Sample Doctor"
        )
        val items = ReportDsl.parse(
            "# Red Blood Cells\nHaemoglobin | gm/dl | M: 13 - 18 gm/dl, F: 11.5 - 16.5 gm/dl\nESR (Westergren) | mm /hr | M: 0 - 10, F: 0 - 20 mm/hr.\n" +
                "# White Blood Cells\nTotal WBC Count | /Cumm | Adult: 4,000-11,000/cumm\n# RBC Panel\nMCV | fL | 76-94 fL\nMCH | pg | 27-32 pg"
        )
        val tests = items.filterIsInstance<ReportItem.Test>()
        val vals = HashMap<String, String>()
        listOf("10.8", "45", "12,100", "83.0", "27.0").forEachIndexed { i, v -> tests.getOrNull(i)?.let { vals[it.key] = v } }
        val today = Formatters.today()
        return patient to ReportData("HAEMATOLOGY REPORT", "BLOOD", patient.refBy, today, today, items, vals)
    }
}
