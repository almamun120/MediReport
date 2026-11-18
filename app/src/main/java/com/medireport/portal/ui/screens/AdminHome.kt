package com.medireport.portal.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.ui.DashboardViewModel
import com.medireport.portal.ui.components.AppTopBar
import com.medireport.portal.ui.components.clickableNoRipple
import com.medireport.portal.ui.components.EmptyState
import com.medireport.portal.ui.components.MiniBarChart
import com.medireport.portal.ui.components.ReportCard
import com.medireport.portal.ui.components.SectionTitle
import com.medireport.portal.ui.components.StatCard
import com.medireport.portal.ui.repoViewModel
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.ui.theme.Amber500
import com.medireport.portal.ui.theme.Emerald500
import com.medireport.portal.ui.theme.Indigo600
import com.medireport.portal.ui.theme.Sky500
import com.medireport.portal.ui.theme.heroBrush
import com.medireport.portal.ui.theme.isAppDark
import com.medireport.portal.util.Formatters
import com.medireport.portal.util.Intents

private data class Tab(val titleRes: Int, val selected: ImageVector, val unselected: ImageVector)

@Composable
fun AdminHomeScreen(
    onOpenPatient: (Long) -> Unit,
    onAddPatient: () -> Unit,
    onEditPatient: (Long) -> Unit,
    onNewReport: (Long) -> Unit,
    onEditTemplate: (Long) -> Unit,
    onPageSetup: () -> Unit,
    onMailSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val tabs = listOf(
        Tab(R.string.tab_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        Tab(R.string.tab_patients, Icons.Filled.PeopleAlt, Icons.Outlined.PeopleAlt),
        Tab(R.string.tab_templates, Icons.Filled.Description, Icons.Outlined.Description),
        Tab(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (tab != 0) AppTopBar(stringResource(tabs[tab].titleRes))
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(if (tab == i) t.selected else t.unselected, null) },
                        label = { Text(stringResource(t.titleRes)) }
                    )
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                1 -> ExtendedFloatingActionButton(
                    onClick = onAddPatient,
                    icon = { Icon(Icons.Filled.PersonAdd, null) },
                    text = { Text(stringResource(R.string.add_patient)) }
                )
                2 -> ExtendedFloatingActionButton(
                    onClick = { onEditTemplate(0L) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(stringResource(R.string.add_template)) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> DashboardTab(
                    onAddPatient = onAddPatient,
                    onNewReport = { onNewReport(0L) },
                    onOpenSettings = { tab = 3 },
                    onOpenPatients = { tab = 1 }
                )
                1 -> PatientsTab(onOpenPatient, onEditPatient)
                2 -> TemplatesTab(onEditTemplate)
                else -> SettingsTab(snackbar, onPageSetup, onMailSettings, onLogout)
            }
        }
    }
}

@Composable
private fun DashboardTab(
    onAddPatient: () -> Unit,
    onNewReport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPatients: () -> Unit
) {
    val vm: DashboardViewModel = repoViewModel()
    val repo = rememberRepo()
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val defaultPw by vm.usesDefaultPassword.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(heroBrush(isAppDark())).padding(20.dp)
            ) {
                Column {
                    Text(stringResource(R.string.greeting_admin), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(Formatters.date(System.currentTimeMillis()), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction(Icons.Filled.PersonAdd, stringResource(R.string.add_patient), onAddPatient, Modifier.weight(1f))
                        QuickAction(Icons.Filled.NoteAdd, stringResource(R.string.new_report), onNewReport, Modifier.weight(1f))
                    }
                }
            }
        }
        if (defaultPw) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Amber500.copy(alpha = 0.16f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Amber500)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.warn_default_pw), style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.change)) }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Icons.Outlined.Assignment, stringResource(R.string.stat_patients), state.patients.toString(), Sky500, Modifier.weight(1f))
                    StatCard(Icons.Outlined.FolderOpen, stringResource(R.string.stat_reports), state.reports.toString(), Emerald500, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Icons.Outlined.Description, stringResource(R.string.stat_templates), state.templates.toString(), Indigo600, Modifier.weight(1f))
                    StatCard(Icons.Filled.NoteAdd, stringResource(R.string.stat_week), state.chart.sumOf { it.second }.toString(), Amber500, Modifier.weight(1f))
                }
            }
        }
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.chart_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    MiniBarChart(state.chart)
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.recent_reports)) {
                TextButton(onClick = onOpenPatients) { Text(stringResource(R.string.see_patients)) }
            }
        }
        if (state.recent.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.FolderOpen, stringResource(R.string.no_reports), stringResource(R.string.no_reports_admin_hint))
            }
        } else {
            items(state.recent, key = { it.report.id }) { row ->
                val r = row.report
                ReportCard(
                    report = r,
                    subtitle = row.patientName + " · " + Formatters.dateTime(r.createdAt),
                    onOpen = { Intents.openFile(context, repo.store.reportFile(r.fileName), r.mimeType, repo.store) },
                    onShare = { Intents.shareFile(context, repo.store.reportFile(r.fileName), r.mimeType, r.title, repo.store) }
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Color.White.copy(alpha = 0.18f))
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
