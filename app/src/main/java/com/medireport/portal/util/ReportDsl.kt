package com.medireport.portal.util

/**
 * Report templates are written in a very small language, one item per line:
 *
 *   # Section heading
 *   Test name | unit | reference range | default result
 *   @ Free text field label          (findings, impression ... multi-line)
 *   > A fixed note printed as it is
 *
 * Use \n inside a reference range for a line break. Empty lines are ignored.
 */
sealed interface ReportItem {
    data class Section(val name: String) : ReportItem
    data class Test(val key: String, val name: String, val unit: String, val ref: String, val default: String) : ReportItem
    data class Field(val key: String, val label: String) : ReportItem
    data class Note(val text: String) : ReportItem
}

object ReportDsl {
    fun parse(src: String): List<ReportItem> {
        val out = ArrayList<ReportItem>()
        var n = 0
        for (raw in src.replace("\r", "").split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#") -> out.add(ReportItem.Section(line.removePrefix("#").trim()))
                line.startsWith("@") -> out.add(ReportItem.Field("f${n++}", line.removePrefix("@").trim()))
                line.startsWith(">") -> out.add(ReportItem.Note(line.removePrefix(">").trim()))
                else -> {
                    val p = line.split("|").map { it.trim() }
                    out.add(
                        ReportItem.Test(
                            key = "t${n++}",
                            name = p.getOrElse(0) { "" },
                            unit = p.getOrElse(1) { "" },
                            ref = p.getOrElse(2) { "" }.replace("\\n", "\n"),
                            default = p.getOrElse(3) { "" }
                        )
                    )
                }
            }
        }
        return out
    }

    /** Number of entry fields (tests + free text) – shown in the template list. */
    fun fieldCount(src: String): Int = parse(src).count { it is ReportItem.Test || it is ReportItem.Field }
}

/** Marks a numeric result as high (H) or low (L) when the reference range can be understood. Only an aid. */
object RangeCheck {
    private val NUM = "\\d[\\d,]*(?:\\.\\d+)?"
    private val RESULT = Regex("^\\s*($NUM)\\s*%?\\s*$")
    private val RANGE = Regex("($NUM)\\s*(?:-|–|—|to)\\s*($NUM)")
    private val UPPER = Regex("[<≤]\\s*($NUM)")
    private val LOWER = Regex("[>≥]\\s*($NUM)")

    private fun num(s: String) = s.replace(",", "").toDoubleOrNull()

    private fun segment(ref: String, sex: String): String? {
        val lower = ref.lowercase()
        val sexRx = when {
            sex.equals("Male", true) -> Regex("(?i)\\b(?:male|m)\\b\\s*[:\\-]\\s*([^A-Za-z]*)")
            sex.equals("Female", true) -> Regex("(?i)\\b(?:female|f)\\b\\s*[:\\-]\\s*([^A-Za-z]*)")
            else -> null
        }
        val hasSexParts = Regex("(?i)\\b(?:male|m)\\b\\s*[:\\-]").containsMatchIn(ref) &&
            Regex("(?i)\\b(?:female|f)\\b\\s*[:\\-]").containsMatchIn(ref)
        if (hasSexParts) return sexRx?.find(ref)?.groupValues?.get(1)
        if (lower.contains("adult")) return Regex("(?i)adult\\s*:?\\s*([^A-Za-z]*)").find(ref)?.groupValues?.get(1)
        if (lower.contains("child") || lower.contains("infant") || lower.contains("newborn")) return null
        return ref
    }

    /** "H", "L" or null. */
    fun flag(result: String, ref: String, sex: String): String? {
        val v = RESULT.find(result)?.groupValues?.get(1)?.let(::num) ?: return null
        val seg = segment(ref, sex) ?: return null
        RANGE.find(seg)?.let { m ->
            val lo = num(m.groupValues[1]) ?: return null
            val hi = num(m.groupValues[2]) ?: return null
            return when {
                v > hi -> "H"
                v < lo -> "L"
                else -> null
            }
        }
        UPPER.find(seg)?.let { m -> val hi = num(m.groupValues[1]) ?: return null; return if (v > hi) "H" else null }
        LOWER.find(seg)?.let { m -> val lo = num(m.groupValues[1]) ?: return null; return if (v < lo) "L" else null }
        return null
    }
}
