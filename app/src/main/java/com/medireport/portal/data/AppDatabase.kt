package com.medireport.portal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Patient::class, Report::class, Template::class, PageSetup::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun reportDao(): ReportDao
    abstract fun templateDao(): TemplateDao
    abstract fun pageSetupDao(): PageSetupDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "medireport.db")
                .build()
    }
}
