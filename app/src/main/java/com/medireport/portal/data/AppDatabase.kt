package com.medireport.portal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Patient::class, Report::class, Template::class, PageSetup::class, SmsRequest::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun reportDao(): ReportDao
    abstract fun templateDao(): TemplateDao
    abstract fun pageSetupDao(): PageSetupDao
    abstract fun smsDao(): SmsDao

    companion object {
        /**
         * v1 -> v2: patients and reports keep their data. Templates and page setup use a new
         * structure (table based report layout), so they are re-created and the defaults are seeded again.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE patients ADD COLUMN ageMonths INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE patients ADD COLUMN ageDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE patients ADD COLUMN email TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reports ADD COLUMN published INTEGER NOT NULL DEFAULT 0")

                db.execSQL("DROP TABLE IF EXISTS templates")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `title` TEXT NOT NULL, `specimen` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )

                db.execSQL("DROP TABLE IF EXISTS page_setup")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `page_setup` (`id` INTEGER PRIMARY KEY NOT NULL, " +
                        "`clinicName` TEXT NOT NULL, `headerHtml` TEXT NOT NULL, `footerHtml` TEXT NOT NULL, " +
                        "`headerImage` TEXT, `footerImage` TEXT, `backgroundImage` TEXT, " +
                        "`backgroundOpacity` REAL NOT NULL, `watermarkImage` TEXT, `watermarkOpacity` REAL NOT NULL, " +
                        "`watermarkSizeMm` INTEGER NOT NULL, `topMm` INTEGER NOT NULL, `bottomMm` INTEGER NOT NULL, " +
                        "`marginMm` INTEGER NOT NULL, `fontSize` REAL NOT NULL, `letterhead` INTEGER NOT NULL, " +
                        "`showBarcode` INTEGER NOT NULL, `sig1Html` TEXT NOT NULL, `sig2Html` TEXT NOT NULL, " +
                        "`sig3Html` TEXT NOT NULL, `sig1Image` TEXT, `sig2Image` TEXT, `sig3Image` TEXT)"
                )
            }
        }

        /** v2 -> v3: adds the SMS request queue. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sms_requests` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sender` TEXT NOT NULL, `body` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, " +
                        "`status` INTEGER NOT NULL, `reply` TEXT NOT NULL, `handledAt` INTEGER NOT NULL)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "medireport.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
