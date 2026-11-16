package com.medireport.portal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.data.Prefs
import com.medireport.portal.ui.AuthResult
import com.medireport.portal.ui.AuthViewModel
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.components.PasswordField
import com.medireport.portal.ui.repoViewModel
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.ui.theme.heroBrush
import com.medireport.portal.ui.theme.isAppDark
import kotlinx.coroutines.launch

@Composable
private fun LoginScaffold(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.fillMaxWidth().background(heroBrush(isAppDark())).statusBarsPadding()) {
            Column(Modifier.padding(start = 8.dp, end = 24.dp, bottom = 56.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, Modifier.size(30.dp), tint = Color.White) }
                    Spacer(Modifier.height(14.dp))
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
        Card(
            Modifier.padding(horizontal = 20.dp).padding(top = 0.dp).navigationBarsPadding(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(Modifier.padding(20.dp)) { content() }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun errorText(r: AuthResult, invalidRes: Int): String? = when (r) {
    is AuthResult.Invalid -> stringResource(invalidRes)
    is AuthResult.Locked -> stringResource(R.string.err_locked, r.seconds)
    AuthResult.Success -> null
}

@Composable
fun AdminLoginScreen(onSuccess: () -> Unit, onBack: () -> Unit) {
    val vm: AuthViewModel = repoViewModel()
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val isDefault by repo.prefs.adminUsesDefaultPassword.collectAsState(initial = false)
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AuthResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    LoginScaffold(
        Icons.Filled.AdminPanelSettings,
        stringResource(R.string.admin_login),
        stringResource(R.string.admin_login_sub),
        onBack
    ) {
        AppField(user, { user = it; result = null }, stringResource(R.string.username), leading = Icons.Filled.Person)
        Spacer(Modifier.height(10.dp))
        PasswordField(pass, { pass = it; result = null }, stringResource(R.string.password), Icons.Filled.Lock)
        result?.let { r ->
            errorText(r, R.string.err_admin_invalid)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (isDefault) {
            Text(
                stringResource(R.string.default_cred_hint, Prefs.DEFAULT_ADMIN_USER, Prefs.DEFAULT_ADMIN_PASSWORD),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                loading = true
                scope.launch {
                    val r = vm.adminLogin(user, pass)
                    loading = false
                    if (r == AuthResult.Success) onSuccess() else result = r
                }
            },
            enabled = !loading && user.isNotBlank() && pass.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else Text(stringResource(R.string.login))
        }
    }
}

@Composable
fun PatientLoginScreen(onSuccess: () -> Unit, onBack: () -> Unit) {
    val vm: AuthViewModel = repoViewModel()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AuthResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    LoginScaffold(
        Icons.Filled.Person,
        stringResource(R.string.patient_login),
        stringResource(R.string.patient_login_sub),
        onBack
    ) {
        AppField(code, { code = it; result = null }, stringResource(R.string.patient_id), leading = Icons.Filled.Badge)
        Spacer(Modifier.height(10.dp))
        AppField(
            phone, { phone = it; result = null }, stringResource(R.string.phone_password),
            leading = Icons.Filled.Phone, keyboardType = KeyboardType.Phone
        )
        result?.let { r ->
            errorText(r, R.string.err_patient_invalid)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                loading = true
                scope.launch {
                    val r = vm.patientLogin(code, phone)
                    loading = false
                    if (r == AuthResult.Success) onSuccess() else result = r
                }
            },
            enabled = !loading && code.isNotBlank() && phone.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else Text(stringResource(R.string.login))
        }
    }
}
