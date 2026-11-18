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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import androidx.compose.foundation.border
import com.medireport.portal.util.Printing
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

private class ImgSlot(
    val get: (PageSetup) -> String?,
    val set: (PageSetup, String?) -> PageSetup
)

private val HEADER = ImgSlot({ it.headerImage }, { s, v -> s.copy(headerImage = v) })
private val FOOTER = ImgSlot({ it.footerImage }, { s, v -> s.copy(footerImage = v) })
private val BACKGROUND = ImgSlot({ it.backgroundImage }, { s, v -> s.copy(backgroundImage = v) })
private val WATERMARK = ImgSlot({ it.watermarkImage }, { s, v -> s.copy(watermarkImage = v) })
private val SIG1 = ImgSlot({ it.sig1Image }, { s, v -> s.copy(sig1Image = v) })
private val SIG2 = ImgSlot({ it.sig2Image }, { s, v -> s.copy(sig2Image = v) })
private val SIG3 = ImgSlot({ it.sig3Image }, { s, v -> s.copy(sig3Image = v) })

@Composable
fun PageSetupScreen(onBack: () -> Unit) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var loaded by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(PageSetup()) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val savedMsg = stringResource(R.string.saved)
    val previewFail = stringResource(R.string.err_generate)
    val printFail = stringResource(R.string.print_failed)

    LaunchedEffect(Unit) {
        setup = repo.getPageSetup()
        loaded = true
        preview = repo.samplePreview(setup, setup.letterhead)
    }

    fun refreshPreview() {
        busy = true
        scope.launch {
            preview = repo.samplePreview(setup, setup.letterhead)
            busy = false
            if (preview == null) snackbar.showSnackbar(previewFail)
        }
    }

    fun setImage(slot: ImgSlot, uri: Uri?) {
        scope.launch {
            val old = slot.get(setup)
            val newName = if (uri != null) repo.importImage(uri) else null
            if (uri != null && newName == null) return@launch
            repo.store.deleteImage(old)
            setup = slot.set(setup, newName)
            repo.savePageSetup(setup)
            refreshPreview()
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

            SetupCard(stringResource(R.string.clinic_info)) {
                AppField(setup.clinicName, { setup = setup.copy(clinicName = it) }, stringResource(R.string.clinic_name))
                ToggleRow(stringResource(R.string.show_barcode), stringResource(R.string.show_barcode_hint), setup.showBarcode) {
                    setup = setup.copy(showBarcode = it)
                }
                ToggleRow(stringResource(R.string.letterhead_default), stringResource(R.string.letterhead_default_hint), setup.letterhead) {
                    setup = setup.copy(letterhead = it)
                }
            }

            SetupCard(stringResource(R.string.page_layout)) {
                MmSlider(R.string.top_space, setup.topMm, 0f..90f) { setup = setup.copy(topMm = it) }
                MmSlider(R.string.bottom_space, setup.bottomMm, 0f..70f) { setup = setup.copy(bottomMm = it) }
                MmSlider(R.string.side_margin, setup.marginMm, 5f..30f) { setup = setup.copy(marginMm = it) }
                Text(stringResource(R.string.font_size, setup.fontSize), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = setup.fontSize, onValueChange = { setup = setup.copy(fontSize = (it * 2).roundToInt() / 2f) },
                    valueRange = 7f..13f
                )
                Text(stringResource(R.string.page_layout_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SetupCard(stringResource(R.string.header)) {
                ImagePicker(stringResource(R.string.header_image), setup.headerImage) { setImage(HEADER, it) }
                AppField(
                    setup.headerHtml, { setup = setup.copy(headerHtml = it) }, stringResource(R.string.header_text),
                    singleLine = false, minLines = 2, supporting = stringResource(R.string.html_hint)
                )
            }

            SetupCard(stringResource(R.string.footer)) {
                ImagePicker(stringResource(R.string.footer_image), setup.footerImage) { setImage(FOOTER, it) }
                AppField(
                    setup.footerHtml, { setup = setup.copy(footerHtml = it) }, stringResource(R.string.footer_text),
                    singleLine = false, minLines = 2, supporting = stringResource(R.string.html_hint)
                )
            }

            SetupCard(stringResource(R.string.background)) {
                ImagePicker(stringResource(R.string.background_image), setup.backgroundImage) { setImage(BACKGROUND, it) }
                Text(stringResource(R.string.opacity, (setup.backgroundOpacity * 100).roundToInt()), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = setup.backgroundOpacity, onValueChange = { setup = setup.copy(backgroundOpacity = it) },
                    valueRange = 0.02f..1f
                )
            }

            SetupCard(stringResource(R.string.watermark)) {
                ImagePicker(stringResource(R.string.watermark_image), setup.watermarkImage) { setImage(WATERMARK, it) }
                Text(stringResource(R.string.opacity, (setup.watermarkOpacity * 100).roundToInt()), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = setup.watermarkOpacity, onValueChange = { setup = setup.copy(watermarkOpacity = it) },
                    valueRange = 0.02f..0.6f
                )
                MmSlider(R.string.watermark_size, setup.watermarkSizeMm, 40f..190f) { setup = setup.copy(watermarkSizeMm = it) }
            }

            SetupCard(stringResource(R.string.signatures)) {
                Text(stringResource(R.string.signatures_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SigBlock(1, setup.sig1Html, setup.sig1Image, { setup = setup.copy(sig1Html = it) }) { setImage(SIG1, it) }
                SigBlock(2, setup.sig2Html, setup.sig2Image, { setup = setup.copy(sig2Html = it) }) { setImage(SIG2, it) }
                SigBlock(3, setup.sig3Html, setup.sig3Image, { setup = setup.copy(sig3Html = it) }) { setImage(SIG3, it) }
            }

            SetupCard(stringResource(R.string.live_preview)) {
                val bmp = preview
                if (bmp != null) {
                    Image(
                        bmp.asImageBitmap(), null, Modifier.fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant).background(Color.White),
                        contentScale = ContentScale.FillWidth
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { refreshPreview() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.refresh_preview)) }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val f = repo.samplePdf(setup, setup.letterhead)
                                if (f == null || !Printing.print(context, f, "application/pdf", "Sample report")) {
                                    snackbar.showSnackbar(printFail)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Filled.Print, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.print_sample)) }
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val f = repo.samplePdf(setup, setup.letterhead)
                            if (f != null) Intents.openFile(context, f, "application/pdf", repo.store)
                            else snackbar.showSnackbar(previewFail)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.preview_pdf)) }
            }

            Button(
                onClick = {
                    scope.launch {
                        repo.savePageSetup(setup)
                        snackbar.showSnackbar(savedMsg)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text(stringResource(R.string.save)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MmSlider(labelRes: Int, value: Int, range: ClosedFloatingPointRange<Float>, onChange: (Int) -> Unit) {
    Text(stringResource(labelRes, value), style = MaterialTheme.typography.bodySmall)
    Slider(value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) }, valueRange = range)
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SigBlock(n: Int, html: String, image: String?, onHtml: (String) -> Unit, onPick: (Uri?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.signature_n, n), style = MaterialTheme.typography.labelLarge)
        AppField(html, onHtml, stringResource(R.string.signature_text), singleLine = false, minLines = 2)
        ImagePicker(stringResource(R.string.signature_image), image, onPick)
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
