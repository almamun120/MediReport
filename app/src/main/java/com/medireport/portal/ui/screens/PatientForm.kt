package com.medireport.portal.ui.screens

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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.data.Patient
import com.medireport.portal.data.SaveResult
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.components.AppTopBar
import com.medireport.portal.ui.components.Chip
import com.medireport.portal.ui.rememberRepo
import kotlinx.coroutines.launch

@Composable
fun sexLabel(sex: String): String = when (sex) {
    "Male" -> stringResource(R.string.sex_male)
    "Female" -> stringResource(R.string.sex_female)
    "Other" -> stringResource(R.string.sex_other)
    else -> sex
}

@Composable
fun PatientFormScreen(patientId: Long, onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var existing by remember { mutableStateOf<Patient?>(null) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("Male") }
    var age by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var refBy by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(patientId) {
        if (patientId == 0L) {
            code = repo.nextPatientCode()
        } else {
            repo.getPatient(patientId)?.let {
                existing = it
                code = it.patientCode; name = it.name; sex = it.sex
                age = it.age.toString(); phone = it.phone; refBy = it.refBy
            }
        }
    }

    val ageInt = age.toIntOrNull()
    val ageValid = ageInt != null && ageInt in 0..150
    val phoneValid = phone.filter { it.isDigit() }.length >= 6

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                stringResource(if (patientId == 0L) R.string.add_patient else R.string.edit_patient),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppField(
                code, { code = it; duplicate = false }, stringResource(R.string.patient_id), leading = Icons.Filled.Badge,
                error = when {
                    duplicate -> stringResource(R.string.err_duplicate_id)
                    submitted && code.isBlank() -> stringResource(R.string.err_required)
                    else -> null
                }
            )
            AppField(
                name, { name = it }, stringResource(R.string.full_name), leading = Icons.Filled.Person,
                error = if (submitted && name.isBlank()) stringResource(R.string.err_required) else null
            )
            Text(stringResource(R.string.sex), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Male", "Female", "Other").forEach { s -> Chip(sexLabel(s), sex == s) { sex = s } }
            }
            AppField(
                age, { age = it.filter { c -> c.isDigit() }.take(3) }, stringResource(R.string.age),
                leading = Icons.Filled.Cake, keyboardType = KeyboardType.Number,
                error = if (submitted && !ageValid) stringResource(R.string.err_age) else null
            )
            AppField(
                phone, { phone = it }, stringResource(R.string.phone), leading = Icons.Filled.Phone,
                keyboardType = KeyboardType.Phone,
                error = if (submitted && !phoneValid) stringResource(R.string.err_phone) else null,
                supporting = stringResource(R.string.phone_login_hint)
            )
            AppField(refBy, { refBy = it }, stringResource(R.string.ref_by), leading = Icons.Filled.LocalHospital)

            if (failed) {
                Text(stringResource(R.string.err_save), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    submitted = true
                    if (code.isNotBlank() && name.isNotBlank() && ageValid && phoneValid) {
                        saving = true
                        scope.launch {
                            val p = Patient(
                                id = existing?.id ?: 0L,
                                patientCode = code.trim(),
                                name = name.trim(),
                                sex = sex,
                                age = ageInt ?: 0,
                                phone = phone.trim(),
                                refBy = refBy.trim(),
                                createdAt = existing?.createdAt ?: System.currentTimeMillis()
                            )
                            when (repo.savePatient(p)) {
                                SaveResult.OK -> onBack()
                                SaveResult.DUPLICATE_CODE -> duplicate = true
                                SaveResult.ERROR -> failed = true
                            }
                            saving = false
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
