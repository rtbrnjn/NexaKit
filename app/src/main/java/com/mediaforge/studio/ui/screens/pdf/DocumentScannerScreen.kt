package com.mediaforge.studio.ui.screens.pdf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.mediaforge.studio.ui.components.EmptyPicker
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.components.UriImage
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.DocumentScannerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocumentScannerScreen(vm: DocumentScannerViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val pages by vm.pageFiles.collectAsState()
    val prefix by vm.filePrefix.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.onScanResult(GmsDocumentScanningResult.fromActivityResultIntent(result.data))
        }
    }

    fun launchScanner() {
        val activity = ctx.findActivity()
        if (activity == null) {
            vm.onScannerError("Activity kamera tidak tersedia.")
            return
        }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(30)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                vm.onScannerError(e.localizedMessage ?: "Gagal membuka ML Kit Document Scanner.")
            }
    }

    ToolPage(
        title = "Document Scanner",
        subtitle = "Auto-crop perspektif & filter dokumen bersih offline",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        if (pages.isEmpty() && !vm.hasPdf) {
            EmptyPicker(
                title = "Pindai Dokumen Sekarang",
                subtitle = "Deteksi tepi kertas otomatis • Multi-halaman • PDF atau JPG",
                icon = Icons.Rounded.DocumentScanner,
                onClick = ::launchScanner
            )

            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Kemampuan Pemindai Offline")
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScannerFeatureTag(Icons.Rounded.Crop, "Auto-Crop Perspektif")
                    ScannerFeatureTag(Icons.Rounded.AutoAwesome, "Hapus Bayangan & Filter")
                    ScannerFeatureTag(Icons.Rounded.PictureAsPdf, "Documents/DocPix (.pdf)")
                    ScannerFeatureTag(Icons.Rounded.Image, "Pictures/DocPix (.jpg)")
                }
                Spacer(Modifier.height(14.dp))
                GlassButton(
                    text = "Mulai Scan Dokumen",
                    icon = Icons.Rounded.DocumentScanner,
                    primary = true,
                    onClick = ::launchScanner,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${pages.size} Halaman Hasil Scan",
                            style = MaterialTheme.typography.titleMedium,
                            color = Glass.TextPrimary
                        )
                        Hint("Pilih simpan ke PDF atau simpan setiap halaman ke Gambar")
                    }
                    GlassButton(
                        text = "Scan Ulang",
                        icon = Icons.Rounded.DocumentScanner,
                        primary = false,
                        compact = true,
                        onClick = ::launchScanner
                    )
                    Spacer(Modifier.width(6.dp))
                    GlassButton(
                        text = "Hapus",
                        icon = Icons.Rounded.DeleteSweep,
                        primary = false,
                        compact = true,
                        onClick = vm::clearSession
                    )
                }

                if (pages.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    pages.chunked(3).forEachIndexed { rowIdx, rowFiles ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFiles.forEachIndexed { colIdx, file ->
                                val pageNo = rowIdx * 3 + colIdx + 1
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(0.74f)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    UriImage(
                                        uri = Uri.fromFile(file),
                                        modifier = Modifier.matchParentSize(),
                                        maxDim = 540
                                    )
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Glass.Surface.copy(alpha = 0.85f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "$pageNo",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Glass.TextPrimary
                                        )
                                    }
                                }
                            }
                            repeat(3 - rowFiles.size) { Spacer(Modifier.weight(1f)) }
                        }
                        if (rowIdx < (pages.size - 1) / 3) Spacer(Modifier.height(8.dp))
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Nama File Output")
                Spacer(Modifier.height(8.dp))
                GlassTextField(
                    value = prefix,
                    onValueChange = vm::setFilePrefix,
                    label = "Nama Dokumen",
                    placeholder = "Scan_2026...",
                    singleLine = true
                )
            }

            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Pilih Format Penyimpanan")
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = "Simpan sebagai PDF (Documents/DocPix)",
                    icon = Icons.Rounded.PictureAsPdf,
                    primary = true,
                    onClick = vm::saveAsPdf,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = "Simpan sebagai Gambar JPG (Pictures/DocPix)",
                    icon = Icons.Rounded.Image,
                    primary = false,
                    enabled = pages.isNotEmpty(),
                    onClick = vm::saveAsImages,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ScannerFeatureTag(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Glass.CardFillSubtle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Glass.Cyan, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Glass.TextSecondary)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
