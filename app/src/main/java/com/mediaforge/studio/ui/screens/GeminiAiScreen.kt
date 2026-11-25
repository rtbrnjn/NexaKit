package com.mediaforge.studio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bookmarks
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaforge.studio.ai.AiEngine
import com.mediaforge.studio.ai.AiPreference
import com.mediaforge.studio.ai.AiRouting
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassChip
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassSegmentedBar
import com.mediaforge.studio.ui.components.LiquidGlassTopHeader
import com.mediaforge.studio.ui.components.LocalHazeState
import com.mediaforge.studio.ui.components.OfflineBadge
import com.mediaforge.studio.ui.components.OnlineBadge
import com.mediaforge.studio.ui.components.horizontalCarouselScroll
import com.mediaforge.studio.ui.screens.ai.AiVaultTab
import com.mediaforge.studio.ui.screens.ai.CopywriterTab
import com.mediaforge.studio.ui.screens.ai.PdfAssistantTab
import com.mediaforge.studio.ui.screens.ai.PromptGeneratorTab
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.AiVaultViewModel
import com.mediaforge.studio.vm.CopywriterViewModel
import com.mediaforge.studio.vm.PdfAiViewModel
import com.mediaforge.studio.vm.PromptGeneratorViewModel
import com.mediaforge.studio.vm.SettingsViewModel

enum class AiTool(val label: String, val icon: ImageVector) {
    PDF_CHAT("Chat & Tanya PDF", Icons.Rounded.Description),
    PROMPT_MAKER("Prompt Generator", Icons.Rounded.AutoFixHigh)
}

private enum class AiOverlayView { NONE, VAULT, COPYWRITER }

@Composable
fun GeminiAiScreen(
    settingsVm: SettingsViewModel,
    onOpenVault: () -> Unit,
    copyVm: CopywriterViewModel = viewModel(),
    pdfAiVm: PdfAiViewModel = viewModel(),
    promptVm: PromptGeneratorViewModel = viewModel(),
    vaultVm: AiVaultViewModel = viewModel(),
    requestedTool: AiTool? = null,
    onToolHandled: () -> Unit = {},
    requestedShareCopywriter: Boolean = false,
    onShareCopywriterHandled: () -> Unit = {},
    isTabActive: Boolean = true
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
    var overlayView by rememberSaveable { mutableStateOf(AiOverlayView.NONE) }

    val chromeHaze = LocalHazeState.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val topPadding = with(LocalDensity.current) { headerHeightPx.toDp() } + 8.dp

    LaunchedEffect(requestedTool) {
        if (requestedTool != null) {
            overlayView = AiOverlayView.NONE
            active = requestedTool
            onToolHandled()
        }
    }

    LaunchedEffect(requestedShareCopywriter) {
        if (requestedShareCopywriter) {
            overlayView = AiOverlayView.COPYWRITER
            onShareCopywriterHandled()
        }
    }

    BackHandler(enabled = isTabActive && overlayView != AiOverlayView.NONE) {
        overlayView = AiOverlayView.NONE
    }

    val hasAnyKey = groqKey.isNotBlank() || geminiKey.isNotBlank()
    val aiReady = online && hasAnyKey
    val primaryEngine = AiRouting.resolvePrimaryEngine(aiPref, groqKey.isNotBlank(), geminiKey.isNotBlank())
    val displayedEngine = when {
        overlayView == AiOverlayView.COPYWRITER -> activeCopyEngine ?: primaryEngine
        overlayView == AiOverlayView.VAULT -> primaryEngine
        active == AiTool.PDF_CHAT -> activePdfEngine ?: primaryEngine
        else -> activePromptEngine ?: primaryEngine
    }
    val engineBadge = if (!online || !hasAnyKey) {
        "Offline Mode"
    } else {
        AiRouting.badgeLabel(displayedEngine, model, isVision = overlayView == AiOverlayView.COPYWRITER)
    }

    val contentKey: Any = if (overlayView != AiOverlayView.NONE) overlayView else active

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = contentKey,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.985f, animationSpec = tween(200)))
                    .togetherWith(fadeOut(tween(160)))
            },
            label = "aiSubTab",
            modifier = Modifier.fillMaxSize()
        ) { target ->
            when (target) {
                AiOverlayView.VAULT -> AiVaultTab(vaultVm, topPadding, chromeHaze)
                AiOverlayView.COPYWRITER -> CopywriterTab(copyVm, aiReady, topPadding, chromeHaze)
                AiTool.PROMPT_MAKER -> PromptGeneratorTab(
                    vm = promptVm,
                    aiReady = aiReady,
                    topPadding = topPadding,
                    hazeState = chromeHaze
                )
                else -> PdfAssistantTab(pdfAiVm, aiReady, topPadding, chromeHaze)
            }
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquidGlassTopHeader(
                title = when (overlayView) {
                    AiOverlayView.VAULT -> "DocPix · AI Vault"
                    AiOverlayView.COPYWRITER -> "DocPix · Copywriter"
                    AiOverlayView.NONE -> "DocPix · AI Studio"
                },
                subtitle = if (!aiReady) "Tambahkan Groq atau Gemini API Key di Settings"
                else if (groqKey.isNotBlank() && geminiKey.isNotBlank()) "$engineBadge • Auto-Fallback"
                else "$engineBadge • SSE Streaming",
                hazeState = chromeHaze,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (overlayView != AiOverlayView.NONE) {
                            GlassIconButton(
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                description = "Kembali ke AI Studio",
                                onClick = { overlayView = AiOverlayView.NONE },
                                tint = Glass.Cyan
                            )
                        } else {
                            GlassIconButton(
                                icon = Icons.Rounded.Bookmarks,
                                description = "AI Vault",
                                onClick = { overlayView = AiOverlayView.VAULT },
                                tint = Glass.TextSecondary
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        if (aiReady) {
                            OnlineBadge(
                                label = engineBadge,
                                accent = if (displayedEngine == AiEngine.GROQ) Glass.Emerald else Glass.Cyan
                            )
                        } else {
                            OfflineBadge(compact = true)
                        }
                    }
                }
            )

            if (!online) {
                GlassCard(Modifier.fillMaxWidth(), accent = Glass.Amber, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        "Mode Offline aktif. Sambungkan internet untuk AI Studio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.TextPrimary
                    )
                }
            } else if (!hasAnyKey) {
                GlassCard(Modifier.fillMaxWidth(), accent = Glass.Amber, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Key, null, tint = Glass.Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tambahkan Groq atau Gemini API Key di Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        GlassButton("Settings", primary = false, compact = true, onClick = onOpenVault)
                    }
                }
            } else if (overlayView != AiOverlayView.VAULT) {
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

            if (overlayView == AiOverlayView.NONE) {
                GlassSegmentedBar(
                    items = AiTool.entries,
                    selected = active,
                    label = { it.label },
                    icon = { it.icon },
                    onSelect = { active = it },
                    hazeState = chromeHaze
                )
            }
        }
    }
}
