package com.medireport.portal.data

import com.medireport.portal.util.TemplateEngine

object DefaultTemplates {
    private const val HEAD = """<p><strong>Patient Name:</strong> {{patient_name}}</p>
<p><strong>Patient ID:</strong> {{patient_id}} &nbsp;&nbsp; <strong>Age/Sex:</strong> {{age}}/{{sex}}</p>
<p><strong>Ref. By:</strong> {{ref_by}} &nbsp;&nbsp; <strong>Date:</strong> {{date}}</p>
<hr>"""

    private fun t(name: String, body: String): Template {
        val content = body.trimIndent()
        return Template(
            name = name,
            content = content,
            parameters = TemplateEngine.detectParams(content).joinToString(",")
        )
    }

    fun all(): List<Template> = listOf(
        t(
            "Blood Test Report (CBC)",
            """
            <h2 style="text-align:center;">Complete Blood Count (CBC)</h2>
            $HEAD
            <table>
              <tr><th>Test Name</th><th>Result</th><th>Normal Range</th></tr>
              <tr><td>Hemoglobin</td><td>{{hemoglobin}}</td><td>12 - 16 g/dL</td></tr>
              <tr><td>WBC Count</td><td>{{wbc}}</td><td>4,000 - 11,000 /µL</td></tr>
              <tr><td>Platelet Count</td><td>{{platelet}}</td><td>150,000 - 450,000 /µL</td></tr>
              <tr><td>ESR</td><td>{{esr}}</td><td>0 - 20 mm/1st hr</td></tr>
            </table>
            <p style="margin-top:16px;"><strong>Remarks:</strong> {{remarks}}</p>
            """
        ),
        t(
            "X-Ray Report",
            """
            <h2 style="text-align:center;">X-Ray Report</h2>
            $HEAD
            <p><strong>Examination:</strong> {{examination}}</p>
            <p><strong>Findings:</strong></p>
            <p>{{findings}}</p>
            <p><strong>Impression:</strong></p>
            <p>{{impression}}</p>
            """
        ),
        t(
            "Urine R/E",
            """
            <h2 style="text-align:center;">Urine Routine Examination</h2>
            $HEAD
            <table>
              <tr><th>Test</th><th>Result</th></tr>
              <tr><td>Colour</td><td>{{colour}}</td></tr>
              <tr><td>Albumin</td><td>{{albumin}}</td></tr>
              <tr><td>Sugar</td><td>{{sugar}}</td></tr>
              <tr><td>Pus Cells</td><td>{{pus_cells}}</td></tr>
              <tr><td>RBC</td><td>{{rbc}}</td></tr>
            </table>
            <p style="margin-top:16px;"><strong>Remarks:</strong> {{remarks}}</p>
            """
        ),
        t(
            "Lipid Profile",
            """
            <h2 style="text-align:center;">Lipid Profile</h2>
            $HEAD
            <table>
              <tr><th>Test</th><th>Result</th><th>Desirable</th></tr>
              <tr><td>Total Cholesterol</td><td>{{cholesterol}}</td><td>&lt; 200 mg/dL</td></tr>
              <tr><td>Triglycerides</td><td>{{triglycerides}}</td><td>&lt; 150 mg/dL</td></tr>
              <tr><td>HDL</td><td>{{hdl}}</td><td>&gt; 40 mg/dL</td></tr>
              <tr><td>LDL</td><td>{{ldl}}</td><td>&lt; 100 mg/dL</td></tr>
            </table>
            <p style="margin-top:16px;"><strong>Remarks:</strong> {{remarks}}</p>
            """
        ),
        t(
            "General Report",
            """
            <h2 style="text-align:center;">Medical Report</h2>
            $HEAD
            <h3>{{report_title}}</h3>
            <p>{{report_content}}</p>
            """
        )
    )
}
