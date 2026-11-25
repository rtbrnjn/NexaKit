package com.mediaforge.studio.ui.screens.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.ai.CopyLanguage
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.data.db.AiHistoryKind
import com.mediaforge.studio.ui.components.BitmapImage
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
import com.mediaforge.studio.vm.AiVaultViewModel
import com.mediaforge.studio.vm.CopywriterViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

internal fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "$label copied", Toast.LENGTH_SHORT).show()
}

internal fun pasteFromClipboard(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0).coerceToText(ctx)?.toString()?.takeIf { it.isNotBlank() }
}

@Composable
internal fun QuickPasteResetRow(
    hasText: Boolean,
    onPaste: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassButton(
            text = "Tempel",
            icon = Icons.Rounded.ContentPaste,
            primary = false,
            compact = true,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val pasted = pasteFromClipboard(ctx)
                if (pasted != null) {
                    onPaste(pasted)
                } else {
                    Toast.makeText(ctx, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                }
            }
        )
        GlassButton(
            text = "Bersihkan",
            icon = Icons.Rounded.Close,
            primary = false,
            compact = true,
            enabled = hasText,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClear()
            }
        )
    }
}

private enum class VaultFilter(val label: String) {
    ALL("All"),
    PDF("PDF Summaries"),
    COPY("Copywriting")
}

