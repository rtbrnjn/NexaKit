package com.mediaforge.studio.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaforge.studio.ai.AiEngine
import com.mediaforge.studio.ai.AiPreference
import com.mediaforge.studio.ai.AiRouting
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassChip
import com.mediaforge.studio.ui.components.LiquidGlassTopHeader
import com.mediaforge.studio.ui.components.LocalHazeState
import com.mediaforge.studio.ui.components.OfflineBadge
import com.mediaforge.studio.ui.components.OnlineBadge
import com.mediaforge.studio.ui.components.diagonalGlassBorder
import com.mediaforge.studio.ui.components.horizontalCarouselScroll
import com.mediaforge.studio.ui.components.liquidGlassChrome
import com.mediaforge.studio.ui.screens.ai.AiVaultTab
import com.mediaforge.studio.ui.screens.ai.CopywriterTab
import com.mediaforge.studio.ui.screens.ai.ImageCreationTab
import com.mediaforge.studio.ui.screens.ai.PdfAssistantTab
import com.mediaforge.studio.ui.screens.ai.PromptGeneratorTab
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.AiVaultViewModel
import com.mediaforge.studio.vm.CopywriterViewModel
import com.mediaforge.studio.vm.ImageCreationViewModel
import com.mediaforge.studio.vm.PdfAiViewModel
import com.mediaforge.studio.vm.PromptGeneratorViewModel
import com.mediaforge.studio.vm.SettingsViewModel
import dev.chrisbanes.haze.HazeState

enum class AiTool(val label: String, val icon: ImageVector) {
    PDF_CHAT("Chat PDF", Icons.Rounded.Description),
    PROMPT_MAKER("Prompt", Icons.Rounded.AutoFixHigh),
    IMAGE_CREATE("Image AI", Icons.Rounded.Brush),
    COPYWRITER("Copywriter", Icons.Rounded.Campaign),
    VAULT("AI Vault", Icons.Rounded.Bookmarks)
}

