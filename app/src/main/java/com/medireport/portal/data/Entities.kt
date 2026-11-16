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
    val createdAt: Long = System.currentTimeMillis()
)

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
    val createdAt: Long = System.currentTimeMillis()
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
    val content: String,
    /** comma separated custom placeholders detected from [content] */
    val parameters: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "page_setup")
data class PageSetup(
    @PrimaryKey val id: Int = 1,
    val headerHtml: String = "",
    val footerHtml: String = "",
    val headerImage: String? = null,
    val footerImage: String? = null,
    val backgroundImage: String? = null,
    val backgroundOpacity: Float = 0.1f,
    val headerHeightMm: Int = 30,
    val footerHeightMm: Int = 20
)
