package com.medireport.portal.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.data.Patient
import com.medireport.portal.data.Report
import com.medireport.portal.data.Template
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.components.AppTopBar
import com.medireport.portal.ui.components.SectionTitle
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.util.Formatters
import com.medireport.portal.util.Intents
import com.medireport.portal.util.Printing
import com.medireport.portal.util.RangeCheck
import com.medireport.portal.util.ReportDsl
import com.medireport.portal.util.ReportItem
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
    var specimen by remember { mutableStateOf("") }
    var consultedBy by remember { mutableStateOf("") }
    var received by remember { mutableStateOf(Formatters.today()) }
    var printDate by remember { mutableStateOf(Formatters.today()) }
    var letterhead by remember { mutableStateOf(true) }
    var publish by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewing by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Report?>(null) }

    val patient: Patient? = patients.firstOrNull { it.id == patientId }
    val template: Template? = templates.firstOrNull { it.id == templateId }
    val items = remember(template?.id, template?.content) {
        template?.let { ReportDsl.parse(it.content) } ?: emptyList()
    }
    val values = remember(template?.id) {
        mutableStateMapOf<String, String>().apply {
            items.forEach { if (it is ReportItem.Test && it.default.isNotBlank()) put(it.key, it.default) }
        }
    }
    LaunchedEffect(template?.id) {
        template?.let { title = it.name; specimen = it.specimen; preview = null }
    }
    LaunchedEffect(patient?.id) {
        patient?.let { if (consultedBy.isBlank()) consultedBy = it.refBy }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(stringResource(R.string.generate_report), onBack = onBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SelectField(
                label = stringResource(R.string.select_patient),
                valueText = patient?.let { it.name + " (" + it.patientCode + ")" } ?: "",
                icon = Icons.Filled.Person,
                items = patients.map { it.id to (it.name + " · " + it.patientCode) },
                onSelect = { patientId = it; consultedBy = "" }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppField(specimen, { specimen = it }, stringResource(R.string.specimen), modifier = Modifier.weight(1f))
                    AppField(consultedBy, { consultedBy = it }, stringResource(R.string.consulted_by), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppField(received, { received = it }, stringResource(R.string.received_date), modifier = Modifier.weight(1f))
                    AppField(printDate, { printDate = it }, stringResource(R.string.print_date), modifier = Modifier.weight(1f))
                }

                SectionTitle(stringResource(R.string.enter_results))
                items.forEach { item ->
                    when (item) {
                        is ReportItem.Section -> Text(
                            item.name, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)
                        )
                        is ReportItem.Note -> Text(
                            item.text, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        is ReportItem.Field -> AppField(
                            values[item.key].orEmpty(), { values[item.key] = it; preview = null }, item.label,
                            singleLine = false, minLines = 3
                        )
                        is ReportItem.Test -> TestRow(
                            item = item,
                            value = values[item.key].orEmpty(),
                            sex = patient?.sex ?: "",
                            onChange = { values[item.key] = it; preview = null }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.use_letterhead), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.use_letterhead_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = letterhead, onCheckedChange = { letterhead = it; preview = null })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.publish_to_web), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.publish_to_web_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = publish, onCheckedChange = { publish = it })
                }

                preview?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            .background(Color.White)
                    )
                }

                if (failed) {
                    Text(stringResource(R.string.err_generate), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val pt = patient ?: return@OutlinedButton
                            previewing = true
                            scope.launch {
                                preview = repo.previewReport(pt, template, specimen, consultedBy, received, printDate, values.toMap(), letterhead)
                                previewing = false
                            }
                        },
                        enabled = patient != null && !previewing,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (previewing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Filled.Visibility, null); Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.preview))
                        }
                    }
                    Button(
                        onClick = {
                            val pt = patient ?: return@Button
                            generating = true; failed = false
                            scope.launch {
                                val r = repo.generateReport(
                                    pt, template, specimen, consultedBy, received, printDate, values.toMap(),
                                    title.trim().ifBlank { template.name }, letterhead, publish
                                )
                                generating = false
                                if (r != null) done = r else failed = true
                            }
                        },
                        enabled = patient != null && !generating,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (generating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
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
                        Printing.print(context, repo.store.reportFile(r.fileName), r.mimeType, r.title)
                    }) { Text(stringResource(R.string.print)) }
                    TextButton(onClick = {
                        Intents.shareFile(context, repo.store.reportFile(r.fileName), r.mimeType, r.title, repo.store)
                    }) { Text(stringResource(R.string.share)) }
                    TextButton(onClick = { done = null; onBack() }) { Text(stringResource(R.string.done)) }
                }
            }
        )
    }
}

/** One test line: name, result box, unit, reference range and an automatic H/L hint. */
@Composable
private fun TestRow(item: ReportItem.Test, value: String, sex: String, onChange: (String) -> Unit) {
    val flag = RangeCheck.flag(value, item.ref, sex)
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (item.ref.isNotBlank()) {
                Text(
                    item.ref.replace("\n", " · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            suffix = if (item.unit.isNotBlank()) ({ Text(item.unit, style = MaterialTheme.typography.bodySmall) }) else null,
            trailingIcon = flag?.let { f ->
                {
                    Text(
                        f, fontWeight = FontWeight.Bold,
                        color = if (f == "H") Color(0xFFDC2626) else Color(0xFF2563EB)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.width(170.dp)
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
