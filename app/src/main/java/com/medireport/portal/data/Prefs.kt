package com.medireport.portal.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.medireport.portal.util.Security
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "medireport_prefs")

enum class Role { NONE, ADMIN, PATIENT }

data class Session(val role: Role = Role.NONE, val patientId: Long = 0L)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class Prefs(private val context: Context) {
    private val ds get() = context.dataStore

    private object K {
        val adminUser = stringPreferencesKey("admin_user")
        val adminHash = stringPreferencesKey("admin_hash")
        val adminDefault = booleanPreferencesKey("admin_default_password")
        val role = stringPreferencesKey("session_role")
        val patientId = longPreferencesKey("session_patient")
        val theme = stringPreferencesKey("theme")
        val onboarded = booleanPreferencesKey("onboarded")
        fun fails(scope: String) = intPreferencesKey("fails_$scope")
        fun lockUntil(scope: String) = longPreferencesKey("lock_until_$scope")
    }

    val session: Flow<Session> = ds.data.map { p ->
        val role = runCatching { Role.valueOf(p[K.role] ?: "NONE") }.getOrDefault(Role.NONE)
        Session(role, p[K.patientId] ?: 0L)
    }

    val themeMode: Flow<ThemeMode> = ds.data.map { p ->
        runCatching { ThemeMode.valueOf(p[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }

    val adminUsername: Flow<String> = ds.data.map { it[K.adminUser] ?: DEFAULT_ADMIN_USER }
    val adminUsesDefaultPassword: Flow<Boolean> = ds.data.map { it[K.adminDefault] ?: true }
    val onboarded: Flow<Boolean> = ds.data.map { it[K.onboarded] ?: false }

    suspend fun setOnboarded() = ds.edit { it[K.onboarded] = true }

    suspend fun setTheme(mode: ThemeMode) = ds.edit { it[K.theme] = mode.name }

    suspend fun startSession(role: Role, patientId: Long = 0L) = ds.edit {
        it[K.role] = role.name
        it[K.patientId] = patientId
    }

    suspend fun endSession() = ds.edit {
        it[K.role] = Role.NONE.name
        it[K.patientId] = 0L
    }

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
        val userOk = user.equals(username.trim(), ignoreCase = true)
        val passOk = Security.verify(password, hash)
        return userOk && passOk
    }

    suspend fun changeAdminCredentials(username: String, newPassword: String) {
        val h = Security.hash(newPassword)
        ds.edit {
            it[K.adminUser] = username.trim()
            it[K.adminHash] = h
            it[K.adminDefault] = false
        }
    }

    // ---- brute-force protection ----
    /** Returns remaining lock time in ms (0 when not locked). */
    suspend fun lockRemaining(scope: String): Long {
        val until = ds.data.first()[K.lockUntil(scope)] ?: 0L
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Records a failed attempt; locks for 30s after every 5th failure. Returns remaining lock ms. */
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

    companion object {
        const val DEFAULT_ADMIN_USER = "admin"
        const val DEFAULT_ADMIN_PASSWORD = "admin123"
        const val MAX_ATTEMPTS = 5
        const val LOCK_MS = 30_000L
    }
}
