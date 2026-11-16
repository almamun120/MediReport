package com.medireport.portal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.data.Template
import com.medireport.portal.ui.components.ConfirmDialog
import com.medireport.portal.ui.components.EmptyState
import com.medireport.portal.ui.rememberRepo
import kotlinx.coroutines.launch

@Composable
fun TemplatesTab(onEdit: (Long) -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val list by repo.templates.collectAsState(initial = emptyList())
    var toDelete by remember { mutableStateOf<Template?>(null) }

    if (list.isEmpty()) {
        EmptyState(Icons.Filled.Description, stringResource(R.string.no_templates), stringResource(R.string.no_templates_hint))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list, key = { it.id }) { t ->
                var menu by remember { mutableStateOf(false) }
                val params = t.parameters.split(",").filter { it.isNotBlank() }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onEdit(t.id) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.secondary) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.fields_count, params.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; onEdit(t.id) })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.duplicate)) },
                                    onClick = { menu = false; scope.launch { repo.duplicateTemplate(t) } }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; toDelete = t }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { t ->
        ConfirmDialog(
            title = stringResource(R.string.delete_template),
            message = stringResource(R.string.delete_template_msg, t.name),
            confirmText = stringResource(R.string.delete),
            onConfirm = { scope.launch { repo.deleteTemplate(t) }; toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}
