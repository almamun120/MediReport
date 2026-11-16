package com.medireport.portal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.ui.components.Avatar
import com.medireport.portal.ui.components.EmptyState
import com.medireport.portal.ui.components.InfoRow
import com.medireport.portal.ui.components.ReportCard
import com.medireport.portal.ui.components.SectionTitle
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.ui.theme.heroBrush
import com.medireport.portal.ui.theme.isAppDark
import com.medireport.portal.util.Intents

@Composable
fun PatientHomeScreen(patientId: Long, onLogout: () -> Unit) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val patient by repo.observePatient(patientId).collectAsState(initial = null)
    val reports by repo.observeReports(patientId).collectAsState(initial = emptyList())
    val p = patient

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().background(heroBrush(isAppDark())).statusBarsPadding()) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(p?.name ?: "?", 56)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.welcome_back), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                    Text(p?.name ?: "", color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.Outlined.Logout, stringResource(R.string.logout), tint = Color.White)
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (p != null) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(stringResource(R.string.my_information), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            InfoRow(stringResource(R.string.patient_id), p.patientCode)
                            InfoRow(stringResource(R.string.sex), sexLabel(p.sex))
                            InfoRow(stringResource(R.string.age), p.age.toString())
                            InfoRow(stringResource(R.string.phone), p.phone)
                            InfoRow(stringResource(R.string.ref_by), p.refBy.ifBlank { "-" })
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.my_reports) + " (" + reports.size + ")") }
            if (reports.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.FolderOpen, stringResource(R.string.no_reports), stringResource(R.string.no_reports_patient))
                }
            } else {
                items(reports, key = { it.id }) { r ->
                    ReportCard(
                        report = r,
                        onOpen = { Intents.openFile(context, repo.store.reportFile(r.fileName), r.mimeType, repo.store) },
                        onShare = { Intents.shareFile(context, repo.store.reportFile(r.fileName), r.mimeType, r.title, repo.store) }
                    )
                }
            }
        }
    }
}
