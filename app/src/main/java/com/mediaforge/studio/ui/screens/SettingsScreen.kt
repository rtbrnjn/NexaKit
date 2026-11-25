package com.mediaforge.studio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.BuildConfig
import com.mediaforge.studio.R
import com.mediaforge.studio.ai.AiEngine
import com.mediaforge.studio.ai.AiPreference
import com.mediaforge.studio.ai.AiRouting
import com.mediaforge.studio.core.Actions
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.OutputKind
import com.mediaforge.studio.core.Permissions
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.pdf.PdfPageMode
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassSwitch
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.LabeledSlider
import com.mediaforge.studio.ui.components.LiquidGlassTopHeader
import com.mediaforge.studio.ui.components.LocalHazeState
import com.mediaforge.studio.ui.components.OfflineBadge
import com.mediaforge.studio.ui.components.OnlineBadge
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.UriImage
import com.mediaforge.studio.ui.screens.settings.ApiKeyTutorialSheet
import com.mediaforge.studio.ui.screens.settings.ApiKeyVaultSection
import com.mediaforge.studio.ui.screens.settings.FeedbackReportSheet
import com.mediaforge.studio.ui.screens.settings.ModelDropdownSelector
import com.mediaforge.studio.ui.screens.settings.ThemePillSelector
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.SettingsViewModel
import dev.chrisbanes.haze.hazeSource

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val ctx = LocalContext.current
    val chromeHaze = LocalHazeState.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val topPadding = with(LocalDensity.current) { headerHeightPx.toDp() } + 10.dp

    val themeMode by vm.themeMode.collectAsState()
    val aiPref by vm.aiPreference.collectAsState()

    val groqKeyInput by vm.groqKeyInput.collectAsState()
    val savedGroqKey by vm.savedGroqKey.collectAsState()
    val groqConnection by vm.groqConnection.collectAsState()
    val groqStatus by vm.groqStatus.collectAsState()

    val keyInput by vm.keyInput.collectAsState()
    val savedGeminiKey by vm.savedKey.collectAsState()
    val connection by vm.connection.collectAsState()
    val status by vm.status.collectAsState()

    val model by vm.model.collectAsState()
    val autoShow by vm.autoShow.collectAsState()
    val defaultQuality by vm.defaultQuality.collectAsState()
    val defaultPdfMode by vm.defaultPdfPageMode.collectAsState()
    val autoStripExif by vm.autoStripExif.collectAsState()
    val totalSaved by vm.totalBytesSaved.collectAsState()
    val cacheBytes by vm.cacheBytes.collectAsState()
    val history by vm.history.collectAsState()
    val online by vm.online.collectAsState()
    val checkingUpdate by vm.checkingUpdate.collectAsState()
    val availableUpdate by vm.availableUpdate.collectAsState()
    val updateStatus by vm.updateStatus.collectAsState()

    var showGuideSheet by remember { mutableStateOf(false) }
    var showFeedbackSheet by remember { mutableStateOf(false) }
    var permsGranted by remember { mutableStateOf(Permissions.granted(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permsGranted = Permissions.granted(ctx)
    }

    LaunchedEffect(Unit) { vm.refreshCacheSize() }

    val primaryEngine = AiRouting.resolvePrimaryEngine(aiPref, savedGroqKey.isNotBlank(), savedGeminiKey.isNotBlank())
    val headerReady = online && primaryEngine != null
    val headerBadgeText = when {
        !online -> "Offline Mode"
        primaryEngine == AiEngine.GROQ -> "Groq LPU Ready"
        primaryEngine == AiEngine.GEMINI -> "Gemini Online"
        else -> "No Key Yet"
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .let { if (chromeHaze != null) it.hazeSource(chromeHaze, zIndex = 1f) else it }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = topPadding, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ------------------------------------------------------------------ 1. Appearance (Sliding Liquid Pill)
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Appearance")
                Spacer(Modifier.height(10.dp))
                ThemePillSelector(themeMode = themeMode, onSelect = vm::setThemeMode)
                Spacer(Modifier.height(8.dp))
                Hint("Liquid Glass adapts frosted transparency across Light & Dark themes.")
            }

            // ------------------------------------------------------------------ 2. Dual-Engine AI Configuration (Groq + Gemini)
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Dual-Engine AI Vault", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Hint("API keys are encrypted locally on this device via Android Keystore.")
                Spacer(Modifier.height(14.dp))

                SectionLabel("Primary AI Engine")
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = AiPreference.entries,
                    selected = aiPref,
                    label = { it.label },
                    onSelect = vm::setAiPreference
                )
                Spacer(Modifier.height(6.dp))
                Hint("Auto uses Groq LPU (ultra-fast) when a Groq key is saved, and switches seamlessly to your backup key if 503/rate-limit occurs.")

                Spacer(Modifier.height(16.dp))
                ApiKeyVaultSection(
                    title = "1. Groq API Key (Ultra-Fast LPU)",
                    subtitle = "PDF: llama-3.3-70b-versatile • Vision: llama-4-scout-17b",
                    fieldLabel = "Groq API Key",
                    placeholder = "gsk_…",
                    keyInput = groqKeyInput,
                    onKeyChanged = vm::onGroqKeyChanged,
                    onSave = vm::saveGroqKey,
                    onVerify = vm::testGroqConnection,
                    onDelete = vm::clearGroqKey,
                    connection = groqConnection,
                    statusText = groqStatus,
                    verifiedUnit = "Groq",
                    accent = Glass.Emerald
                )

                Spacer(Modifier.height(18.dp))
                ApiKeyVaultSection(
                    title = "2. Google Gemini API Key (Backup / Vision)",
                    subtitle = "Default: gemini-3.8-flash / gemini-3.5-flash-lite",
                    fieldLabel = "Google Gemini API Key",
                    placeholder = "AIza…",
                    keyInput = keyInput,
                    onKeyChanged = vm::onKeyChanged,
                    onSave = vm::saveKey,
                    onVerify = vm::testConnection,
                    onDelete = vm::clearKey,
                    connection = connection,
                    statusText = status,
                    verifiedUnit = "Gemini",
                    accent = Glass.Cyan
                )

                Spacer(Modifier.height(14.dp))
                SectionLabel("Gemini Model Selector")
                Spacer(Modifier.height(8.dp))
                ModelDropdownSelector(selectedModel = model, onSelectModel = vm::setModel)
            }

            // ------------------------------------------------------------------ Interactive API Key Tutorial Trigger
            GlassCard(
                Modifier.fillMaxWidth(),
                accent = Glass.Cyan,
                onClick = { showGuideSheet = true },
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.18f else 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.TipsAndUpdates, null, tint = Glass.Cyan, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cara Mendapatkan API Key Gratis?", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        Hint("Panduan cepat daftar Groq (gsk_...) & Gemini (AIza...) + link langsung")
                    }
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open guide", tint = Glass.Cyan, modifier = Modifier.size(18.dp))
                }
            }

            // ------------------------------------------------------------------ 3. Default Export Preferences
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Default Export Preferences")
                Spacer(Modifier.height(10.dp))
                LabeledSlider(
                    label = "Default Image Compression Quality",
                    valueText = "$defaultQuality%",
                    value = defaultQuality.toFloat(),
                    range = 10f..95f,
                    onChange = { vm.setDefaultQuality(it.toInt()) }
                )
                SectionLabel("Default PDF Page Size")
                Spacer(Modifier.height(8.dp))
                ChoiceRow(PdfPageMode.entries, defaultPdfMode, { it.label }, vm::setDefaultPdfPageMode)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-Strip EXIF Metadata", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                        Hint("Removes GPS & device metadata by default")
                    }
                    GlassSwitch(autoStripExif, vm::setAutoStripExif)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Open / Share Action Sheet", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                        Hint("Show quick actions automatically after export")
                    }
                    GlassSwitch(autoShow, vm::setAutoShow)
                }
            }

            // ------------------------------------------------------------------ 4. Storage, Cache & Folders
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Storage & Cache")
                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Glass.Emerald.copy(alpha = if (Glass.IsDark) 0.12f else 0.08f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Savings, null, tint = Glass.Emerald, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Total Storage Saved", style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
                        Text(FileUtils.formatBytes(totalSaved), style = MaterialTheme.typography.titleLarge, color = Glass.Emerald)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Glass.CardFillSubtle)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AutoDelete, null, tint = Glass.Cyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Temporary Cache", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                        Text(FileUtils.formatBytes(cacheBytes), style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
                    }
                    GlassButton("Clear", primary = false, compact = true, enabled = cacheBytes > 0, onClick = vm::clearCache)
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (permsGranted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                        null,
                        tint = if (permsGranted) Glass.Emerald else Glass.Amber,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (permsGranted) "Media Permission Granted" else "Media Permission Needed", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                        Hint("Used for reading gallery images & saving legacy exports")
                    }
                    if (!permsGranted) {
                        GlassButton("Grant", compact = true, primary = false, onClick = { permLauncher.launch(Permissions.required()) })
                    }
                }

                Spacer(Modifier.height(14.dp))
                SectionLabel("Output Folders")
                Spacer(Modifier.height(8.dp))
                val folders = listOf(
                    "Images, Mockups & Collages" to Storage.folderPath(OutputKind.IMAGE),
                    "PDF Page Exports" to Storage.folderPath(OutputKind.IMAGE, "PDF_Export"),
                    "PDF & Word Documents" to Storage.folderPath(OutputKind.DOCUMENT)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.forEach { (label, path) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Glass.CardFillSubtle)
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.FolderOpen, null, tint = Glass.Cyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                                Text(path, style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
                            }
                            GlassButton("Open", primary = false, compact = true, onClick = { Actions.openFolder(ctx, path) })
                        }
                    }
                }
            }

            // ------------------------------------------------------------------ 5. Export History
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Export History (${history.size})", Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    GlassButton("Clear List", icon = Icons.Rounded.DeleteSweep, primary = false, compact = true, onClick = vm::clearHistory)
                }
            }

            if (history.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Text("No exports yet", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Hint("Files created in Media Lab or PDF Suite appear here for quick access.")
                }
            } else {
                history.forEach { f ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = { Actions.open(ctx, f) }, contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (f.isImage) {
                                UriImage(f.uri, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), maxDim = 180)
                            } else {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.16f else 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (f.isZip) Icons.Rounded.FolderZip else Icons.Rounded.Description,
                                        null,
                                        tint = Glass.Cyan
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${f.tool} • ${FileUtils.formatBytes(f.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = Glass.Cyan)
                                Text("${f.folder} • ${FileUtils.formatDate(f.timestamp)}", style = MaterialTheme.typography.labelSmall, color = Glass.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            GlassIconButton(Icons.AutoMirrored.Rounded.OpenInNew, "Open", { Actions.open(ctx, f) })
                            GlassIconButton(Icons.Rounded.Share, "Share", { Actions.share(ctx, listOf(f)) }, tint = Glass.Cyan)
                            GlassIconButton(Icons.Rounded.Delete, "Remove", { vm.forget(f, deleteFile = false) }, tint = Glass.TextDim)
                        }
                    }
                }
            }

            // ------------------------------------------------------------------ Bug Report & Feature Suggestions
            GlassCard(
                Modifier.fillMaxWidth(),
                accent = Glass.Amber,
                onClick = { showFeedbackSheet = true },
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Glass.Amber.copy(alpha = if (Glass.IsDark) 0.18f else 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.BugReport, null, tint = Glass.Amber, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Laporkan Bug atau Kirim Saran", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        Hint("Buka formulir laporan error / ide fitur ke GitHub Issues atau salin cepat")
                    }
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open feedback form", tint = Glass.Amber, modifier = Modifier.size(18.dp))
                }
            }

            // ------------------------------------------------------------------ 6. About DocPix & Updates
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("About DocPix")
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconShape = RoundedCornerShape(16.dp)
                    val iconBg = if (Glass.IsDark) Color(0xFF0E1116) else Color(0xFFF4F6F9)
                    val iconFg = if (Glass.IsDark) Color(0xFFE2E8F0) else Color(0xFF111827)
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(iconShape)
                            .background(iconBg)
                            .border(1.dp, Glass.Stroke, iconShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                            contentDescription = "DocPix app icon",
                            tint = iconFg,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 1.46f
                                    scaleY = 1.46f
                                }
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "DocPix v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.titleMedium,
                            color = Glass.TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Package: com.mediaforge.studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.TextDim
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Hint("Monochrome Liquid Glass Edition • Adaptive & Material You Themed Icon • Hilt DI • ML Kit Document Scanner • Dual-Engine Groq LPU + Gemini 3.8 SSE • Optimized ML Kit OCR • Offline Media & 14-Tool PDF Suite.")
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassButton(
                        text = if (checkingUpdate) "Memeriksa…" else "Cek Pembaruan",
                        icon = if (checkingUpdate) Icons.Rounded.Refresh else Icons.Rounded.SystemUpdate,
                        primary = availableUpdate == null,
                        compact = true,
                        enabled = !checkingUpdate,
                        onClick = { vm.checkForUpdates(userInitiated = true) },
                        modifier = Modifier.weight(1f)
                    )
                    if (availableUpdate != null) {
                        GlassButton(
                            text = "Update ${availableUpdate!!.tagName}",
                            icon = Icons.Rounded.Download,
                            primary = true,
                            compact = true,
                            onClick = vm::openUpdateDialog,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (!updateStatus.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Hint(updateStatus!!)
                }
            }
        }

        LiquidGlassTopHeader(
            title = "DocPix · Settings",
            hazeState = chromeHaze,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { headerHeightPx = it.height }
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 4.dp),
            trailing = {
                if (headerReady) {
                    OnlineBadge(
                        label = headerBadgeText,
                        accent = if (primaryEngine == AiEngine.GROQ) Glass.Emerald else Glass.Cyan
                    )
                } else {
                    OfflineBadge(compact = true)
                }
            }
        )

        if (showGuideSheet) {
            ApiKeyTutorialSheet(onDismiss = { showGuideSheet = false })
        }

        if (showFeedbackSheet) {
            FeedbackReportSheet(onDismiss = { showFeedbackSheet = false })
        }
    }
}
