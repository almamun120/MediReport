package com.medireport.portal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.data.Patient
import com.medireport.portal.data.Report
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.util.Mailer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Lets the admin e-mail a report (PDF/image attached) to the patient or any address. */
@Composable
fun SendEmailDialog(
    report: Report,
    patient: Patient,
    onDismiss: () -> Unit,
    onResult: (String?) -> Unit   // null = sent, otherwise error text
) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var to by remember { mutableStateOf(patient.email) }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var smtpOk by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalid = stringResource(R.string.err_email_invalid)

    LaunchedEffect(Unit) {
        smtpOk = repo.prefs.getSmtp().isComplete
        val clinic = repo.clinicName()
        val vars = mapOf("patient" to patient.name, "clinic" to clinic, "report" to report.title, "id" to patient.patientCode)
        subject = Mailer.fill(repo.prefs.mailSubject.first(), vars)
        body = Mailer.fill(repo.prefs.mailBody.first(), vars)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.send_email)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (smtpOk == false) {
                    Text(stringResource(R.string.mail_not_configured), color = MaterialTheme.colorScheme.error)
                } else {
                    AppField(to, { to = it; error = null }, stringResource(R.string.email_to),
                        leading = Icons.Filled.Email, keyboardType = KeyboardType.Email)
                    AppField(subject, { subject = it }, stringResource(R.string.email_subject))
                    AppField(body, { body = it }, stringResource(R.string.email_body), singleLine = false, minLines = 4)
                    Text(stringResource(R.string.email_attached, report.title), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (busy) {
                        Row { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                    }
                }
            }
        },
        confirmButton = {
            if (smtpOk == true) {
                TextButton(
                    enabled = !busy && subject.isNotBlank(),
                    onClick = {
                        if (!Mailer.isValidEmail(to)) { error = invalid; return@TextButton }
                        busy = true
                        scope.launch {
                            val e = repo.sendReportEmail(report, patient, to.trim(), subject, body)
                            busy = false
                            if (e == null) { onResult(null); onDismiss() } else error = e
                        }
                    }
                ) { Text(stringResource(R.string.send)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } }
    )
}