@Composable
fun GeminiAiScreen(
    settingsVm: SettingsViewModel,
    onOpenVault: () -> Unit,
    copyVm: CopywriterViewModel = viewModel(),
    pdfAiVm: PdfAiViewModel = viewModel(),
    promptVm: PromptGeneratorViewModel = viewModel(),
    imageVm: ImageCreationViewModel = viewModel(),
    vaultVm: AiVaultViewModel = viewModel(),
    requestedTool: AiTool? = null,
    onToolHandled: () -> Unit = {}
) {
    val online by settingsVm.online.collectAsState()
    val geminiKey by settingsVm.savedKey.collectAsState()
    val groqKey by settingsVm.savedGroqKey.collectAsState()
    val model by settingsVm.model.collectAsState()
    val aiPref by settingsVm.aiPreference.collectAsState()
    val activePdfEngine by pdfAiVm.activeEngine.collectAsState()
    val activeCopyEngine by copyVm.activeEngine.collectAsState()
    val activePromptEngine by promptVm.activeEngine.collectAsState()
    var active by rememberSaveable { mutableStateOf(AiTool.PDF_CHAT) }

    val chromeHaze = LocalHazeState.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val topPadding = with(LocalDensity.current) { headerHeightPx.toDp() } + 10.dp

    LaunchedEffect(requestedTool) {
        if (requestedTool != null) {
            active = requestedTool
            onToolHandled()
        }
    }

    val hasAnyKey = groqKey.isNotBlank() || geminiKey.isNotBlank()
    val aiReady = online && hasAnyKey
    val primaryEngine = AiRouting.resolvePrimaryEngine(aiPref, groqKey.isNotBlank(), geminiKey.isNotBlank())
    val displayedEngine = when (active) {
        AiTool.PDF_CHAT -> activePdfEngine ?: primaryEngine
        AiTool.PROMPT_MAKER -> activePromptEngine ?: primaryEngine
        AiTool.IMAGE_CREATE -> if (geminiKey.isNotBlank()) AiEngine.GEMINI else primaryEngine
        AiTool.COPYWRITER -> activeCopyEngine ?: primaryEngine
        AiTool.VAULT -> primaryEngine
    }
    val engineBadge = if (!online || !hasAnyKey) {
        "Offline Mode"
    } else if (active == AiTool.IMAGE_CREATE) {
        if (geminiKey.isNotBlank()) "Gemini · Image AI" else "Need Gemini Key"
    } else {
        AiRouting.badgeLabel(displayedEngine, model, isVision = active == AiTool.COPYWRITER)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = active,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.985f, animationSpec = tween(200)))
                    .togetherWith(fadeOut(tween(160)))
            },
            label = "aiSubTab",
            modifier = Modifier.fillMaxSize()
        ) { tool ->
            when (tool) {
                AiTool.PDF_CHAT -> PdfAssistantTab(pdfAiVm, aiReady, topPadding, chromeHaze)
                AiTool.PROMPT_MAKER -> PromptGeneratorTab(
                    vm = promptVm,
                    aiReady = aiReady,
                    topPadding = topPadding,
                    hazeState = chromeHaze,
                    onSendToImageCreation = { mainPrompt, negPrompt, ratio ->
                        imageVm.applyFromPromptGenerator(mainPrompt, negPrompt, ratio)
                        active = AiTool.IMAGE_CREATE
                    }
                )
                AiTool.IMAGE_CREATE -> ImageCreationTab(
                    vm = imageVm,
                    online = online,
                    hasGeminiKey = geminiKey.isNotBlank(),
                    onOpenSettings = onOpenVault,
                    topPadding = topPadding,
                    hazeState = chromeHaze
                )
                AiTool.COPYWRITER -> CopywriterTab(copyVm, aiReady, topPadding, chromeHaze)
                AiTool.VAULT -> AiVaultTab(vaultVm, topPadding, chromeHaze)
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
            LiquidGlassTopHeader(
                title = "DocPix · AI Studio",
                subtitle = if (!aiReady) "Add Groq or Gemini API key in Settings"
                else if (active == AiTool.IMAGE_CREATE) "Gemini / Imagen Image Generation"
                else if (groqKey.isNotBlank() && geminiKey.isNotBlank()) "$engineBadge • Auto-Fallback"
                else "$engineBadge • SSE Streaming",
                hazeState = chromeHaze,
                trailing = {
                    if (aiReady) {
                        OnlineBadge(
                            label = engineBadge,
                            accent = if (displayedEngine == AiEngine.GROQ) Glass.Emerald else Glass.Cyan
                        )
                    } else {
                        OfflineBadge(compact = true)
                    }
                }
            )

            if (!online) {
                GlassCard(Modifier.fillMaxWidth(), accent = Glass.Amber, contentPadding = PaddingValues(12.dp)) {
                    Text(
                        "Offline Mode — All Media & PDF Tools Ready. Connect to the internet for live Groq & Gemini streaming.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.TextPrimary
                    )
                }
            } else if (!hasAnyKey) {
                GlassCard(Modifier.fillMaxWidth(), accent = Glass.Amber, contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Key, null, tint = Glass.Amber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Add a Groq (gsk_...) or Gemini (AIza...) key in Settings to enable AI Studio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        GlassButton("Settings", primary = false, compact = true, onClick = onOpenVault)
                    }
                }
            } else if (active != AiTool.IMAGE_CREATE && active != AiTool.VAULT) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalCarouselScroll(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Engine", style = MaterialTheme.typography.labelSmall, color = Glass.TextDim)
                    AiPreference.entries.forEach { pref ->
                        val chipColor = when (pref) {
                            AiPreference.GROQ -> Glass.Emerald
                            AiPreference.GEMINI -> Glass.Cyan
                            AiPreference.AUTO -> if (primaryEngine == AiEngine.GROQ) Glass.Emerald else Glass.Cyan
                        }
                        GlassChip(
                            text = if (pref == AiPreference.GROQ) "Groq LPU" else pref.label,
                            selected = aiPref == pref,
                            onClick = { settingsVm.setAiPreference(pref) },
                            color = chipColor
                        )
                    }
                }
            }

            AiStudioPillSelector(
                items = AiTool.entries,
                selected = active,
                onSelect = { active = it },
                hazeState = chromeHaze
            )
        }
    }
}

@Composable
private fun AiStudioPillSelector(
    items: List<AiTool>,
    selected: AiTool,
    onSelect: (AiTool) -> Unit,
    hazeState: HazeState?
) {
    val isDark = Glass.IsDark
    val haptics = LocalHapticFeedback.current
    val outerShape = RoundedCornerShape(26.dp)
    val pillShape = RoundedCornerShape(20.dp)
    val activeFill = remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.07f))
            )
        } else {
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.65f))
            )
        }
    }
    val activeBorder = diagonalGlassBorder(isDark = isDark, strong = true)

    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = outerShape,
                hazeState = hazeState,
                blurRadius = 14.dp,
                shadowElevation = 6.dp
            )
            .horizontalCarouselScroll()
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .clip(pillShape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(activeFill)
                                .border(1.dp, activeBorder, pillShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(interactionSource = interaction, indication = null) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(item)
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (isSelected) (if (isDark) Glass.TextPrimary else Glass.Cyan) else Glass.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = item.label,
                    color = if (isSelected) Glass.TextPrimary else Glass.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
