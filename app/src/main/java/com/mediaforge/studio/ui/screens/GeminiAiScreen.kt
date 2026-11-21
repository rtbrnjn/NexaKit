package com.mediaforge.studio.ui.screens

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
import androidx.compose.material.icons.rounded.Bookmarks
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
import com.mediaforge.studio.ui.components.GlassSegmentedBar
import com.mediaforge.studio.ui.components.LiquidGlassTopHeader
import com.mediaforge.studio.ui.components.LocalHazeState
import com.mediaforge.studio.ui.components.OfflineBadge
import com.mediaforge.studio.ui.components.OnlineBadge
import com.mediaforge.studio.ui.screens.ai.AiVaultTab
import com.mediaforge.studio.ui.screens.ai.CopywriterTab
import com.mediaforge.studio.ui.screens.ai.PdfAssistantTab
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.AiVaultViewModel
import com.mediaforge.studio.vm.CopywriterViewModel
import com.mediaforge.studio.vm.PdfAiViewModel
import com.mediaforge.studio.vm.SettingsViewModel

enum class AiTool(val label: String, val icon: ImageVector) {
    PDF_CHAT("PDF AI", Icons.Rounded.Description),
    COPYWRITER("Copywriter", Icons.Rounded.Campaign),
    VAULT("AI Vault", Icons.Rounded.Bookmarks)
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
    val geminiKey by settingsVm.savedKey.collectAsState()
    val groqKey by settingsVm.savedGroqKey.collectAsState()
    val model by settingsVm.model.collectAsState()
    val aiPref by settingsVm.aiPreference.collectAsState()
    val activePdfEngine by pdfAiVm.activeEngine.collectAsState()
    val activeCopyEngine by copyVm.activeEngine.collectAsState()
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
        AiTool.COPYWRITER -> activeCopyEngine ?: primaryEngine
        AiTool.VAULT -> primaryEngine
    }
    val engineBadge = if (!online || !hasAnyKey) {
        "Offline Mode"
    } else {
        AiRouting.badgeLabel(displayedEngine, model, isVision = active == AiTool.COPYWRITER)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = active,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "aiSubTab",
            modifier = Modifier.fillMaxSize()
        ) { tool ->
            when (tool) {
                AiTool.PDF_CHAT -> PdfAssistantTab(pdfAiVm, aiReady, topPadding, chromeHaze)
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
            } else {
                Row(
                    Modifier.fillMaxWidth(),
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
