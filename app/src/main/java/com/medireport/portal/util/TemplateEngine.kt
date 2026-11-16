package com.medireport.portal.util

import android.text.TextUtils
import com.medireport.portal.data.Patient

object TemplateEngine {
    /** Placeholders filled automatically from the selected patient. */
    val BUILT_IN = setOf("patient_name", "patient_id", "age", "sex", "phone", "ref_by")

    private val rx = Regex("\\{\\{\\s*(\\w+)\\s*\\}\\}")

    /** Custom placeholders (those the user must fill in) in order of appearance. */
    fun detectParams(content: String): List<String> =
        rx.findAll(content).map { it.groupValues[1] }.filter { it !in BUILT_IN }.distinct().toList()

    fun render(content: String, patient: Patient, values: Map<String, String>): String =
        rx.replace(content) { m ->
            when (val key = m.groupValues[1]) {
                "patient_name" -> esc(patient.name)
                "patient_id" -> esc(patient.patientCode)
                "age" -> patient.age.toString()
                "sex" -> esc(patient.sex)
                "phone" -> esc(patient.phone)
                "ref_by" -> esc(patient.refBy.ifBlank { "-" })
                else -> esc(values[key].orEmpty())
            }
        }

    fun isMultiline(name: String): Boolean {
        val n = name.lowercase()
        return listOf("content", "finding", "impression", "remark", "note", "description", "history", "comment", "advice")
            .any { n.contains(it) }
    }

    fun humanize(name: String): String =
        name.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

    private fun esc(s: String): String = TextUtils.htmlEncode(s).replace("\n", "<br>")
}