@Composable
internal fun CopywriterTab(
    vm: CopywriterViewModel,
    aiReady: Boolean,
    topPadding: Dp,
    hazeState: HazeState?
) {
    val ctx = LocalContext.current
    val uri by vm.uri.collectAsState()
    val preview by vm.preview.collectAsState()
    val payloadSize by vm.payloadSize.collectAsState()
    val language by vm.language.collectAsState()
    val notes by vm.notes.collectAsState()
    val streamingText by vm.streamingText.collectAsState()
    val isStreaming by vm.isStreaming.collectAsState()
    val liveStatus by vm.liveStatus.collectAsState()
    val result by vm.result.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.pick(u)
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
        if (uri == null) {
            EmptyPicker(
                title = "Select a Product Photo",
                subtitle = "Auto-resized to 768px / 75% JPEG • Streams copy word-by-word",
                icon = Icons.Rounded.AddPhotoAlternate,
                onClick = { picker.launch("image/*") }
            )
        } else {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Glass.CardFillSubtle)
                    ) {
                        BitmapImage(preview, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Product Photo Ready", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                        Hint(
                            if (payloadSize > 0) "Lightweight payload: 768px · 75% JPEG (${FileUtils.formatBytes(payloadSize)})"
                            else liveStatus ?: "Compressing preview in memory…"
                        )
                        GlassButton("Change Photo", primary = false, compact = true, onClick = { picker.launch("image/*") })
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Output Language")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(CopyLanguage.entries, language, { it.label }, vm::setLanguage)
            Spacer(Modifier.height(10.dp))
            QuickPasteResetRow(
                hasText = notes.isNotBlank(),
                onPaste = { pasted -> vm.setNotes(if (notes.isBlank()) pasted else "$notes $pasted") },
                onClear = { vm.setNotes("") }
            )
            Spacer(Modifier.height(6.dp))
            GlassTextField(
                value = notes,
                onValueChange = vm::setNotes,
                label = "Optional Product Notes",
                placeholder = "e.g. Matte ceramic, free shipping, minimalist desk setup",
                minLines = 2,
                maxLines = 4
            )
        }

        if (isStreaming) {
            GlassButton(
                text = "Stop Streaming",
                icon = Icons.Rounded.StopCircle,
                primary = false,
                onClick = vm::stop,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            GlassButton(
                text = "Generate Marketing Copy",
                icon = Icons.Rounded.AutoAwesome,
                enabled = uri != null && payloadSize > 0 && aiReady,
                onClick = vm::generate,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isStreaming) {
            GlassCard(Modifier.fillMaxWidth(), accent = Glass.Cyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Glass.Cyan)
                    Spacer(Modifier.width(8.dp))
                    SectionLabel(liveStatus ?: "Menghubungkan ke Gemini...")
                }
                if (streamingText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(streamingText, style = MaterialTheme.typography.bodySmall, color = Glass.TextPrimary)
                }
            }
        }

        if (!isStreaming) {
            result?.let { copy ->
                if (copy.title.isNotBlank()) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Product Title", Modifier.weight(1f))
                            GlassIconButton(Icons.Rounded.ContentCopy, "Copy title", { copyToClipboard(ctx, "Product Title", copy.title) }, tint = Glass.Cyan)
                        }
                        Text(copy.title, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    }
                }

                if (copy.hooks.isNotEmpty()) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Short Video Hooks", Modifier.weight(1f))
                            GlassIconButton(
                                Icons.Rounded.ContentCopy,
                                "Copy hooks",
                                { copyToClipboard(ctx, "Review Hooks", copy.hooks.mapIndexed { i, h -> "${i + 1}. $h" }.joinToString("\n")) },
                                tint = Glass.Emerald
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            copy.hooks.forEachIndexed { idx, hook ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Glass.CardFillSubtle)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("${idx + 1}.", style = MaterialTheme.typography.labelLarge, color = Glass.Emerald, modifier = Modifier.width(22.dp))
                                    Text(hook, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary, modifier = Modifier.weight(1f))
                                    GlassIconButton(Icons.Rounded.ContentCopy, "Copy hook", { copyToClipboard(ctx, "Hook ${idx + 1}", hook) }, tint = Glass.TextSecondary)
                                }
                            }
                        }
                    }
                }

                if (copy.caption.isNotBlank() || copy.hashtags.isNotEmpty()) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Caption & Hashtags", Modifier.weight(1f))
                            GlassIconButton(
                                Icons.Rounded.ContentCopy,
                                "Copy caption",
                                { copyToClipboard(ctx, "Caption & Hashtags", copy.captionWithTags) },
                                tint = Glass.Cyan
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(copy.caption, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                        if (copy.hashtagLine.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(copy.hashtagLine, style = MaterialTheme.typography.bodySmall, color = Glass.Cyan)
                        }
                        Spacer(Modifier.height(12.dp))
                        GlassButton(
                            text = "Copy All Copywriting",
                            icon = Icons.Rounded.ContentCopy,
                            primary = false,
                            compact = true,
                            onClick = { copyToClipboard(ctx, "Full Product Copy", copy.asPlainText()) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiVaultTab(
    vm: AiVaultViewModel,
    topPadding: Dp,
    hazeState: HazeState?
) {
    val ctx = LocalContext.current
    val entries by vm.entries.collectAsState()
    var filter by rememberSaveable { mutableStateOf(VaultFilter.ALL) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    val visible = remember(entries, filter) {
        when (filter) {
            VaultFilter.ALL -> entries
            VaultFilter.PDF -> entries.filter { it.kind == AiHistoryKind.PDF_SUMMARY }
            VaultFilter.COPY -> entries.filter { it.kind == AiHistoryKind.COPYWRITING }
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .let { if (hazeState != null) it.hazeSource(hazeState, zIndex = 1f) else it }
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = topPadding, bottom = 120.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ChoiceRow(VaultFilter.entries, filter, { it.label }, { filter = it }, modifier = Modifier.weight(1f))
                if (entries.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(Icons.Rounded.DeleteSweep, "Clear AI Vault", vm::clearAll, tint = Glass.Danger)
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp)) {
                    Text("AI History Vault is empty", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Hint("PDF summaries, translations, Q&A replies, and product copywriting generated by Gemini are automatically saved here offline.")
                }
            }
        } else {
            items(visible, key = { it.id }) { item ->
                val isPdf = item.kind == AiHistoryKind.PDF_SUMMARY
                val expanded = expandedId == item.id
                GlassCard(
                    Modifier.fillMaxWidth(),
                    onClick = { expandedId = if (expanded) null else item.id },
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background((if (isPdf) Glass.Cyan else Glass.Emerald).copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isPdf) "PDF AI" else "COPY",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPdf) Glass.Cyan else Glass.Emerald
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${item.subtitle} • ${FileUtils.formatDate(item.createdAt)}", style = MaterialTheme.typography.labelSmall, color = Glass.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        GlassIconButton(Icons.Rounded.ContentCopy, "Copy", { copyToClipboard(ctx, item.title, item.content) }, tint = Glass.Cyan)
                        GlassIconButton(Icons.Rounded.Delete, "Delete", { vm.delete(item.id) }, tint = Glass.Danger)
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            tint = Glass.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.TextSecondary,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
