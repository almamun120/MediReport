package com.medireport.portal.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.medireport.portal.R
import com.medireport.portal.data.Patient
import com.medireport.portal.data.Report
import com.medireport.portal.data.Template
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.components.AppTopBar
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.util.Formatters
import com.medireport.portal.util.Intents
import com.medireport.portal.util.TemplateEngine
import kotlinx.coroutines.launch

@Composable
fun ReportBuilderScreen(initialPatientId: Long, onBack: () -> Unit) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val patients by repo.patients.collectAsState(initial = emptyList())
    val templates by repo.templates.collectAsState(initial = emptyList())

    var patientId by remember { mutableStateOf(initialPatientId) }
    var templateId by remember { mutableStateOf(0L) }
    var title by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Report?>(null) }

    val patient: Patient? = patients.firstOrNull { it.id == patientId }
    val template: Template? = templates.firstOrNull { it.id == templateId }
    val params = remember(template?.id, template?.content) {
        template?.let { TemplateEngine.detectParams(it.content) } ?: emptyList()
    }
    val values = remember(template?.id) {
        mutableStateMapOf<String, String>().apply {
            params.forEach { put(it, if (it == "date") Formatters.today() else "") }
        }
    }
    LaunchedEffect(template?.id) {
        template?.let { title = it.name }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(stringResource(R.string.generate_report), onBack = onBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SelectField(
                label = stringResource(R.string.select_patient),
                valueText = patient?.let { it.name + " (" + it.patientCode + ")" } ?: "",
                icon = Icons.Filled.Person,
                items = patients.map { it.id to (it.name + " · " + it.patientCode) },
                onSelect = { patientId = it }
            )
            SelectField(
                label = stringResource(R.string.select_template),
                valueText = template?.name ?: "",
                icon = Icons.Filled.Description,
                items = templates.map { it.id to it.name },
                onSelect = { templateId = it }
            )

            if (template != null) {
                AppField(title, { title = it }, stringResource(R.string.report_name), leading = Icons.Filled.Title)

                if (params.isNotEmpty()) {
                    Text(stringResource(R.string.fill_details), style = MaterialTheme.typography.titleMedium)
                }
                params.forEach { key ->
                    val multi = TemplateEngine.isMultiline(key)
                    AppField(
                        value = values[key].orEmpty(),
                        onValueChange = { values[key] = it },
                        label = TemplateEngine.humanize(key),
                        singleLine = !multi,
                        minLines = if (multi) 3 else 1
                    )
                }

                if (showPreview && patient != null) {
                    val html = remember(values.toMap(), patient, template) {
                        "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'>" +
                            "<style>body{font-family:sans-serif;font-size:14px;padding:8px}" +
                            "table{border-collapse:collapse;width:100%}th,td{border:1px solid #999;padding:5px 7px;text-align:left}" +
                            "</style></head><body>" + TemplateEngine.render(template.content, patient, values.toMap()) +
                            "</body></html>"
                    }
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(380.dp),
                            factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = false } },
                            update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) }
                        )
                    }
                }

                if (failed) {
                    Text(stringResource(R.string.err_generate), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showPreview = !showPreview },
                        enabled = patient != null,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.Visibility, null); Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (showPreview) R.string.hide_preview else R.string.preview))
                    }
                    Button(
                        onClick = {
                            val pt = patient ?: return@Button
                            generating = true; failed = false
                            scope.launch {
                                val r = repo.generateReport(pt, template, values.toMap(), title.trim().ifBlank { template.name })
                                generating = false
                                if (r != null) done = r else failed = true
                            }
                        },
                        enabled = patient != null && !generating,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (generating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                        else {
                            Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.create_pdf))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    done?.let { r ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.report_ready)) },
            text = { Text(stringResource(R.string.report_ready_msg, r.title)) },
            confirmButton = {
                TextButton(onClick = {
                    Intents.openFile(context, repo.store.reportFile(r.fileName), r.mimeType, repo.store)
                }) { Text(stringResource(R.string.view)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        Intents.shareFile(context, repo.store.reportFile(r.fileName), r.mimeType, r.title, repo.store)
                    }) { Text(stringResource(R.string.share)) }
                    TextButton(onClick = { done = null; onBack() }) { Text(stringResource(R.string.done)) }
                }
            }
        )
    }
}

@Composable
private fun SelectField(
    label: String,
    valueText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valueText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(icon, null) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (id, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}
