package com.medireport.portal.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medireport.portal.R
import com.medireport.portal.data.PageSetup
import com.medireport.portal.ui.components.AppField
import com.medireport.portal.ui.components.AppTopBar
import com.medireport.portal.ui.rememberRepo
import com.medireport.portal.util.Intents
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Slot { HEADER, FOOTER, BACKGROUND }

@Composable
fun PageSetupScreen(onBack: () -> Unit) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var loaded by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(PageSetup()) }
    var busy by remember { mutableStateOf(false) }
    val savedMsg = stringResource(R.string.saved)
    val previewFail = stringResource(R.string.err_generate)

    LaunchedEffect(Unit) {
        setup = repo.getPageSetup()
        loaded = true
    }

    fun setImage(slot: Slot, uri: Uri?) {
        scope.launch {
            val old = when (slot) {
                Slot.HEADER -> setup.headerImage
                Slot.FOOTER -> setup.footerImage
                Slot.BACKGROUND -> setup.backgroundImage
            }
            val newName = if (uri != null) repo.importImage(uri) else null
            if (uri != null && newName == null) return@launch
            repo.store.deleteImage(old)
            setup = when (slot) {
                Slot.HEADER -> setup.copy(headerImage = newName)
                Slot.FOOTER -> setup.copy(footerImage = newName)
                Slot.BACKGROUND -> setup.copy(backgroundImage = newName)
            }
            repo.savePageSetup(setup)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(stringResource(R.string.page_setup), onBack = onBack) }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.page_setup_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SetupCard(stringResource(R.string.header)) {
                AppField(
                    setup.headerHtml, { setup = setup.copy(headerHtml = it) }, stringResource(R.string.header_html),
                    singleLine = false, minLines = 3
                )
                ImagePicker(stringResource(R.string.header_image), setup.headerImage) { setImage(Slot.HEADER, it) }
                Text(stringResource(R.string.header_height, setup.headerHeightMm), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = setup.headerHeightMm.toFloat(), onValueChange = { setup = setup.copy(headerHeightMm = it.roundToInt()) },
                    valueRange = 0f..80f
                )
            }

            SetupCard(stringResource(R.string.footer)) {
                AppField(
                    setup.footerHtml, { setup = setup.copy(footerHtml = it) }, stringResource(R.string.footer_html),
                    singleLine = false, minLines = 3
                )
                ImagePicker(stringResource(R.string.footer_image), setup.footerImage) { setImage(Slot.FOOTER, it) }
                Text(stringResource(R.string.footer_height, setup.footerHeightMm), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = setup.footerHeightMm.toFloat(), onValueChange = { setup = setup.copy(footerHeightMm = it.roundToInt()) },
                    valueRange = 0f..60f
                )
            }

            SetupCard(stringResource(R.string.watermark)) {
                ImagePicker(stringResource(R.string.background_image), setup.backgroundImage) { setImage(Slot.BACKGROUND, it) }
                Text(stringResource(R.string.opacity, (setup.backgroundOpacity * 100).roundToInt()), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = setup.backgroundOpacity, onValueChange = { setup = setup.copy(backgroundOpacity = it) },
                    valueRange = 0.02f..1f
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            val f = repo.generateSample(setup)
                            busy = false
                            if (f != null) Intents.openFile(context, f, "application/pdf", repo.store)
                            else snackbar.showSnackbar(previewFail)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.preview_pdf)) }
                }
                Button(
                    onClick = {
                        scope.launch {
                            repo.savePageSetup(setup)
                            snackbar.showSnackbar(savedMsg)
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

@Composable
private fun SetupCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ImagePicker(label: String, imageName: String?, onPick: (Uri?) -> Unit) {
    val repo = rememberRepo()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPick(uri)
    }
    val bitmap = remember(imageName) {
        imageName?.let { BitmapFactory.decodeFile(repo.store.imageFile(it).path)?.asImageBitmap() }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(72.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Icon(Icons.Filled.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { launcher.launch("image/*") }) {
                Text(stringResource(if (imageName == null) R.string.choose_image else R.string.change_image))
            }
        }
        if (imageName != null) {
            IconButton(onClick = { onPick(null) }) {
                Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
