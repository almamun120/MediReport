package com.medireport.portal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.medireport.portal.R
import com.medireport.portal.data.SmsConfig
import com.medireport.portal.data.SmsRequest
import com.medireport.portal.sms.SmsProcessor
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.rememberRepo
import kotlinx.coroutines.launch

/** Settings card: patients text "ID REPORT" and get an automatic reply about their published reports. */
@Composable
fun SmsCard(snackbar: SnackbarHostState) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by repo.prefs.sms.collectAsState(initial = SmsConfig())
    val pending by repo.smsDao.observePending().collectAsState(initial = 0)
    val recent by repo.smsDao.observeRecent(6).collectAsState(initial = emptyList())
    var baseUrl by remember(cfg.baseUrl) { mutableStateOf(cfg.baseUrl) }
    val denied = stringResource(R.string.sms_permission_denied)

    fun granted() = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (granted()) scope.launch { repo.prefs.saveSms(cfg.copy(enabled = true)); SmsProcessor.kick(context) }
        else scope.launch { snackbar.showSnackbar(denied) }
    }

    LaunchedEffect(Unit) { if (cfg.enabled && pending > 0) SmsProcessor.kick(context) }

    SettingsCard(Icons.Outlined.Sms, stringResource(R.string.sms_lookup)) {
        Text(
            stringResource(R.string.sms_lookup_desc),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sms_enable), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.sms_pending, pending),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = cfg.enabled,
                onCheckedChange = { on ->
                    if (!on) scope.launch { repo.prefs.saveSms(cfg.copy(enabled = false)) }
                    else if (granted()) scope.launch { repo.prefs.saveSms(cfg.copy(enabled = true)) }
                    else permissions.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS))
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.sms_format_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.sms_format_own), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.sms_format_other), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        AppField(
            baseUrl, { baseUrl = it }, stringResource(R.string.sms_base_url),
            keyboardType = KeyboardType.Uri, supporting = stringResource(R.string.sms_base_url_hint)
        )
        TextButton(onClick = {
            scope.launch { repo.prefs.saveSms(cfg.copy(baseUrl = baseUrl)); snackbar.showSnackbar(context.getString(R.string.saved)) }
        }) { Text(stringResource(R.string.save)) }
        Text(
            stringResource(R.string.sms_limit_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.sms_recent), style = MaterialTheme.typography.titleSmall)
            recent.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.sender + "  ·  " + r.body, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        stringResource(
                            when (r.status) {
                                SmsRequest.STATUS_PENDING -> R.string.sms_st_pending
                                SmsRequest.STATUS_DONE -> R.string.sms_st_done
                                SmsRequest.STATUS_IGNORED -> R.string.sms_st_ignored
                                else -> R.string.sms_st_failed
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (r.status == SmsRequest.STATUS_FAILED) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
