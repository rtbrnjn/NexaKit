package com.mediaforge.studio.ui.screens.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.media.CompressionPreset
import com.mediaforge.studio.media.MaxDimension
import com.mediaforge.studio.media.OutputFormat
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.EmptyPicker
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassChip
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassSwitch
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.LabeledSlider
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.components.UriImage
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.CompressorViewModel
import dev.chrisbanes.haze.HazeState

@Composable
internal fun CompressorTab(vm: CompressorViewModel, topPadding: Dp, hazeState: HazeState?) {
    val items by vm.items.collectAsState()
    val opts by vm.options.collectAsState()
    val estimating by vm.estimating.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    val haptics = LocalHapticFeedback.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.addImages(uris)
    }

    ToolPage(
        title = null,
        subtitle = null,
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        topPadding = topPadding,
        hazeState = hazeState
    ) {
        if (items.isEmpty()) {
            EmptyPicker(
                title = "Select Photos to Compress",
                subtitle = "Multi-select gallery images • Real-time size & EXIF cleaner",
                icon = Icons.Rounded.AddPhotoAlternate,
                onClick = { picker.launch("image/*") }
            )
        } else {
            val totalBefore = items.sumOf { it.originalBytes }
            val readyEst = items.all { it.estimatedBytes != null }
            val totalAfter = items.sumOf { it.estimatedBytes ?: it.originalBytes }
            val pct = FileUtils.savingsPercent(totalBefore, totalAfter)

            GlassCard(Modifier.fillMaxWidth(), accent = Glass.Emerald) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${items.size} image(s) queued", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                        val summary = if (estimating && !readyEst) {
                            "${FileUtils.formatBytes(totalBefore)} → calculating…"
                        } else {
                            "${FileUtils.formatBytes(totalBefore)} → ${FileUtils.formatBytes(totalAfter)}"
                        }
                        Text(summary, style = MaterialTheme.typography.bodyMedium, color = Glass.Cyan)
                    }
                    if (readyEst && totalBefore > 0) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    (if (pct >= 0) Glass.Emerald else Glass.Amber).copy(alpha = 0.14f)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                if (pct >= 0) "-$pct%" else "+${-pct}%",
                                color = if (pct >= 0) Glass.Emerald else Glass.Amber,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "Add More",
                        icon = Icons.Rounded.AddPhotoAlternate,
                        primary = false,
                        compact = true,
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Clear",
                        icon = Icons.Rounded.DeleteSweep,
                        primary = false,
                        compact = true,
                        onClick = vm::clear
                    )
                }
            }

            items.forEach { item ->
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UriImage(
                            item.uri,
                            Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            maxDim = 220
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Glass.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val est = item.estimatedBytes
                            val sizeLine = if (est != null && est > 0) {
                                val s = FileUtils.savingsPercent(item.originalBytes, est)
                                "${FileUtils.formatBytes(item.originalBytes)} → ${FileUtils.formatBytes(est)}" +
                                    if (s > 0) " (-$s%)" else ""
                            } else {
                                "${FileUtils.formatBytes(item.originalBytes)} → estimating…"
                            }
                            Text(sizeLine, style = MaterialTheme.typography.bodySmall, color = Glass.Cyan)
                            if (opts.stripExif && item.hasGps) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.LocationOff, null, tint = Glass.Amber, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("GPS EXIF detected • will strip", style = MaterialTheme.typography.labelSmall, color = Glass.Amber)
                                }
                            } else {
                                Text(
                                    if (opts.stripExif) "EXIF & GPS metadata will be stripped" else "Basic camera EXIF retained",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.TextDim
                                )
                            }
                        }
                        GlassIconButton(Icons.Rounded.Close, "Remove", onClick = { vm.remove(item.uri) })
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Compression Settings")
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompressionPreset.entries.forEach { preset ->
                    GlassChip(
                        text = preset.label,
                        selected = opts.quality == preset.quality,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.setQuality(preset.quality)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LabeledSlider(
                label = "Quality",
                valueText = "${opts.quality}%",
                value = opts.quality.toFloat(),
                range = 10f..95f,
                onChange = { vm.setQuality(it.toInt()) }
            )
            SectionLabel("Max Resolution")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(MaxDimension.entries, opts.maxDimension, { it.label }, vm::setMaxDimension)
            Spacer(Modifier.height(10.dp))
            SectionLabel("Output Format")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(OutputFormat.entries, opts.format, { it.label }, vm::setFormat)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strip EXIF Metadata", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                    Hint("Hapus informasi lokasi GPS & detail kamera saat kompresi")
                }
                GlassSwitch(opts.stripExif) { enabled ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.setStripExif(enabled)
                }
            }
        }

        GlassButton(
            text = if (items.isEmpty()) "Compress Selected Files" else if (opts.stripExif) "Compress & Strip EXIF (${items.size})" else "Compress Selected Files (${items.size})",
            icon = Icons.Rounded.Download,
            enabled = items.isNotEmpty(),
            onClick = vm::compressAll,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
