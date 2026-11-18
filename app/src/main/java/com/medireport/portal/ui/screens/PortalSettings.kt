package com.medireport.portal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.medireport.portal.R
import com.medireport.portal.data.PortalConfig
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.web.PortalService
import kotlinx.coroutines.launch

/** Settings card: patients log in on a web page served by this phone (ID + phone number). */
@Composable
fun PortalCard(snackbar: SnackbarHostState) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val state by PortalService.state.collectAsState()
    val cfg by repo.prefs.portal.collectAsState(initial = PortalConfig())
    var portText by remember(cfg.port) { mutableStateOf(cfg.port.toString()) }
    var portError by remember { mutableStateOf(false) }
    val copied = stringResource(R.string.copied)

    fun begin() {
        val port = portText.toIntOrNull()
        if (port == null || port !in 1024..65535) { portError = true; return }
        portError = false
        scope.launch {
            repo.prefs.savePortal(cfg.copy(port = port))
            PortalService.start(context)
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { begin() }

    SettingsCard(Icons.Outlined.Public, stringResource(R.string.patient_portal)) {
        Text(
            stringResource(R.string.patient_portal_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        AppField(
            portText, { portText = it.filter(Char::isDigit).take(5); portError = false },
            stringResource(R.string.portal_port), keyboardType = KeyboardType.Number,
            error = if (portError) stringResource(R.string.err_port) else null,
            supporting = stringResource(R.string.portal_port_hint),
            enabled = !state.running
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.portal_running), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(if (state.running) R.string.portal_on else R.string.portal_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.running) androidx.compose.ui.graphics.Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.running,
                onCheckedChange = { on ->
                    if (!on) PortalService.stop(context)
                    else if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else begin()
                }
            )
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.portal_autostart), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.portal_autostart_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = cfg.autoStart, onCheckedChange = { v -> scope.launch { repo.prefs.savePortal(cfg.copy(autoStart = v)) } })
        }

        if (state.running) {
            val addresses = remember(state.running) { PortalService.localAddresses() }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.portal_addresses), style = MaterialTheme.typography.titleSmall)
            if (addresses.isEmpty()) {
                Text(stringResource(R.string.portal_no_network), style = MaterialTheme.typography.bodySmall)
            }
            addresses.forEach { ip ->
                val url = "http://$ip:${state.port}"
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(url, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(url))
                        scope.launch { snackbar.showSnackbar(copied) }
                    }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) }
                }
            }
            Text(
                stringResource(R.string.portal_internet_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
