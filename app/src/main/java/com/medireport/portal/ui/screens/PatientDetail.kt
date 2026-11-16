package com.medireport.portal.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.data.Report
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.components.AppTopBar
import com.medireport.portal.ui.components.Avatar
import com.medireport.portal.ui.components.ConfirmDialog
import com.medireport.portal.ui.components.EmptyState
import com.medireport.portal.ui.components.InfoRow
import com.medireport.portal.ui.components.ReportCard
import com.medireport.portal.ui.components.SectionTitle
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.util.Intents
import kotlinx.coroutines.launch

@Composable
fun PatientDetailScreen(
    patientId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNewReport: () -> Unit
) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val patient by repo.observePatient(patientId).collectAsState(initial = null)
    val reports by repo.observeReports(patientId).collectAsState(initial = emptyList())
    val snackbar = remember { SnackbarHostState() }

    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingTitle by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Report?>(null) }
    val uploadFail = stringResource(R.string.upload_failed)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri
            pendingTitle = (repo.store.displayName(uri) ?: "").substringBeforeLast('.')
        }
    }

    val p = patient
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(stringResource(R.string.patient_details), onBack = onBack) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
            }
        }
    ) { padding ->
        if (p == null) {
            Column(Modifier.fillMaxSize().padding(padding)) {}
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.name, 60)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(p.name, style = MaterialTheme.typography.titleLarge)
                                Text(p.patientCode, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        InfoRow(stringResource(R.string.sex), sexLabel(p.sex))
                        InfoRow(stringResource(R.string.age), p.age.toString())
                        InfoRow(stringResource(R.string.phone), p.phone)
                        InfoRow(stringResource(R.string.ref_by), p.refBy.ifBlank { "-" })
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { Intents.dial(context, p.phone) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.call))
                            }
                            OutlinedButton(onClick = { Intents.sms(context, p.phone) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Sms, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.sms))
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onNewReport, modifier = Modifier.weight(1f).height(50.dp), shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Filled.NoteAdd, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.generate_report))
                    }
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("application/pdf", "image/png", "image/jpeg")) },
                        modifier = Modifier.weight(1f).height(50.dp), shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.upload_report))
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.reports_count, reports.size)) }
            if (reports.isEmpty()) {
                item { EmptyState(Icons.Outlined.FolderOpen, stringResource(R.string.no_reports), stringResource(R.string.no_reports_patient_admin)) }
            } else {
                items(reports, key = { it.id }) { r ->
                    ReportCard(
                        report = r,
                        onOpen = { Intents.openFile(context, repo.store.reportFile(r.fileName), r.mimeType, repo.store) },
                        onShare = { Intents.shareFile(context, repo.store.reportFile(r.fileName), r.mimeType, r.title, repo.store) },
                        onDelete = { toDelete = r }
                    )
                }
            }
        }
    }

    pendingUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text(stringResource(R.string.report_name)) },
            text = { AppField(pendingTitle, { pendingTitle = it }, stringResource(R.string.report_name)) },
            confirmButton = {
                TextButton(
                    enabled = pendingTitle.isNotBlank(),
                    onClick = {
                        val title = pendingTitle.trim()
                        pendingUri = null
                        scope.launch {
                            if (!repo.addUploadedReport(patientId, title, uri)) snackbar.showSnackbar(uploadFail)
                        }
                    }
                ) { Text(stringResource(R.string.upload)) }
            },
            dismissButton = { TextButton(onClick = { pendingUri = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    toDelete?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.delete_report),
            message = stringResource(R.string.delete_report_msg, r.title),
            confirmText = stringResource(R.string.delete),
            onConfirm = { scope.launch { repo.deleteReport(r) }; toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}
