package com.mediaforge.studio.ui.screens.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.pdf.PdfCompressPreset
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.EmptyPicker
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.CompressPdfViewModel
import com.mediaforge.studio.vm.EditPdfViewModel
import com.mediaforge.studio.vm.MergePdfViewModel
import com.mediaforge.studio.vm.SplitPdfViewModel

@Composable
internal fun MergePdfScreen(vm: MergePdfViewModel, onBack: () -> Unit) {
    val sources by vm.sources.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.add(uris)
    }

    ToolPage(
        title = "Merge PDFs",
        subtitle = "Combine & reorder multiple PDFs",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        if (sources.isEmpty()) {
            EmptyPicker(
                title = "Add PDF Files",
                subtitle = "Select 2 or more PDFs to merge in order",
                icon = Icons.AutoMirrored.Rounded.MergeType,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${sources.size} PDF(s) • ${sources.sumOf { it.pageCount }} pages", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                        Hint("Total input size: ${FileUtils.formatBytes(sources.sumOf { it.sizeBytes })}")
                    }
                    GlassButton("Add", icon = Icons.Rounded.FileOpen, primary = false, compact = true, onClick = { picker.launch(arrayOf("application/pdf")) })
                    Spacer(Modifier.width(8.dp))
                    GlassButton("Clear", icon = Icons.Rounded.DeleteSweep, primary = false, compact = true, onClick = vm::clear)
                }
            }

            sources.forEachIndexed { i, src ->
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = Glass.Cyan, modifier = Modifier.width(26.dp))
                        Column(Modifier.weight(1f)) {
                            Text(src.name, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${src.pageCount} pages • ${FileUtils.formatBytes(src.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
                        }
                        GlassIconButton(Icons.Rounded.ArrowUpward, "Up", { vm.move(i, -1) }, enabled = i > 0)
                        GlassIconButton(Icons.Rounded.ArrowDownward, "Down", { vm.move(i, 1) }, enabled = i < sources.lastIndex)
                        GlassIconButton(Icons.Rounded.Close, "Remove", { vm.remove(i) })
                    }
                }
            }
        }

        GlassButton(
            text = "Merge ${sources.size} PDFs",
            icon = Icons.Rounded.Download,
            enabled = sources.size >= 2,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun SplitPdfScreen(vm: SplitPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val selected by vm.selected.collectAsState()
    val rangeText by vm.rangeText.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    var visibleLimit by remember(source) { mutableIntStateOf(30) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Split / Extract Pages",
        subtitle = "Tap thumbnails or enter ranges like 1-3, 6",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF File",
                subtitle = "Preview page thumbnails & extract selected pages",
                icon = Icons.Rounded.ContentCut,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }

            GlassCard(Modifier.fillMaxWidth()) {
                GlassTextField(
                    value = rangeText,
                    onValueChange = vm::setRangeText,
                    label = "Page Range (1-based)",
                    placeholder = "e.g. 1-3, 6",
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Select All", primary = false, compact = true, onClick = vm::selectAll, modifier = Modifier.weight(1f))
                    GlassButton("Clear (${selected.size})", primary = false, compact = true, onClick = vm::clearSelection, modifier = Modifier.weight(1f))
                }
            }

            val showCount = minOf(src.pageCount, visibleLimit)
            (0 until showCount).chunked(3).forEach { rowIndexes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowIndexes.forEach { idx ->
                        val isPicked = idx in selected
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(13.dp))
                                .border(
                                    width = if (isPicked) 2.5.dp else 1.dp,
                                    color = if (isPicked) Glass.Cyan else Glass.Stroke,
                                    shape = RoundedCornerShape(13.dp)
                                )
                                .clickable { vm.toggle(idx) }
                        ) {
                            PdfThumbImage(vm.thumbs, idx, Modifier.fillMaxSize())
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.Black.copy(alpha = 0.68f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("${idx + 1}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                            if (isPicked) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Glass.Cyan),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Check, null, tint = Glass.OnAccent, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                    repeat(3 - rowIndexes.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            if (src.pageCount > visibleLimit) {
                GlassButton(
                    text = "Show More Pages ($visibleLimit / ${src.pageCount})",
                    primary = false,
                    compact = true,
                    onClick = { visibleLimit += 30 },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            GlassButton(
                text = "Extract Selected Pages",
                icon = Icons.Rounded.Download,
                enabled = rangeText.isNotBlank(),
                onClick = vm::export,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun CompressPdfScreen(vm: CompressPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val preset by vm.preset.collectAsState()
    val result by vm.result.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Compress PDF",
        subtitle = "Optimise embedded page images offline",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF to Shrink",
                subtitle = "Best for scanned PDFs & photo-heavy documents",
                icon = Icons.Rounded.Compress,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }
        }

        result?.let { r ->
            val pct = FileUtils.savingsPercent(r.before, r.after)
            GlassCard(Modifier.fillMaxWidth(), accent = if (pct > 0) Glass.Emerald else Glass.Amber) {
                SectionLabel("Compression Result")
                Spacer(Modifier.height(4.dp))
                Text(
                    "${FileUtils.formatBytes(r.before)} → ${FileUtils.formatBytes(r.after)}" +
                        if (pct > 0) "  (-$pct%)" else "  (already minimal)",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (pct > 0) Glass.Emerald else Glass.Amber
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Compression Preset")
            Spacer(Modifier.height(8.dp))
            ChoiceRow(PdfCompressPreset.entries, preset, { it.label }, vm::setPreset)
            Spacer(Modifier.height(8.dp))
            Hint("${preset.detail} • ${(preset.jpegQuality * 100).toInt()}% JPEG image quality")
        }

        GlassButton(
            text = "Compress PDF (${preset.label})",
            icon = Icons.Rounded.Download,
            enabled = src != null,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun EditPdfScreen(vm: EditPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val rotations by vm.rotations.collectAsState()
    val deleted by vm.deleted.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    var visibleLimit by remember(source) { mutableIntStateOf(24) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Rotate & Delete Pages",
        subtitle = "Adjust page angles (90°/180°/270°) or remove pages",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF File",
                subtitle = "Preview pages to rotate or delete individually",
                icon = Icons.Rounded.Rotate90DegreesCw,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }

            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("All +90°", icon = Icons.AutoMirrored.Rounded.RotateRight, primary = false, compact = true, onClick = { vm.rotateAll(90) }, modifier = Modifier.weight(1f))
                    GlassButton("All 180°", icon = Icons.Rounded.Rotate90DegreesCw, primary = false, compact = true, onClick = { vm.rotateAll(180) }, modifier = Modifier.weight(1f))
                    GlassButton("Reset", icon = Icons.Rounded.Restore, primary = false, compact = true, onClick = vm::reset)
                }
            }

            val showCount = minOf(src.pageCount, visibleLimit)
            (0 until showCount).chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { idx ->
                        val rot = rotations[idx] ?: 0
                        val isDel = idx in deleted
                        GlassCard(
                            Modifier
                                .weight(1f)
                                .alpha(if (isDel) 0.45f else 1f),
                            contentPadding = PaddingValues(10.dp)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                PdfThumbImage(vm.thumbs, idx, Modifier.fillMaxSize(), rotation = rot.toFloat())
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.68f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (isDel) "DEL" else if (rot != 0) "P${idx + 1} • ${rot}°" else "P${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDel) Glass.Danger else Color.White
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                GlassIconButton(Icons.AutoMirrored.Rounded.RotateRight, "Rotate 90°", { vm.rotate(idx, 90) }, enabled = !isDel, tint = Glass.Cyan)
                                GlassIconButton(Icons.Rounded.Rotate90DegreesCw, "Rotate 180°", { vm.rotate(idx, 180) }, enabled = !isDel)
                                GlassIconButton(
                                    if (isDel) Icons.Rounded.Restore else Icons.Rounded.Delete,
                                    if (isDel) "Restore" else "Delete",
                                    { vm.toggleDelete(idx) },
                                    tint = if (isDel) Glass.Emerald else Glass.Danger
                                )
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            if (src.pageCount > visibleLimit) {
                GlassButton(
                    text = "Show More Pages ($visibleLimit / ${src.pageCount})",
                    primary = false,
                    compact = true,
                    onClick = { visibleLimit += 24 },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            GlassButton(
                text = "Save Edited PDF",
                icon = Icons.Rounded.Download,
                onClick = vm::export,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
