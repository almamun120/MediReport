package com.medireport.portal.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.medireport.portal.util.Security
import com.medireport.portal.util.SecretBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.dataStore by preferencesDataStore(name = "medireport_prefs")

enum class Role { NONE, ADMIN }

data class Session(val role: Role = Role.NONE)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SmtpConfig(
    val host: String = "",
    val port: Int = 465,
    /** SSL | STARTTLS | NONE */
    val security: String = "SSL",
    val username: String = "",
    val password: String = "",
    val fromEmail: String = "",
    val fromName: String = ""
) {
    val isComplete: Boolean get() = host.isNotBlank() && fromEmail.isNotBlank()
}

enum class OtpResult { OK, WRONG, EXPIRED, TOO_MANY }

data class PortalConfig(val port: Int = 8080, val autoStart: Boolean = false)

/** SMS report lookup: enabled flag and an optional public web address put into replies. */
data class SmsConfig(val enabled: Boolean = false, val baseUrl: String = "")

class Prefs(private val context: Context) {
    private val ds get() = context.dataStore

    private object K {
        val adminUser = stringPreferencesKey("admin_user")
        val adminHash = stringPreferencesKey("admin_hash")
        val adminDefault = booleanPreferencesKey("admin_default_password")
        val role = stringPreferencesKey("session_role")
        val theme = stringPreferencesKey("theme")

        val smtpHost = stringPreferencesKey("smtp_host")
        val smtpPort = intPreferencesKey("smtp_port")
        val smtpSecurity = stringPreferencesKey("smtp_security")
        val smtpUser = stringPreferencesKey("smtp_user")
        val smtpPass = stringPreferencesKey("smtp_pass_enc")
        val smtpFrom = stringPreferencesKey("smtp_from")
        val smtpFromName = stringPreferencesKey("smtp_from_name")
        val recoveryEmail = stringPreferencesKey("recovery_email")
        val mailVerified = booleanPreferencesKey("mail_verified")
        val mailSubject = stringPreferencesKey("mail_subject")
        val mailBody = stringPreferencesKey("mail_body")

        val otpHash = stringPreferencesKey("otp_hash")
        val otpExpiry = longPreferencesKey("otp_expiry")
        val otpTries = intPreferencesKey("otp_tries")

        val portalPort = intPreferencesKey("portal_port")
        val portalAuto = booleanPreferencesKey("portal_autostart")
        val smsEnabled = booleanPreferencesKey("sms_enabled")
        val smsBaseUrl = stringPreferencesKey("sms_base_url")

        fun fails(scope: String) = intPreferencesKey("fails_$scope")
        fun lockUntil(scope: String) = longPreferencesKey("lock_until_$scope")
    }

    // ---------------------------------------------------------------- session / theme
    val session: Flow<Session> = ds.data.map { p ->
        Session(runCatching { Role.valueOf(p[K.role] ?: "NONE") }.getOrDefault(Role.NONE))
    }

