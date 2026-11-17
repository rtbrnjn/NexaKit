package com.mediaforge.studio.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PictureAsPdf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaforge.studio.ai.CopyLanguage
import com.mediaforge.studio.ai.DocTask
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.ui.components.BitmapImage
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.EmptyPicker
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassChip
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.OfflineBadge
import com.mediaforge.studio.ui.components.OnlineBadge
import com.mediaforge.studio.ui.components.ProgressOverlay
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.StatusBanner
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.CopywriterViewModel
import com.mediaforge.studio.vm.PdfAiViewModel
import com.mediaforge.studio.vm.SettingsViewModel

private enum class AiTool(val label: String, val icon: ImageVector) {
    PDF_CHAT("PDF Assistant", Icons.Rounded.Description),
    COPYWRITER("Photo Copywriter", Icons.Rounded.Campaign)
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
    pdfAiVm: PdfAiViewModel = viewModel()
) {
    val online by settingsVm.online.collectAsState()
    val apiKey by settingsVm.savedKey.collectAsState()
    val model by settingsVm.model.collectAsState()
    var active by rememberSaveable { mutableStateOf(AiTool.PDF_CHAT) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gemini AI", style = MaterialTheme.typography.headlineMedium, color = Glass.TextPrimary)
                    Text("Smart Document Q&A & Copywriter • $model", style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
                }
                if (online && apiKey.isNotBlank()) OnlineBadge() else OfflineBadge(compact = true)
            }

            if (!online) {
                GlassCard(Modifier.fillMaxWidth(), accent = Glass.Amber, contentPadding = PaddingValues(12.dp)) {
                    Text(
                        "Offline Mode — All Media & PDF Tools Ready. Connect to the internet when you want Gemini AI analysis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.TextPrimary
                    )
                }
            } else if (apiKey.isBlank()) {
                GlassCard(Modifier.fillMaxWidth(), accent = Glass.Amber, contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Key, null, tint = Glass.Amber, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Offline Mode — All Media & PDF Tools Ready. Add your Gemini API key in Settings to enable AI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        GlassButton("Settings", primary = false, compact = true, onClick = onOpenVault)
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Glass.CardFillSubtle)
                    .border(1.dp, Glass.Stroke, RoundedCornerShape(18.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AiTool.entries.forEach { tool ->
                    val selected = tool == active
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (selected) Modifier
                                    .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.18f else 0.14f))
                                    .border(1.dp, Glass.Cyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                else Modifier
                            )
                            .clickable { active = tool }
                            .padding(vertical = 9.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(tool.icon, null, tint = if (selected) Glass.Cyan else Glass.TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tool.label,
                            color = if (selected) Glass.TextPrimary else Glass.TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        val aiReady = online && apiKey.isNotBlank()
        when (active) {
            AiTool.PDF_CHAT -> PdfAssistantTab(pdfAiVm, aiReady, Modifier.weight(1f))
            AiTool.COPYWRITER -> CopywriterTab(copyVm, aiReady, Modifier.weight(1f))
        }
    }
}

// ============================================================ 1. PDF Summariser & Q&A Chat

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfAssistantTab(vm: PdfAiViewModel, aiReady: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val source by vm.source.collectAsState()
    val extractInfo by vm.extractInfo.collectAsState()
    val messages by vm.messages.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    var question by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusBanner(message, vm::dismissMessage)

            val src = source
            if (src == null) {
                EmptyPicker(
                    title = "Open a PDF Document",
                    subtitle = "Extracts text locally before querying Gemini",
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
                            Text("${src.pageCount}p • ${FileUtils.formatBytes(src.sizeBytes)} • $extractInfo", style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary, maxLines = 2)
                        }
                        if (messages.isNotEmpty()) {
                            GlassIconButton(Icons.Rounded.DeleteSweep, "Clear chat", vm::clearChat)
                        }
                        GlassButton("Open", primary = false, compact = true, onClick = { picker.launch(arrayOf("application/pdf")) })
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocTask.entries.forEach { task ->
                        GlassChip(
                            text = task.label,
                            selected = false,
                            onClick = { if (aiReady) vm.runDocTask(task) },
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
                                Hint("Tap Summarize / Key Takeaways / Translate above or ask a question below.")
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
                                        .widthIn(max = 310.dp)
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
                                    Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                                    if (!msg.fromUser) {
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
                    Box(
                        Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                if (aiReady && question.isNotBlank()) Glass.Cyan else Glass.CardFill
                            )
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
        ProgressOverlay(progress)
    }
}

// ============================================================ 2. Product Photo Copywriter

@Composable
private fun CopywriterTab(vm: CopywriterViewModel, aiReady: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val uri by vm.uri.collectAsState()
    val preview by vm.preview.collectAsState()
    val language by vm.language.collectAsState()
    val notes by vm.notes.collectAsState()
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
        modifier = modifier
    ) {
        if (uri == null) {
            EmptyPicker(
                title = "Select a Product Photo",
                subtitle = "Generate title, 3 short review hooks & ready-to-copy caption",
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
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Product Photo Ready", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                        Hint("Downscaled locally before sending to Gemini Vision.")
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
                placeholder = "e.g. Matte finish, ceramic, minimalist desk setup",
                minLines = 2,
                maxLines = 4
            )
        }

        GlassButton(
            text = "Generate Marketing Copy",
            icon = Icons.Rounded.AutoAwesome,
            enabled = uri != null && aiReady,
            onClick = vm::generate,
            modifier = Modifier.fillMaxWidth()
        )

        result?.let { copy ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Product Title", Modifier.weight(1f))
                    GlassIconButton(Icons.Rounded.ContentCopy, "Copy title", { copyToClipboard(ctx, "Product Title", copy.title) }, tint = Glass.Cyan)
                }
                Text(copy.title, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("3 Short Review Hooks", Modifier.weight(1f))
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

            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Social / Marketplace Caption", Modifier.weight(1f))
                    GlassIconButton(
                        Icons.Rounded.ContentCopy,
                        "Copy caption",
                        { copyToClipboard(ctx, "Caption & Hashtags", copy.captionWithTags) },
                        tint = Glass.Cyan
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(copy.caption, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                Spacer(Modifier.height(10.dp))
                Text(copy.hashtagLine, style = MaterialTheme.typography.bodySmall, color = Glass.Cyan)
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
