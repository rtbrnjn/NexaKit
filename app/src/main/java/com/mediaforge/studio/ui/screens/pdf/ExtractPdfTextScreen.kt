package com.mediaforge.studio.ui.screens.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.ui.components.EmptyPicker
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassChip
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.screens.ai.copyToClipboard
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.ExtractPdfTextViewModel
import com.mediaforge.studio.vm.PdfSource

@Composable
internal fun ExtractPdfTextScreen(
    vm: ExtractPdfTextViewModel,
    onBack: () -> Unit,
    onSendToAi: (PdfSource, String, Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val source by vm.source.collectAsState()
    val pageRange by vm.pageRange.collectAsState()
    val extractedText by vm.extractedText.collectAsState()
    val usedOcr by vm.usedOcr.collectAsState()
    val summary by vm.summary.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Ekstrak Teks PDF",
        subtitle = "Ekstrak teks & OCR lokal offline • 1 ketukan ke AI Studio",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Pilih File PDF",
                subtitle = "Salin teks PDF digital atau pindai halaman scan via ML Kit OCR offline",
                icon = Icons.Rounded.TextFields,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Cara Kerja Offline")
                Spacer(Modifier.height(6.dp))
                Hint("1. PDF berbasis teks langsung diekstrak secara instan di perangkat.")
                Spacer(Modifier.height(4.dp))
                Hint("2. PDF hasil scan otomatis diproses menggunakan ML Kit OCR lokal tanpa internet.")
                Spacer(Modifier.height(4.dp))
                Hint("3. Salin seluruh teks atau kirim langsung ke AI Studio untuk diringkas.")
            }
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }

            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Rentang Halaman")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlassTextField(
                        value = pageRange,
                        onValueChange = vm::setPageRange,
                        label = "Halaman (mis. 1-5, all)",
                        placeholder = "all",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    GlassButton(
                        text = "Ekstrak",
                        primary = false,
                        compact = true,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.extract(forceOcr = false)
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1-5", "1-10", "all").forEach { preset ->
                        GlassChip(
                            text = if (preset == "all") "Semua (${src.pageCount} hal)" else "Hal $preset",
                            selected = pageRange.equals(preset, ignoreCase = true),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                vm.setPageRange(preset)
                                vm.extract(forceOcr = false)
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = "Paksa OCR Lokal (Scan / Gambar)",
                    icon = Icons.Rounded.DocumentScanner,
                    primary = false,
                    compact = true,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.extract(forceOcr = true)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (extractedText.isNotBlank()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionLabel(if (usedOcr) "Hasil OCR Lokal" else "Teks Terekstrak")
                    if (summary.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Hint(summary)
                    }
                    Spacer(Modifier.height(10.dp))
                    val boxShape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 300.dp)
                            .clip(boxShape)
                            .background(Glass.CardFillSubtle)
                            .border(1.dp, Glass.Stroke, boxShape)
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = extractedText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Glass.TextPrimary
                            )
                        }
                    }
                }

                GlassButton(
                    text = "Salin Semua Teks",
                    icon = Icons.Rounded.ContentCopy,
                    primary = false,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        copyToClipboard(ctx, "Teks PDF", extractedText)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                GlassButton(
                    text = "Ringkas di AI Studio",
                    icon = Icons.Rounded.AutoAwesome,
                    primary = true,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendToAi(src, extractedText, usedOcr)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (progress == null) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Belum ada teks yang terekstrak", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Hint("Jika PDF berupa foto/hasil scan, tekan tombol \"Paksa OCR Lokal\" di atas untuk membaca teks secara offline.")
                }
            }
        }
    }
}
