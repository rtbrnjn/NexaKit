package com.mediaforge.studio.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.ai.ImageAspectRatio
import com.mediaforge.studio.core.Actions
import com.mediaforge.studio.ui.components.BitmapImage
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.components.bringIntoViewOnFocus
import com.mediaforge.studio.ui.components.diagonalGlassBorder
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.ImageCreationViewModel
import dev.chrisbanes.haze.HazeState

@Composable
internal fun ImageCreationTab(
    vm: ImageCreationViewModel,
    online: Boolean,
    hasGeminiKey: Boolean,
    onOpenSettings: () -> Unit,
    topPadding: Dp,
    hazeState: HazeState?
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val prompt by vm.prompt.collectAsState()
    val negativePrompt by vm.negativePrompt.collectAsState()
    val aspectRatio by vm.aspectRatio.collectAsState()
    val bitmap by vm.generatedBitmap.collectAsState()
    val generatedResult by vm.generatedResult.collectAsState()
    val isGenerating by vm.isGenerating.collectAsState()
    val liveStatus by vm.liveStatus.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val canGenerate = online && hasGeminiKey && prompt.isNotBlank() && !isGenerating

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
        if (!hasGeminiKey) {
            GlassCard(Modifier.fillMaxWidth(), accent = Glass.Amber, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Key, null, tint = Glass.Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Memerlukan Google Gemini API Key", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                        Hint("Image Creation terhubung ke Gemini / Imagen REST API. Simpan Gemini API Key (AIza...) di Settings.")
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassButton("Settings", primary = false, compact = true, onClick = onOpenSettings)
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Image Creation (Gemini / Imagen)")
            Spacer(Modifier.height(4.dp))
            Hint("Tulis prompt sendiri atau kirim otomatis dari tab Prompt Generator, lalu pilih rasio gambar.")
            Spacer(Modifier.height(10.dp))

            QuickPasteResetRow(
                hasText = prompt.isNotBlank(),
                onPaste = { pasted -> vm.setPrompt(if (prompt.isBlank()) pasted else "$prompt $pasted") },
                onClear = { vm.setPrompt("") }
            )
            Spacer(Modifier.height(6.dp))
            GlassTextField(
                value = prompt,
                onValueChange = vm::setPrompt,
                label = "Prompt Gambar AI",
                placeholder = "A photorealistic cat wearing a sleek astronaut helmet on the moon, Earth glowing in visor...",
                minLines = 3,
                maxLines = 7,
                modifier = Modifier.bringIntoViewOnFocus(prompt.length)
            )

            Spacer(Modifier.height(10.dp))
            GlassTextField(
                value = negativePrompt,
                onValueChange = vm::setNegativePrompt,
                label = "Negative Prompt (Opsional)",
                placeholder = "blurry, watermark, distorted anatomy, low quality",
                minLines = 1,
                maxLines = 3,
                modifier = Modifier.bringIntoViewOnFocus(negativePrompt.length)
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("Rasio Gambar")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                options = ImageAspectRatio.entries,
                selected = aspectRatio,
                label = { it.label },
                onSelect = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.setAspectRatio(it)
                }
            )
        }

        if (isGenerating) {
            GlassButton(
                text = "Batalkan Generasi",
                icon = Icons.Rounded.StopCircle,
                primary = false,
                onClick = vm::stop,
                modifier = Modifier.fillMaxWidth()
            )
            GlassCard(Modifier.fillMaxWidth(), accent = Glass.Cyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Glass.Cyan)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(liveStatus ?: "Membuat gambar AI...", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                        Hint("Biasanya membutuhkan 5–15 detik tergantung antrean server Gemini/Imagen.")
                    }
                }
            }
        } else {
            GlassButton(
                text = "Generate Gambar AI",
                icon = Icons.Rounded.AutoAwesome,
                enabled = canGenerate,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.generate()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        bitmap?.let { img ->
            val frameRatio = (img.width.toFloat() / img.height.coerceAtLeast(1).toFloat()).coerceIn(0.55f, 1.85f)
            val frameShape = RoundedCornerShape(20.dp)
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Pratinjau Hasil", Modifier.weight(1f))
                    generatedResult?.let { meta ->
                        Text(
                            "${meta.modelId} • ${img.width}×${img.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(frameRatio)
                        .clip(frameShape)
                        .background(Glass.CardFillSubtle)
                        .border(1.dp, diagonalGlassBorder(isDark = Glass.IsDark, strong = true), frameShape)
                ) {
                    BitmapImage(
                        bitmap = img,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (!generatedResult?.caption.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Hint(generatedResult!!.caption)
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = "Simpan ke Galeri",
                        icon = Icons.Rounded.Download,
                        primary = true,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.saveToGallery()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Bagikan",
                        icon = Icons.Rounded.Share,
                        primary = false,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.shareImage { saved -> Actions.share(ctx, listOf(saved)) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
