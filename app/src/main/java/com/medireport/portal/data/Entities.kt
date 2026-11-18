package com.medireport.portal.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "patients", indices = [Index(value = ["patientCode"], unique = true)])
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientCode: String,
    val name: String,
    val sex: String,
    val age: Int,
    val phone: String,
    val refBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val ageMonths: Int = 0,
    val ageDays: Int = 0,
    val email: String = ""
) {
    /** "33 Y0 M0 D" – the format used on the printed report. */
    val ageText: String get() = "$age Y$ageMonths M$ageDays D"
}

@Entity(
    tableName = "reports",
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId")]
)
data class Report(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val title: String,
    val fileName: String,
    val mimeType: String = "application/pdf",
    /** "UPLOAD" or "GENERATED" */
    val source: String = SOURCE_UPLOAD,
    val createdAt: Long = System.currentTimeMillis(),
    /** visible to the patient on the web portal */
    val published: Boolean = false
) {
    companion object {
        const val SOURCE_UPLOAD = "UPLOAD"
        const val SOURCE_GENERATED = "GENERATED"
    }
}

data class ReportWithPatient(
    @Embedded val report: Report,
    @ColumnInfo(name = "patientName") val patientName: String,
    @ColumnInfo(name = "patientCode") val patientCode: String
)

@Entity(tableName = "templates")
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** heading printed on the report, e.g. "HAEMATOLOGY REPORT" */
    val title: String = "",
    /** default specimen, e.g. "BLOOD" */
    val specimen: String = "",
    /** body written in the simple template language, see ReportDsl */
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "page_setup")
data class PageSetup(
    @PrimaryKey val id: Int = 1,
    val clinicName: String = "",
    val headerHtml: String = "",
    val footerHtml: String = "",
    val headerImage: String? = null,
    val footerImage: String? = null,
    val backgroundImage: String? = null,
    val backgroundOpacity: Float = 0.12f,
    val watermarkImage: String? = null,
    val watermarkOpacity: Float = 0.10f,
    val watermarkSizeMm: Int = 110,
    /** space kept free at the top / bottom when the header / footer picture is not printed */
    val topMm: Int = 45,
    val bottomMm: Int = 28,
    val marginMm: Int = 12,
    val fontSize: Float = 9.5f,
    /** print header / footer / background pictures (turn off for pre-printed letterhead paper) */
    val letterhead: Boolean = true,
    val showBarcode: Boolean = true,
    val sig1Html: String = "",
    val sig2Html: String = "",
    val sig3Html: String = "",
    val sig1Image: String? = null,
    val sig2Image: String? = null,
    val sig3Image: String? = null
)

/** One incoming SMS question from a patient; kept as a persistent queue. */
@Entity(tableName = "sms_requests")
data class SmsRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long = System.currentTimeMillis(),
    /** 0 = waiting, 1 = replied, 2 = ignored (spam / duplicate / too old), 3 = failed */
    val status: Int = STATUS_PENDING,
    val reply: String = "",
    val handledAt: Long = 0L
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_DONE = 1
        const val STATUS_IGNORED = 2
        const val STATUS_FAILED = 3
    }
}
