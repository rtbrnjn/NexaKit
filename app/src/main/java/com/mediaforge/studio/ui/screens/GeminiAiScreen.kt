package com.mediaforge.studio.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaforge.studio.ai.CopyLanguage
import com.mediaforge.studio.ai.DocTask
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.data.db.AiHistoryKind
import com.mediaforge.studio.ui.components.BitmapImage
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.EmptyPicker
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassChip
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassSegmentedBar
import com.mediaforge.studio.ui.components.GlassSwitch
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.OfflineBadge
import com.mediaforge.studio.ui.components.OnlineBadge
import com.mediaforge.studio.ui.components.ProgressOverlay
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.StatusBanner
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.AiVaultViewModel
import com.mediaforge.studio.vm.CopywriterViewModel
import com.mediaforge.studio.vm.ExtractDepth
import com.mediaforge.studio.vm.PdfAiViewModel
import com.mediaforge.studio.vm.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

enum class AiTool(val label: String, val icon: ImageVector) {
    PDF_CHAT("PDF AI", Icons.Rounded.Description),
    COPYWRITER("Copywriter", Icons.Rounded.Campaign),
    VAULT("AI Vault", Icons.Rounded.Bookmarks)
}

private enum class VaultFilter(val label: String) {
    ALL("All"),
    PDF("PDF Summaries"),
    COPY("Copywriting")
}

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "$label copied", Toast.LENGTH_SHORT).show()
}

@Composable
fun GeminiAiScreen(
    settingsVm: SettingsViewModel,
    onOpenVault: () -> Unit,
    copyVm: CopywriterViewModel = viewModel(),
    pdfAiVm: PdfAiViewModel = viewModel(),
    vaultVm: AiVaultViewModel = viewModel(),
    requestedTool: AiTool? = null,
    onToolHandled: () -> Unit = {}
) {
    val online by settingsVm.online.collectAsState()
    val apiKey by settingsVm.savedKey.collectAsState()
    val model by settingsVm.model.collectAsState()
    var active by rememberSaveable { mutableStateOf(AiTool.PDF_CHAT) }

    val headerHaze = rememberHazeState()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val topPadding = with(LocalDensity.current) { headerHeightPx.toDp() } + 8.dp

    LaunchedEffect(requestedTool) {
        if (requestedTool != null) {
            active = requestedTool
            onToolHandled()
        }
    }

    val aiReady = online && apiKey.isNotBlank()

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = active,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { if (forward) it / 16 else -it / 16 })
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "aiSubTab",
            modifier = Modifier.fillMaxSize()
        ) { tool ->
            when (tool) {
                AiTool.PDF_CHAT -> PdfAssistantTab(pdfAiVm, aiReady, topPadding, headerHaze)
                AiTool.COPYWRITER -> CopywriterTab(copyVm, aiReady, topPadding, headerHaze)
                AiTool.VAULT -> AiVaultTab(vaultVm, topPadding, headerHaze)
            }
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NexaKit · Gemini AI", style = MaterialTheme.typography.headlineMedium, color = Glass.TextPrimary)
                    Text("Turbo SSE Streaming • $model", style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (online && apiKey.isNotBlank()) OnlineBadge() else OfflineBadge(compact = true)
            }

            if (!online) {
                GlassCard(Modifier.fillMaxWidth(), accent = Glass.Amber, contentPadding = PaddingValues(11.dp)) {
                    Text(
                        "Offline Mode — All Media & PDF Tools Ready. Connect to the internet for live Gemini AI streaming.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.TextPrimary
                    )
                }
            } else if (apiKey.isBlank()) {
                GlassCard(Modifier.fillMaxWidth(), accent = Glass.Amber, contentPadding = PaddingValues(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Key, null, tint = Glass.Amber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Offline Mode — Add your Gemini API key in Settings to enable AI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        GlassButton("Settings", primary = false, compact = true, onClick = onOpenVault)
                    }
                }
            }

            GlassSegmentedBar(
                items = AiTool.entries,
                selected = active,
                label = { it.label },
                icon = { it.icon },
                onSelect = { active = it },
                hazeState = headerHaze
            )
        }
    }
}

// ============================================================ 1. PDF Summariser & Streaming Chat

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfAssistantTab(
    vm: PdfAiViewModel,
    aiReady: Boolean,
    topPadding: Dp,
    hazeState: HazeState
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
                .hazeSource(hazeState)
                .padding(horizontal = 16.dp)
                .padding(top = topPadding, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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

// ============================================================ 2. Product Photo Copywriter (Streaming + 768px/75% JPEG)

@Composable
private fun CopywriterTab(
    vm: CopywriterViewModel,
    aiReady: Boolean,
    topPadding: Dp,
    hazeState: HazeState
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
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Glass.CardFillSubtle)
                                        .border(1.dp, Glass.Stroke, RoundedCornerShape(12.dp))
                                        .padding(10.dp),
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

// ============================================================ 3. AI History Vault (Room Database)

@Composable
private fun AiVaultTab(
    vm: AiVaultViewModel,
    topPadding: Dp,
    hazeState: HazeState
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
            .hazeSource(hazeState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = topPadding, bottom = 114.dp)
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
