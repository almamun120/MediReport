package com.medireport.portal.ui.screens

import android.webkit.WebView
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.medireport.portal.R
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.components.AppTopBar
import com.medireport.portal.ui.components.Chip
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.util.TemplateEngine
import kotlinx.coroutines.launch

@Composable
fun TemplateEditorScreen(templateId: Long, onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf(TextFieldValue("")) }
    var submitted by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }

    LaunchedEffect(templateId) {
        if (templateId != 0L) {
            repo.getTemplate(templateId)?.let {
                name = it.name
                content = TextFieldValue(it.content)
            }
        } else if (content.text.isEmpty()) {
            content = TextFieldValue(
                "<h2 style=\"text-align:center;\">Report Title</h2>\n" +
                    "<p><strong>Patient Name:</strong> {{patient_name}}</p>\n" +
                    "<p><strong>Patient ID:</strong> {{patient_id}} &nbsp; <strong>Age/Sex:</strong> {{age}}/{{sex}}</p>\n" +
                    "<p><strong>Date:</strong> {{date}}</p>\n<hr>\n<p>{{report_content}}</p>"
            )
        }
    }

    fun insert(token: String) {
        val sel = content.selection
        val start = minOf(sel.start, sel.end).coerceIn(0, content.text.length)
        val end = maxOf(sel.start, sel.end).coerceIn(0, content.text.length)
        val newText = content.text.replaceRange(start, end, token)
        content = TextFieldValue(newText, TextRange(start + token.length))
    }

    val detected = TemplateEngine.detectParams(content.text)

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

            Text(stringResource(R.string.insert_placeholder), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (TemplateEngine.BUILT_IN.toList() + "date").forEach { key ->
                    Chip("{{$key}}", false) { insert("{{$key}}") }
                }
            }
            Text(stringResource(R.string.insert_snippet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.snip_heading), false) { insert("<h3>Heading</h3>\n") }
                Chip(stringResource(R.string.snip_line), false) { insert("<hr>\n") }
                Chip(stringResource(R.string.snip_table), false) {
                    insert("<table>\n  <tr><th>Test</th><th>Result</th><th>Normal Range</th></tr>\n  <tr><td>Name</td><td>{{result}}</td><td>-</td></tr>\n</table>\n")
                }
                Chip(stringResource(R.string.snip_para), false) { insert("<p>{{notes}}</p>\n") }
            }

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.template_html)) },
                minLines = 10,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                shape = MaterialTheme.shapes.medium,
                isError = submitted && content.text.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )

            if (detected.isNotEmpty()) {
                Text(stringResource(R.string.detected_fields), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    detected.forEach { Chip(TemplateEngine.humanize(it), true) {} }
                }
            }

            if (preview) {
                val html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'>" +
                    "<style>body{font-family:sans-serif;font-size:14px;padding:8px}table{border-collapse:collapse;width:100%}" +
                    "th,td{border:1px solid #999;padding:5px 7px;text-align:left}</style></head><body>" +
                    content.text + "</body></html>"
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(360.dp),
                        factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = false } },
                        update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { preview = !preview },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(if (preview) R.string.hide_preview else R.string.preview)) }
                Button(
                    onClick = {
                        submitted = true
                        if (name.isNotBlank() && content.text.isNotBlank()) {
                            scope.launch {
                                repo.saveTemplate(templateId, name, content.text)
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.save)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
