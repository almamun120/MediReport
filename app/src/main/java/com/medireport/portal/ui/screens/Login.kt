package com.medireport.portal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.data.OtpResult
import com.medireport.portal.data.Prefs
import com.medireport.portal.ui.AuthResult
import com.medireport.portal.ui.AuthViewModel
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.components.PasswordField
import com.medireport.portal.ui.repoViewModel
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.ui.theme.heroBrush
import com.medireport.portal.ui.theme.isAppDark
import kotlinx.coroutines.launch

@Composable
private fun LoginScaffold(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.fillMaxWidth().background(heroBrush(isAppDark())).statusBarsPadding()) {
            Column(Modifier.padding(start = 8.dp, end = 24.dp, bottom = 56.dp)) {
                Spacer(Modifier.height(24.dp))
                Column(Modifier.padding(start = 16.dp)) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, Modifier.size(30.dp), tint = Color.White) }
                    Spacer(Modifier.height(14.dp))
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
        Card(
            Modifier.padding(horizontal = 20.dp).padding(top = 0.dp).navigationBarsPadding(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(Modifier.padding(20.dp)) { content() }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun errorText(r: AuthResult, invalidRes: Int): String? = when (r) {
    is AuthResult.Invalid -> stringResource(invalidRes)
    is AuthResult.Locked -> stringResource(R.string.err_locked, r.seconds)
    AuthResult.Success -> null
}

@Composable
fun AdminLoginScreen(onSuccess: () -> Unit) {
    val vm: AuthViewModel = repoViewModel()
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val isDefault by repo.prefs.adminUsesDefaultPassword.collectAsState(initial = false)
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AuthResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }

    LoginScaffold(
        Icons.Filled.AdminPanelSettings,
        stringResource(R.string.admin_login),
        stringResource(R.string.admin_login_sub)
    ) {
        AppField(user, { user = it; result = null }, stringResource(R.string.username), leading = Icons.Filled.Person)
        Spacer(Modifier.height(10.dp))
        PasswordField(pass, { pass = it; result = null }, stringResource(R.string.password), Icons.Filled.Lock)
        result?.let { r ->
            errorText(r, R.string.err_admin_invalid)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (isDefault) {
            Text(
                stringResource(R.string.default_cred_hint, Prefs.DEFAULT_ADMIN_USER, Prefs.DEFAULT_ADMIN_PASSWORD),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                loading = true
                scope.launch {
                    val r = vm.adminLogin(user, pass)
                    loading = false
                    if (r == AuthResult.Success) onSuccess() else result = r
                }
            },
            enabled = !loading && user.isNotBlank() && pass.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else Text(stringResource(R.string.login))
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = { showForgot = true }) { Text(stringResource(R.string.forgot_password)) }
        }
    }
    if (showForgot) ForgotPasswordDialog(onDismiss = { showForgot = false })
}

private enum class ForgotStep { SEND, CODE }

@Composable
private fun ForgotPasswordDialog(onDismiss: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val email by repo.prefs.recoveryEmail.collectAsState(initial = "")
    var ready by remember { mutableStateOf<Boolean?>(null) }
    var step by remember { mutableStateOf(ForgotStep.SEND) }
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ready = repo.recoveryReady() }

    val errShort = stringResource(R.string.err_password_short)
    val errMismatch = stringResource(R.string.err_password_mismatch)
    val errCode = stringResource(R.string.err_code_wrong)
    val errExpired = stringResource(R.string.err_code_expired)
    val errMany = stringResource(R.string.err_code_many)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.forgot_password)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    ready == null -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    ready == false -> Text(stringResource(R.string.recovery_not_ready), style = MaterialTheme.typography.bodyMedium)
                    done -> Text(stringResource(R.string.password_reset_ok), style = MaterialTheme.typography.bodyMedium)
                    step == ForgotStep.SEND -> Text(
                        stringResource(R.string.recovery_send_desc, maskEmail(email)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    else -> {
                        Text(stringResource(R.string.recovery_code_sent), style = MaterialTheme.typography.bodyMedium)
                        AppField(code, { code = it.filter(Char::isDigit).take(6); error = null }, stringResource(R.string.recovery_code),
                            keyboardType = KeyboardType.Number)
                        PasswordField(pass, { pass = it; error = null }, stringResource(R.string.new_password), Icons.Filled.Lock)
                        PasswordField(confirm, { confirm = it; error = null }, stringResource(R.string.confirm_password), Icons.Filled.Lock)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (ready == true && !done) {
                TextButton(
                    enabled = !busy && (step == ForgotStep.SEND || (code.length == 6 && pass.isNotEmpty())),
                    onClick = {
                        if (step == ForgotStep.SEND) {
                            busy = true; error = null
                            scope.launch {
                                val e = repo.sendRecoveryCode()
                                busy = false
                                if (e == null) step = ForgotStep.CODE else error = e
                            }
                        } else if (pass.length < 6) error = errShort
                        else if (pass != confirm) error = errMismatch
                        else {
                            busy = true; error = null
                            scope.launch {
                                val r = repo.resetPasswordWithCode(code, pass)
                                busy = false
                                when (r) {
                                    OtpResult.OK -> done = true
                                    OtpResult.WRONG -> error = errCode
                                    OtpResult.EXPIRED -> error = errExpired
                                    OtpResult.TOO_MANY -> error = errMany
                                }
                            }
                        }
                    }
                ) { Text(stringResource(if (step == ForgotStep.SEND) R.string.send_code else R.string.reset_password)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(if (done) R.string.close else R.string.cancel)) } }
    )
}

private fun maskEmail(e: String): String {
    val at = e.indexOf('@')
    if (at < 2) return e
    return e.substring(0, 1) + "***" + e.substring(at - 1)
}
