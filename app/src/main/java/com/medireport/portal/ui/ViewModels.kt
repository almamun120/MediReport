package com.medireport.portal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medireport.portal.App
import com.medireport.portal.data.Patient
import com.medireport.portal.data.Repository
import com.medireport.portal.data.ReportWithPatient
import com.medireport.portal.data.Role
import com.medireport.portal.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RepoViewModelFactory(private val repo: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        modelClass.getConstructor(Repository::class.java).newInstance(repo)
}

@Composable
fun rememberRepo(): Repository = (LocalContext.current.applicationContext as App).repository

@Composable
inline fun <reified VM : ViewModel> repoViewModel(): VM =
    viewModel(factory = RepoViewModelFactory(rememberRepo()))

// ---------------------------------------------------------------- Auth

sealed interface AuthResult {
    data object Success : AuthResult
    data object Invalid : AuthResult
    data class Locked(val seconds: Int) : AuthResult
}

class AuthViewModel(private val repo: Repository) : ViewModel() {
    private val prefs get() = repo.prefs

    private fun secs(ms: Long) = ((ms + 999) / 1000).toInt()

    suspend fun adminLogin(user: String, pass: String): AuthResult {
        val lock = prefs.lockRemaining("admin")
        if (lock > 0) return AuthResult.Locked(secs(lock))
        val ok = withContext(Dispatchers.Default) { prefs.verifyAdmin(user, pass) }
        return if (ok) {
            prefs.clearFailures("admin")
            prefs.startSession(Role.ADMIN)
            AuthResult.Success
        } else {
            val l = prefs.recordFailure("admin")
            if (l > 0) AuthResult.Locked(secs(l)) else AuthResult.Invalid
        }
    }

    suspend fun patientLogin(code: String, phone: String): AuthResult {
        val lock = prefs.lockRemaining("patient")
        if (lock > 0) return AuthResult.Locked(secs(lock))
        val p = repo.patientLogin(code, phone)
        return if (p != null) {
            prefs.clearFailures("patient")
            prefs.startSession(Role.PATIENT, p.id)
            AuthResult.Success
        } else {
            val l = prefs.recordFailure("patient")
            if (l > 0) AuthResult.Locked(secs(l)) else AuthResult.Invalid
        }
    }

    fun logout(after: () -> Unit = {}) {
        viewModelScope.launch {
            prefs.endSession()
            after()
        }
    }
}

// ---------------------------------------------------------------- Dashboard

data class DashboardState(
    val patients: Int = 0,
    val reports: Int = 0,
    val templates: Int = 0,
    val recent: List<ReportWithPatient> = emptyList(),
    val chart: List<Pair<String, Int>> = emptyList()
)

class DashboardViewModel(repo: Repository) : ViewModel() {
    val state: StateFlow<DashboardState> = combine(
        repo.patientCount, repo.reportCount, repo.templateCount, repo.allReports
    ) { p, r, t, all ->
        DashboardState(p, r, t, all.take(5), last7Days(all))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    val usesDefaultPassword: StateFlow<Boolean> =
        repo.prefs.adminUsesDefaultPassword.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun last7Days(all: List<ReportWithPatient>): List<Pair<String, Int>> {
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        val day = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val result = ArrayList<Pair<String, Int>>()
        for (i in 6 downTo 0) {
            val start = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }
            val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val n = all.count { it.report.createdAt >= start.timeInMillis && it.report.createdAt < end.timeInMillis }
            result.add(fmt.format(start.time) to n)
        }
        return result
    }
}

// ---------------------------------------------------------------- Patients

enum class PatientSort { NEWEST, NAME, ID }

class PatientsViewModel(private val repo: Repository) : ViewModel() {
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(PatientSort.NEWEST)

    val list: StateFlow<List<Patient>> = combine(repo.patients, query, sort) { all, q, s ->
        val needle = q.trim().lowercase()
        val filtered = if (needle.isEmpty()) all else all.filter {
            it.name.lowercase().contains(needle) ||
                it.patientCode.lowercase().contains(needle) ||
                it.phone.contains(needle)
        }
        when (s) {
            PatientSort.NEWEST -> filtered
            PatientSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            PatientSort.ID -> filtered.sortedBy { it.patientCode.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(p: Patient) {
        viewModelScope.launch { repo.deletePatient(p) }
    }
}

// ---------------------------------------------------------------- Settings

class SettingsViewModel(private val repo: Repository) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> =
        repo.prefs.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
    val adminUser: StateFlow<String> =
        repo.prefs.adminUsername.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "admin")

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { repo.prefs.setTheme(mode) }
    }

    /** Verifies the current password first. Returns true if changed. */
    suspend fun changeCredentials(current: String, newUser: String, newPass: String): Boolean {
        val user = repo.prefs.adminUsername.first()
        val ok = withContext(Dispatchers.Default) { repo.prefs.verifyAdmin(user, current) }
        if (!ok) return false
        withContext(Dispatchers.Default) { repo.prefs.changeAdminCredentials(newUser, newPass) }
        return true
    }
}
