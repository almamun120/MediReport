package com.medireport.portal.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medireport.portal.R
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.components.AppTopBar
import com.medireport.portal.ui.components.Chip
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.util.ReportDsl
import com.medireport.portal.util.ReportItem
import kotlinx.coroutines.launch

/**
 * Templates are written in a tiny line-based language (see [ReportDsl]):
 *   # Section | Test | unit | reference | default | @ Free text field | > Fixed note
 */
@Composable
fun TemplateEditorScreen(templateId: Long, onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var specimen by remember { mutableStateOf("") }
    var content by remember { mutableStateOf(TextFieldValue("")) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(templateId) {
        if (templateId != 0L) {
            repo.getTemplate(templateId)?.let {
                name = it.name; title = it.title; specimen = it.specimen
                content = TextFieldValue(it.content)
            }
        } else if (content.text.isEmpty()) {
            content = TextFieldValue("# Section\nTest name | unit | 10 - 20 unit | \n@ Findings\n> Note printed as it is")
        }
    }

    fun insertLine(token: String) {
        val sel = content.selection
        val start = minOf(sel.start, sel.end).coerceIn(0, content.text.length)
        val end = maxOf(sel.start, sel.end).coerceIn(0, content.text.length)
        val before = content.text.substring(0, start)
        val prefix = if (before.isEmpty() || before.endsWith("\n")) "" else "\n"
        val ins = prefix + token + "\n"
        content = TextFieldValue(content.text.replaceRange(start, end, ins), TextRange(start + ins.length))
    }

    val items = ReportDsl.parse(content.text)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                stringResource(if (templateId == 0L) R.string.add_template else R.string.edit_template),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppField(
                name, { name = it }, stringResource(R.string.template_name), leading = Icons.Filled.Description,
                error = if (submitted && name.isBlank()) stringResource(R.string.err_required) else null
            )
            AppField(
                title, { title = it }, stringResource(R.string.report_heading), leading = Icons.Filled.Title,
                supporting = stringResource(R.string.report_heading_hint)
            )
            AppField(specimen, { specimen = it }, stringResource(R.string.specimen))

            Text(stringResource(R.string.insert_line), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.snip_section), false) { insertLine("# Section name") }
                Chip(stringResource(R.string.snip_test), false) { insertLine("Test name | unit | 10 - 20 unit | ") }
                Chip(stringResource(R.string.snip_field), false) { insertLine("@ Findings") }
                Chip(stringResource(R.string.snip_note), false) { insertLine("> Note printed as it is") }
            }

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.template_lines)) },
                minLines = 12,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                shape = MaterialTheme.shapes.medium,
                isError = submitted && content.text.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.template_syntax_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // live preview of what the parser understood
            if (items.isNotEmpty()) {
                Text(stringResource(R.string.template_preview), style = MaterialTheme.typography.titleMedium)
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items.forEach { item ->
                            when (item) {
                                is ReportItem.Section -> Text(
                                    item.name, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                                is ReportItem.Test -> Row(Modifier.fillMaxWidth()) {
                                    Text(item.name, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
                                    Text(item.unit, Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(item.ref.replace("\n", " · "), Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                is ReportItem.Field -> Text("▭ " + item.label, style = MaterialTheme.typography.bodyMedium)
                                is ReportItem.Note -> Text(item.text, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    submitted = true
                    if (name.isNotBlank() && content.text.isNotBlank()) {
                        scope.launch {
                            repo.saveTemplate(templateId, name.trim(), title.trim(), specimen.trim(), content.text)
                            onBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text(stringResource(R.string.save)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
