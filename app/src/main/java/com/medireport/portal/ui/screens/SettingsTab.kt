package com.medireport.portal.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.medireport.portal.BuildConfig
import com.medireport.portal.R
import com.medireport.portal.data.ThemeMode
import com.medireport.portal.ui.SettingsViewModel
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.components.Chip
import com.medireport.portal.ui.components.ConfirmDialog
import com.medireport.portal.ui.components.PasswordField
import com.medireport.portal.ui.repoViewModel
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun SettingsTab(snackbar: SnackbarHostState, onPageSetup: () -> Unit, onLogout: () -> Unit) {
    val vm: SettingsViewModel = repoViewModel()
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val theme by vm.themeMode.collectAsState()
    val adminUser by vm.adminUser.collectAsState()
    var lang by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags()) }
    var showCreds by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val msgBackupOk = stringResource(R.string.backup_ok)
    val msgBackupFail = stringResource(R.string.backup_fail)
    val msgRestoreOk = stringResource(R.string.restore_ok)
    val msgRestoreFail = stringResource(R.string.restore_fail)
    val msgCredsOk = stringResource(R.string.creds_changed)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val ok = repo.exportBackup(uri)
                busy = false
                snackbar.showSnackbar(if (ok) msgBackupOk else msgBackupFail)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsCard(Icons.Outlined.Palette, stringResource(R.string.appearance)) {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.theme_system), theme == ThemeMode.SYSTEM) { vm.setTheme(ThemeMode.SYSTEM) }
                Chip(stringResource(R.string.theme_light), theme == ThemeMode.LIGHT) { vm.setTheme(ThemeMode.LIGHT) }
                Chip(stringResource(R.string.theme_dark), theme == ThemeMode.DARK) { vm.setTheme(ThemeMode.DARK) }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Language, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.theme_system), lang.isEmpty()) { lang = ""; setLanguage("") }
                Chip("English", lang.startsWith("en")) { lang = "en"; setLanguage("en") }
                Chip("বাংলা", lang.startsWith("bn")) { lang = "bn"; setLanguage("bn") }
            }
        }

        SettingsCard(Icons.Outlined.Tune, stringResource(R.string.report_layout)) {
            ActionRow(stringResource(R.string.page_setup), stringResource(R.string.page_setup_desc), onPageSetup)
        }

        SettingsCard(Icons.Outlined.Security, stringResource(R.string.security)) {
            ActionRow(
                stringResource(R.string.change_credentials),
                stringResource(R.string.signed_in_as, adminUser)
            ) { showCreds = true }
        }

        SettingsCard(Icons.Outlined.Backup, stringResource(R.string.data_backup)) {
            Text(stringResource(R.string.backup_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { exportLauncher.launch("medireport_backup_${Formatters.stamp()}.zip") },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Outlined.Backup, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.backup)) }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.restore)) }
            }
            if (busy) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.please_wait), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SettingsCard(Icons.Outlined.Info, stringResource(R.string.about)) {
            Text(stringResource(R.string.app_name) + "  v" + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Outlined.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.logout))
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showCreds) {
        ChangeCredentialsDialog(
            currentUser = adminUser,
            onDismiss = { showCreds = false },
            onSubmit = { cur, user, pass ->
                val ok = vm.changeCredentials(cur, user, pass)
                if (ok) {
                    showCreds = false
                    snackbar.showSnackbar(msgCredsOk)
                }
                ok
            }
        )
    }

    restoreUri?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.restore),
            message = stringResource(R.string.restore_warning),
            confirmText = stringResource(R.string.restore),
            onConfirm = {
                restoreUri = null
                busy = true
                scope.launch {
                    val ok = repo.restoreBackup(uri)
                    busy = false
                    snackbar.showSnackbar(if (ok) msgRestoreOk else msgRestoreFail)
                }
            },
            onDismiss = { restoreUri = null }
        )
    }
}

private fun setLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(
        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
    )
}

@Composable
private fun SettingsCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChangeCredentialsDialog(
    currentUser: String,
    onDismiss: () -> Unit,
    onSubmit: suspend (current: String, user: String, pass: String) -> Boolean
) {
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf("") }
    var user by remember { mutableStateOf(currentUser) }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.change_credentials)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(cur, { cur = it; error = null }, stringResource(R.string.current_password), Icons.Filled.Lock)
                AppField(user, { user = it; error = null }, stringResource(R.string.username), leading = Icons.Filled.Person)
                PasswordField(pass, { pass = it; error = null }, stringResource(R.string.new_password), Icons.Filled.Lock)
                PasswordField(confirm, { confirm = it; error = null }, stringResource(R.string.confirm_password), Icons.Filled.Lock)
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    when {
                        user.isBlank() -> error = R.string.err_username_required
                        pass.length < 6 -> error = R.string.err_password_short
                        pass != confirm -> error = R.string.err_password_mismatch
                        else -> {
                            loading = true
                            scope.launch {
                                val ok = onSubmit(cur, user, pass)
                                loading = false
                                if (!ok) error = R.string.err_current_wrong
                            }
                        }
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text(stringResource(R.string.cancel)) } }
    )
}
