package com.medireport.portal.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.data.SmtpConfig
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.components.AppTopBar
import com.medireport.portal.ui.components.Chip
import com.medireport.portal.ui.components.PasswordField
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.util.Mailer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Mail server (SMTP) for sending reports and for admin password recovery.
 * Recovery becomes active only after a test mail to the recovery address succeeded.
 */
@Composable
fun MailSettingsScreen(onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val verified by repo.prefs.mailVerified.collectAsState(initial = false)

    var loaded by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("465") }
    var security by remember { mutableStateOf("SSL") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var fromName by remember { mutableStateOf("") }
    var recovery by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<String?>(null) }

    val msgSaved = stringResource(R.string.saved)
    val msgTestOk = stringResource(R.string.mail_test_ok)
    val errEmail = stringResource(R.string.err_email_invalid)
    val errRequired = stringResource(R.string.err_required)

    LaunchedEffect(Unit) {
        val c = repo.prefs.getSmtp()
        host = c.host; port = c.port.toString(); security = c.security
        user = c.username; pass = c.password; from = c.fromEmail; fromName = c.fromName
        recovery = repo.prefs.recoveryEmail.first()
        subject = repo.prefs.mailSubject.first()
        body = repo.prefs.mailBody.first()
        loaded = true
    }

    fun config() = SmtpConfig(
        host = host.trim(), port = port.toIntOrNull() ?: 465, security = security,
        username = user.trim(), password = pass, fromEmail = from.trim().ifBlank { user.trim() }, fromName = fromName.trim()
    )

    fun valid(): Boolean {
        submitted = true
        return host.isNotBlank() && port.toIntOrNull() != null &&
            Mailer.isValidEmail(config().fromEmail) && Mailer.isValidEmail(recovery)
    }

    suspend fun persist() {
        repo.prefs.saveSmtp(config())
        repo.prefs.saveRecoveryEmail(recovery)
        repo.prefs.saveMailTemplate(subject, body)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(stringResource(R.string.mail_settings), onBack = onBack) }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (verified) Color(0x2610B981) else Color(0x26F59E0B)
                )
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (verified) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                        tint = if (verified) Color(0xFF059669) else Color(0xFFD97706)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(if (verified) R.string.recovery_active else R.string.recovery_inactive),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(stringResource(R.string.mail_presets), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Gmail", false) { host = "smtp.gmail.com"; port = "465"; security = "SSL" }
                Chip("Outlook", false) { host = "smtp.office365.com"; port = "587"; security = "STARTTLS" }
                Chip("Yahoo", false) { host = "smtp.mail.yahoo.com"; port = "465"; security = "SSL" }
            }

            AppField(
                host, { host = it }, stringResource(R.string.smtp_host), leading = Icons.Filled.Dns,
                error = if (submitted && host.isBlank()) errRequired else null
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(
                    port, { port = it.filter(Char::isDigit).take(5) }, stringResource(R.string.smtp_port),
                    modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number
                )
            }
            Text(stringResource(R.string.smtp_security), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SSL", "STARTTLS", "NONE").forEach { m -> Chip(m, security == m) { security = m } }
            }
            AppField(user, { user = it }, stringResource(R.string.smtp_user), leading = Icons.Filled.Person, keyboardType = KeyboardType.Email)
            PasswordField(pass, { pass = it }, stringResource(R.string.smtp_password), Icons.Filled.Lock)
            Text(stringResource(R.string.app_password_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppField(
                from, { from = it }, stringResource(R.string.from_email), leading = Icons.Filled.Email, keyboardType = KeyboardType.Email,
                error = if (submitted && !Mailer.isValidEmail(config().fromEmail)) errEmail else null
            )
            AppField(fromName, { fromName = it }, stringResource(R.string.from_name))

            Text(stringResource(R.string.recovery_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            AppField(
                recovery, { recovery = it }, stringResource(R.string.recovery_email), leading = Icons.Filled.Email,
                keyboardType = KeyboardType.Email,
                error = if (submitted && !Mailer.isValidEmail(recovery)) errEmail else null,
                supporting = stringResource(R.string.recovery_email_hint)
            )
            testError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (valid()) {
                            busy = true; testError = null
                            scope.launch {
                                persist()
                                val e = repo.sendTestMail(recovery.trim())
                                busy = false
                                if (e == null) snackbar.showSnackbar(msgTestOk) else testError = e
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.send_test_mail))
                }
                Button(
                    onClick = {
                        if (valid()) scope.launch { persist(); snackbar.showSnackbar(msgSaved) }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.save)) }
            }

            Text(stringResource(R.string.mail_template), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            AppField(subject, { subject = it }, stringResource(R.string.email_subject))
            AppField(body, { body = it }, stringResource(R.string.email_body), singleLine = false, minLines = 5)
            Text(stringResource(R.string.mail_placeholders), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = { scope.launch { repo.prefs.saveMailTemplate(subject, body); snackbar.showSnackbar(msgSaved) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.save_template)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
