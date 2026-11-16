package com.medireport.portal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.data.Patient
import com.medireport.portal.ui.PatientSort
import com.medireport.portal.ui.PatientsViewModel
import com.medireport.portal.ui.components.Avatar
import com.medireport.portal.ui.components.Chip
import com.medireport.portal.ui.components.ConfirmDialog
import com.medireport.portal.ui.components.EmptyState
import com.medireport.portal.ui.repoViewModel

@Composable
fun PatientsTab(onOpen: (Long) -> Unit, onEdit: (Long) -> Unit) {
    val vm: PatientsViewModel = repoViewModel()
    val list by vm.list.collectAsState()
    val query by vm.query.collectAsState()
    val sort by vm.sort.collectAsState()
    var toDelete by remember { mutableStateOf<Patient?>(null) }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Clear, null) }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Chip(stringResource(R.string.sort_newest), sort == PatientSort.NEWEST) { vm.sort.value = PatientSort.NEWEST }
            Chip(stringResource(R.string.sort_name), sort == PatientSort.NAME) { vm.sort.value = PatientSort.NAME }
            Chip(stringResource(R.string.sort_id), sort == PatientSort.ID) { vm.sort.value = PatientSort.ID }
        }

        if (list.isEmpty()) {
            EmptyState(
                Icons.Filled.PeopleAlt,
                stringResource(if (query.isBlank()) R.string.no_patients else R.string.no_results),
                stringResource(if (query.isBlank()) R.string.no_patients_hint else R.string.no_results_hint)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.id }) { p ->
                    PatientRow(p, onClick = { onOpen(p.id) }, onEdit = { onEdit(p.id) }, onDelete = { toDelete = p })
                }
            }
        }
    }

    toDelete?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.delete_patient),
            message = stringResource(R.string.delete_patient_msg, p.name),
            confirmText = stringResource(R.string.delete),
            onConfirm = { vm.delete(p); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun PatientRow(p: Patient, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    p.patientCode + " · " + p.age + " / " + p.sex,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(p.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}
