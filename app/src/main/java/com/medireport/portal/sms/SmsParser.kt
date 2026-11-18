package com.medireport.portal.sms

/** What a patient asked for by SMS. */
data class SmsQuery(
    val code: String,
    /** set when the patient texts from a different number and gives the registered number as the first word */
    val claimedPhone: String?,
    /** report name to look for; empty = list everything that is published */
    val keyword: String
)

object SmsParser {
    private val LIST_WORDS = setOf("all", "list", "reports", "report")

    /**
     *   "P-001 CBC"                  -> sent from the registered number
     *   "01712345678 P-001 CBC"      -> sent from any other number, registered number as proof
     */
    fun parse(body: String): SmsQuery? {
        val tokens = body.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val first = tokens[0]
        val phoneLike = digits(first).length >= 10 && first.all { it.isDigit() || it == '+' || it == '-' }
        val query = if (phoneLike && tokens.size >= 2) {
            SmsQuery(tokens[1], first, keyword(tokens.drop(2)))
        } else {
            SmsQuery(first, null, keyword(tokens.drop(1)))
        }
        return if (query.code.length > 40) null else query
    }

    private fun keyword(words: List<String>): String {
        val k = words.joinToString(" ").trim()
        return if (k.lowercase() in LIST_WORDS) "" else k
    }

    fun digits(s: String) = s.filter { it.isDigit() }

    /** +8801712345678, 8801712345678 and 01712345678 are the same number. */
    fun sameNumber(a: String, b: String): Boolean {
        val x = digits(a).takeLast(10)
        val y = digits(b).takeLast(10)
        return x.length >= 7 && x == y
    }

    fun looksLikeNumber(sender: String) = digits(sender).length >= 7

    /** every word of the keyword must appear in the title (case-insensitive) */
    fun matches(title: String, keyword: String): Boolean {
        if (keyword.isBlank()) return true
        val t = title.lowercase()
        return keyword.lowercase().split(Regex("\\s+")).all { it in t }
    }
}
