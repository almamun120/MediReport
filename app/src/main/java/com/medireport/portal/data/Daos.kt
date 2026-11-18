package com.medireport.portal.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE id = :id")
    fun observe(id: Long): Flow<Patient?>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getById(id: Long): Patient?

    @Query("SELECT * FROM patients")
    suspend fun getAll(): List<Patient>

    @Query("SELECT * FROM patients WHERE lower(patientCode) = lower(:code) LIMIT 1")
    suspend fun byCode(code: String): Patient?

    @Query("SELECT * FROM patients WHERE lower(patientCode) = lower(:code) AND phone = :phone LIMIT 1")
    suspend fun login(code: String, phone: String): Patient?

    @Query("SELECT COUNT(*) FROM patients WHERE lower(patientCode) = lower(:code) AND id != :excludeId")
    suspend fun codeCount(code: String, excludeId: Long): Int

    @Query("SELECT COUNT(*) FROM patients")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM patients")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(patient: Patient): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(patients: List<Patient>)

    @Update
    suspend fun update(patient: Patient)

    @Delete
    suspend fun delete(patient: Patient)

    @Query("DELETE FROM patients")
    suspend fun deleteAll()
}

@Dao
interface ReportDao {
    @Query("SELECT * FROM reports WHERE patientId = :patientId ORDER BY createdAt DESC")
    fun observeByPatient(patientId: Long): Flow<List<Report>>

    @Query(
        "SELECT r.*, p.name AS patientName, p.patientCode AS patientCode " +
            "FROM reports r INNER JOIN patients p ON p.id = r.patientId ORDER BY r.createdAt DESC"
    )
    fun observeAllWithPatient(): Flow<List<ReportWithPatient>>

    @Query("SELECT * FROM reports WHERE patientId = :patientId")
    suspend fun getByPatient(patientId: Long): List<Report>

    @Query("SELECT * FROM reports")
    suspend fun getAll(): List<Report>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getById(id: Long): Report?

    @Query("SELECT * FROM reports WHERE patientId = :patientId AND published = 1 ORDER BY createdAt DESC")
    suspend fun getPublishedByPatient(patientId: Long): List<Report>

    @Query("UPDATE reports SET published = :published WHERE id = :id")
    suspend fun setPublished(id: Long, published: Boolean)

    @Query("UPDATE reports SET published = :published WHERE patientId = :patientId")
    suspend fun setAllPublished(patientId: Long, published: Boolean)

    @Query("SELECT COUNT(*) FROM reports")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: Report): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reports: List<Report>)

    @Delete
    suspend fun delete(report: Report)

    @Query("DELETE FROM reports")
    suspend fun deleteAll()
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Template>>

    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Template>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: Long): Template?

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM templates")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: Template): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<Template>)

    @Update
    suspend fun update(template: Template)

    @Delete
    suspend fun delete(template: Template)

    @Query("DELETE FROM templates")
    suspend fun deleteAll()
}

@Dao
interface PageSetupDao {
    @Query("SELECT * FROM page_setup WHERE id = 1")
    fun observe(): Flow<PageSetup?>

    @Query("SELECT * FROM page_setup WHERE id = 1")
    suspend fun get(): PageSetup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setup: PageSetup)
}

@Dao
interface SmsDao {
    @Insert
    suspend fun insert(r: SmsRequest): Long

    @Query("SELECT * FROM sms_requests WHERE status = 0 ORDER BY receivedAt ASC, id ASC LIMIT 1")
    suspend fun nextPending(): SmsRequest?

    @Query("SELECT COUNT(*) FROM sms_requests WHERE status = 0")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM sms_requests WHERE status = 0")
    fun observePending(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_requests WHERE sender = :sender AND receivedAt >= :since AND id <> :exceptId")
    suspend fun countFromSince(sender: String, since: Long, exceptId: Long): Int

    /** identical request from the same number that is already waiting or was answered recently */
    @Query("SELECT COUNT(*) FROM sms_requests WHERE sender = :sender AND body = :body AND id <> :exceptId AND receivedAt >= :since AND status IN (0, 1)")
    suspend fun duplicates(sender: String, body: String, since: Long, exceptId: Long): Int

    @Query("UPDATE sms_requests SET status = :status, reply = :reply, handledAt = :at WHERE id = :id")
    suspend fun finish(id: Long, status: Int, reply: String, at: Long)

    @Query("SELECT * FROM sms_requests ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SmsRequest>>

    @Query("DELETE FROM sms_requests WHERE receivedAt < :before AND status <> 0")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM sms_requests")
    suspend fun clear()
}