    val themeMode: Flow<ThemeMode> = ds.data.map { p ->
        runCatching { ThemeMode.valueOf(p[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setTheme(mode: ThemeMode) = ds.edit { it[K.theme] = mode.name }
    suspend fun startSession() = ds.edit { it[K.role] = Role.ADMIN.name }
    suspend fun endSession() = ds.edit { it[K.role] = Role.NONE.name }

    // ---------------------------------------------------------------- admin account
    val adminUsername: Flow<String> = ds.data.map { it[K.adminUser] ?: DEFAULT_ADMIN_USER }
    val adminUsesDefaultPassword: Flow<Boolean> = ds.data.map { it[K.adminDefault] ?: true }

    /** Creates the default admin account on first launch (admin / admin123). */
    suspend fun ensureAdmin() {
        val p = ds.data.first()
        if (p[K.adminHash] == null) {
            val h = Security.hash(DEFAULT_ADMIN_PASSWORD)
            ds.edit {
                it[K.adminUser] = DEFAULT_ADMIN_USER
                it[K.adminHash] = h
                it[K.adminDefault] = true
            }
        }
    }

    suspend fun verifyAdmin(username: String, password: String): Boolean {
        val p = ds.data.first()
        val user = p[K.adminUser] ?: DEFAULT_ADMIN_USER
        val hash = p[K.adminHash] ?: return false
        return user.equals(username.trim(), ignoreCase = true) && Security.verify(password, hash)
    }

    suspend fun changeAdminCredentials(username: String, newPassword: String) {
        val h = Security.hash(newPassword)
        ds.edit {
            it[K.adminUser] = username.trim()
            it[K.adminHash] = h
            it[K.adminDefault] = false
        }
    }

    /** Used by the e-mail recovery flow: keeps the username, sets a new password. */
    suspend fun resetAdminPassword(newPassword: String) {
        val h = Security.hash(newPassword)
        ds.edit {
            it[K.adminHash] = h
            it[K.adminDefault] = false
        }
    }

    // ---------------------------------------------------------------- brute-force protection
    suspend fun lockRemaining(scope: String): Long {
        val until = ds.data.first()[K.lockUntil(scope)] ?: 0L
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    suspend fun recordFailure(scope: String): Long {
        var lock = 0L
        ds.edit {
            val n = (it[K.fails(scope)] ?: 0) + 1
            it[K.fails(scope)] = n
            if (n >= MAX_ATTEMPTS) {
                lock = LOCK_MS
                it[K.lockUntil(scope)] = System.currentTimeMillis() + LOCK_MS
                it[K.fails(scope)] = 0
            }
        }
        return lock
    }

    suspend fun clearFailures(scope: String) = ds.edit {
        it[K.fails(scope)] = 0
        it[K.lockUntil(scope)] = 0L
    }

    // ---------------------------------------------------------------- e-mail
    val smtp: Flow<SmtpConfig> = ds.data.map { readSmtp(it) }
    suspend fun getSmtp(): SmtpConfig = readSmtp(ds.data.first())

    private fun readSmtp(p: androidx.datastore.preferences.core.Preferences) = SmtpConfig(
        host = p[K.smtpHost] ?: "",
        port = p[K.smtpPort] ?: 465,
        security = p[K.smtpSecurity] ?: "SSL",
        username = p[K.smtpUser] ?: "",
        password = SecretBox.decrypt(p[K.smtpPass] ?: ""),
        fromEmail = p[K.smtpFrom] ?: "",
        fromName = p[K.smtpFromName] ?: ""
    )

    suspend fun saveSmtp(c: SmtpConfig) {
        val enc = SecretBox.encrypt(c.password)
        ds.edit {
            val old = readSmtp(it)
            it[K.smtpHost] = c.host
            it[K.smtpPort] = c.port
            it[K.smtpSecurity] = c.security
            it[K.smtpUser] = c.username
            it[K.smtpPass] = enc
            it[K.smtpFrom] = c.fromEmail
            it[K.smtpFromName] = c.fromName
            if (old != c) it[K.mailVerified] = false
        }
    }

    val recoveryEmail: Flow<String> = ds.data.map { it[K.recoveryEmail] ?: "" }
    val mailVerified: Flow<Boolean> = ds.data.map { it[K.mailVerified] ?: false }

    suspend fun saveRecoveryEmail(email: String) = ds.edit {
        if ((it[K.recoveryEmail] ?: "") != email.trim()) it[K.mailVerified] = false
        it[K.recoveryEmail] = email.trim()
    }

    suspend fun setMailVerified(v: Boolean) = ds.edit { it[K.mailVerified] = v }

    /** Recovery works only with a complete, tested mail server and a recovery address. */
    suspend fun recoveryReady(): Boolean {
        val p = ds.data.first()
        return readSmtp(p).isComplete && (p[K.recoveryEmail] ?: "").isNotBlank() && (p[K.mailVerified] ?: false)
    }

    val mailSubject: Flow<String> = ds.data.map { it[K.mailSubject] ?: DEFAULT_MAIL_SUBJECT }
    val mailBody: Flow<String> = ds.data.map { it[K.mailBody] ?: DEFAULT_MAIL_BODY }
    suspend fun saveMailTemplate(subject: String, body: String) = ds.edit {
        it[K.mailSubject] = subject
        it[K.mailBody] = body
    }

    // ---------------------------------------------------------------- one-time code (password recovery)
    /** Returns the new 6-digit code (only the hash is stored). */
    suspend fun createOtp(): String {
        val code = (SecureRandom().nextInt(900000) + 100000).toString()
        val h = Security.hash(code)
        ds.edit {
            it[K.otpHash] = h
            it[K.otpExpiry] = System.currentTimeMillis() + OTP_VALID_MS
            it[K.otpTries] = 0
        }
        return code
    }

    suspend fun verifyOtp(code: String): OtpResult {
        val p = ds.data.first()
        val hash = p[K.otpHash] ?: return OtpResult.EXPIRED
        if (System.currentTimeMillis() > (p[K.otpExpiry] ?: 0L)) return OtpResult.EXPIRED
        if ((p[K.otpTries] ?: 0) >= MAX_ATTEMPTS) return OtpResult.TOO_MANY
        return if (Security.verify(code.trim(), hash)) {
            ds.edit { it.remove(K.otpHash); it[K.otpExpiry] = 0L }
            OtpResult.OK
        } else {
            ds.edit { it[K.otpTries] = (it[K.otpTries] ?: 0) + 1 }
            OtpResult.WRONG
        }
    }

    // ---------------------------------------------------------------- patient web portal
    val portal: Flow<PortalConfig> = ds.data.map {
        PortalConfig(it[K.portalPort] ?: 8080, it[K.portalAuto] ?: false)
    }

    suspend fun getPortal(): PortalConfig = portal.first()

    suspend fun savePortal(c: PortalConfig) = ds.edit {
        it[K.portalPort] = c.port
        it[K.portalAuto] = c.autoStart
    }

    companion object {
        const val DEFAULT_ADMIN_USER = "admin"
        const val DEFAULT_ADMIN_PASSWORD = "admin123"
        const val MAX_ATTEMPTS = 5
        const val LOCK_MS = 30_000L
        const val OTP_VALID_MS = 10 * 60_000L
        const val DEFAULT_MAIL_SUBJECT = "Your report from {clinic}"
        const val DEFAULT_MAIL_BODY = "Dear {patient},\n\nPlease find your report attached.\n\nRegards,\n{clinic}"
    }

    // ---------------------------------------------------------------- SMS lookup
    val sms: Flow<SmsConfig> = ds.data.map { SmsConfig(it[K.smsEnabled] ?: false, it[K.smsBaseUrl] ?: "") }

    suspend fun getSms(): SmsConfig = sms.first()

    suspend fun saveSms(c: SmsConfig) = ds.edit {
        it[K.smsEnabled] = c.enabled
        it[K.smsBaseUrl] = c.baseUrl.trim().trimEnd('/')
    }
}
