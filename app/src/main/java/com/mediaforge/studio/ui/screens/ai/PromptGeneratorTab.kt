package com.mediaforge.studio.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.ai.ImageAspectRatio
import com.mediaforge.studio.ai.LightingPreset
import com.mediaforge.studio.ai.PromptAspectRatio
import com.mediaforge.studio.ai.PromptTarget
import com.mediaforge.studio.ai.VisualStyle
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.components.bringIntoViewOnFocus
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.PromptGeneratorViewModel
import dev.chrisbanes.haze.HazeState

@Composable
internal fun PromptGeneratorTab(
    vm: PromptGeneratorViewModel,
    aiReady: Boolean,
    topPadding: Dp,
    hazeState: HazeState?,
    onSendToImageCreation: (prompt: String, negativePrompt: String, ratio: ImageAspectRatio) -> Unit
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val idea by vm.idea.collectAsState()
    val target by vm.target.collectAsState()
    val style by vm.style.collectAsState()
    val lighting by vm.lighting.collectAsState()
    val aspectRatio by vm.aspectRatio.collectAsState()
    val streamingText by vm.streamingText.collectAsState()
    val isStreaming by vm.isStreaming.collectAsState()
    val liveStatus by vm.liveStatus.collectAsState()
    val result by vm.result.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    ToolPage(
        title = null,
        subtitle = null,
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        topPadding = topPadding,
        hazeState = hazeState,
        imeAware = true
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Prompt Generator (Prompt Maker)")
            Spacer(Modifier.height(4.dp))
            Hint("Ubah ide singkat menjadi prompt Bahasa Inggris detail + Negative Prompt untuk Midjourney, Flux, Imagen, atau AI Video.")
            Spacer(Modifier.height(12.dp))

            SectionLabel("Target Generasi")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                options = PromptTarget.entries,
                selected = target,
                label = { it.label },
                onSelect = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.setTarget(it)
                }
            )

            Spacer(Modifier.height(12.dp))
            QuickPasteResetRow(
                hasText = idea.isNotBlank(),
                onPaste = { pasted -> vm.setIdea(if (idea.isBlank()) pasted else "$idea $pasted") },
                onClear = { vm.setIdea("") }
            )
            Spacer(Modifier.height(6.dp))
            GlassTextField(
                value = idea,
                onValueChange = vm::setIdea,
                label = "Ide Singkat Anda",
                placeholder = "mis. kucing pakai helm astronot di bulan, refleksi bumi di visor",
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.bringIntoViewOnFocus(idea.length)
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("Gaya Visual")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                options = VisualStyle.entries,
                selected = style,
                label = { it.label },
                onSelect = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.setStyle(it)
                }
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("Pencahayaan")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                options = LightingPreset.entries,
                selected = lighting,
                label = { it.label },
                onSelect = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.setLighting(it)
                }
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("Rasio Aspek")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                options = PromptAspectRatio.entries,
                selected = aspectRatio,
                label = { it.label },
                onSelect = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.setAspectRatio(it)
                }
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
                text = "Buat Prompt Profesional",
                icon = Icons.Rounded.AutoFixHigh,
                enabled = idea.isNotBlank() && aiReady,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.generate()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isStreaming) {
            GlassCard(Modifier.fillMaxWidth(), accent = Glass.Cyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Glass.Cyan)
                    Spacer(Modifier.width(8.dp))
                    SectionLabel(liveStatus ?: "Merangkai prompt detail...")
                }
                if (streamingText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(streamingText, style = MaterialTheme.typography.bodySmall, color = Glass.TextPrimary)
                }
            }
        }

        if (!isStreaming) {
            result?.let { generated ->
                if (generated.mainPrompt.isNotBlank()) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Detailed English Prompt", Modifier.weight(1f))
                            GlassIconButton(
                                icon = Icons.Rounded.ContentCopy,
                                description = "Salin Prompt",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    copyToClipboard(ctx, "AI Prompt", generated.mainPrompt)
                                },
                                tint = Glass.Cyan
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(generated.mainPrompt, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                    }
                }

                if (generated.negativePrompt.isNotBlank()) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Negative Prompt", Modifier.weight(1f))
                            GlassIconButton(
                                icon = Icons.Rounded.ContentCopy,
                                description = "Salin Negative Prompt",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    copyToClipboard(ctx, "Negative Prompt", generated.negativePrompt)
                                },
                                tint = Glass.Amber
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(generated.negativePrompt, style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
                    }
                }

                if (generated.tips.isNotBlank()) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        SectionLabel("Tips Cepat")
                        Spacer(Modifier.height(4.dp))
                        Hint(generated.tips)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        text = "Salin Prompt Lengkap",
                        icon = Icons.Rounded.ContentCopy,
                        primary = false,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            copyToClipboard(ctx, "Full Prompt", generated.asCopyableText())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    GlassButton(
                        text = "Kirim ke Image Creation",
                        icon = Icons.Rounded.Brush,
                        primary = true,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSendToImageCreation(
                                generated.mainPrompt,
                                generated.negativePrompt,
                                aspectRatio.imageRatio
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
