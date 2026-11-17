package com.mediaforge.studio.ui.screens.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.ai.DocTask
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.EmptyPicker
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassChip
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassSwitch
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.ProgressOverlay
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.StatusBanner
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.ExtractDepth
import com.mediaforge.studio.vm.PdfAiViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PdfAssistantTab(
    vm: PdfAiViewModel,
    aiReady: Boolean,
    topPadding: Dp,
    hazeState: HazeState?
) {
    val ctx = LocalContext.current
    val source by vm.source.collectAsState()
    val pageRange by vm.pageRange.collectAsState()
    val depth by vm.depth.collectAsState()
    val visionMode by vm.visionMode.collectAsState()
    val isScannedPdf by vm.isScannedPdf.collectAsState()
    val extractInfo by vm.extractInfo.collectAsState()
    val messages by vm.messages.collectAsState()
    val isStreaming by vm.isStreaming.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    var question by rememberSaveable { mutableStateOf("") }
    var showScope by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .let { if (hazeState != null) it.hazeSource(hazeState, zIndex = 1f) else it }
                .padding(horizontal = 16.dp)
                .padding(top = topPadding, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusBanner(message, vm::dismissMessage)

            val src = source
            if (src == null) {
                EmptyPicker(
                    title = "Open a PDF Document",
                    subtitle = "Smart page range + Local ML Kit OCR for scanned PDFs",
                    icon = Icons.Rounded.PictureAsPdf,
                    onClick = { picker.launch(arrayOf("application/pdf")) }
                )
            } else {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PictureAsPdf, null, tint = Glass.Cyan)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(src.name, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${src.pageCount}p • $extractInfo", style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary, maxLines = 2)
                        }
                        GlassIconButton(Icons.Rounded.Tune, "Page scope & OCR", { showScope = !showScope }, tint = if (showScope) Glass.Cyan else Glass.TextSecondary)
                        if (messages.isNotEmpty()) {
                            GlassIconButton(Icons.Rounded.DeleteSweep, "Clear chat", vm::clearChat)
                        }
                        GlassButton("Open", primary = false, compact = true, onClick = { picker.launch(arrayOf("application/pdf")) })
                    }

                    if (showScope) {
                        Spacer(Modifier.height(10.dp))
                        SectionLabel("Smart Extraction Scope")
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            GlassTextField(
                                value = pageRange,
                                onValueChange = vm::setPageRange,
                                label = "Pages (e.g. 1-5, all)",
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            GlassButton("Apply", primary = false, compact = true, onClick = vm::applyRange)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1-5", "1-10", "all").forEach { preset ->
                                GlassChip(
                                    text = if (preset == "all") "All (${src.pageCount}p)" else "Pages $preset",
                                    selected = pageRange.equals(preset, ignoreCase = true),
                                    onClick = { vm.setPageRange(preset); vm.applyRange() }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SectionLabel("Character Budget")
                        Spacer(Modifier.height(6.dp))
                        ChoiceRow(ExtractDepth.entries, depth, { it.label }, vm::setDepth)

                        if (isScannedPdf || visionMode) {
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Vision Mode (Upload Page Images)", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                                    Hint("Default uses fast offline ML Kit OCR; enable Vision Mode only for charts/diagrams.")
                                }
                                GlassSwitch(visionMode, vm::setVisionMode)
                            }
                        }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocTask.entries.forEach { task ->
                        GlassChip(
                            text = task.label,
                            selected = false,
                            onClick = { if (aiReady && !isStreaming) vm.runDocTask(task) },
                            color = Glass.Emerald
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                                Text("Ready to analyze \"${src.name}\"", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                                Spacer(Modifier.height(4.dp))
                                Hint("Tap a quick action above or ask a question below. Results stream live and archive to AI Vault.")
                            }
                        }
                    } else {
                        items(messages) { msg ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start
                            ) {
                                val shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (msg.fromUser) 18.dp else 4.dp,
                                    bottomEnd = if (msg.fromUser) 4.dp else 18.dp
                                )
                                Column(
                                    Modifier
                                        .widthIn(max = 315.dp)
                                        .clip(shape)
                                        .background(
                                            if (msg.fromUser) Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.18f else 0.13f)
                                            else Glass.CardFill
                                        )
                                        .border(
                                            1.dp,
                                            if (msg.fromUser) Glass.Cyan.copy(alpha = 0.42f) else Glass.Stroke,
                                            shape
                                        )
                                        .padding(12.dp)
                                ) {
                                    if (!msg.fromUser && msg.isStreaming) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.8.dp, color = Glass.Cyan)
                                            Spacer(Modifier.width(6.dp))
                                            Text(msg.streamPhase ?: "Streaming…", style = MaterialTheme.typography.labelSmall, color = Glass.Cyan)
                                        }
                                        if (msg.text.isNotEmpty()) Spacer(Modifier.height(6.dp))
                                    }
                                    if (msg.text.isNotEmpty()) {
                                        Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                                    }
                                    if (!msg.fromUser && !msg.isStreaming && msg.text.isNotBlank()) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            GlassIconButton(
                                                Icons.Rounded.ContentCopy,
                                                "Copy reply",
                                                { copyToClipboard(ctx, "Gemini Answer", msg.text) },
                                                tint = Glass.Cyan
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlassTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = "Ask about this PDF…",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isStreaming) {
                        Box(
                            Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Glass.Danger.copy(alpha = 0.16f))
                                .border(1.dp, Glass.Danger.copy(alpha = 0.5f), CircleShape)
                                .clickable { vm.stopStreaming() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.StopCircle, "Stop", tint = Glass.Danger)
                        }
                    } else {
                        Box(
                            Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (aiReady && question.isNotBlank()) Glass.Cyan else Glass.CardFill)
                                .border(1.dp, Glass.Stroke, CircleShape)
                                .clickable(enabled = aiReady && question.isNotBlank()) {
                                    val q = question
                                    question = ""
                                    vm.ask(q)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                "Send",
                                tint = if (aiReady && question.isNotBlank()) Glass.OnAccent else Glass.TextDim
                            )
                        }
                    }
                }
            }
        }
        ProgressOverlay(progress)
    }
}
