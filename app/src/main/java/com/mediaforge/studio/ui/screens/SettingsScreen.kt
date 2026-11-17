package com.mediaforge.studio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.core.Actions
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.OutputKind
import com.mediaforge.studio.core.Permissions
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.data.RECOMMENDED_MODELS
import com.mediaforge.studio.data.ThemeMode
import com.mediaforge.studio.pdf.PdfPageMode
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassSwitch
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.LabeledSlider
import com.mediaforge.studio.ui.components.OfflineBadge
import com.mediaforge.studio.ui.components.OnlineBadge
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.UriImage
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.ConnectionState
import com.mediaforge.studio.vm.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val ctx = LocalContext.current
    val themeMode by vm.themeMode.collectAsState()
    val keyInput by vm.keyInput.collectAsState()
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

    var revealKey by remember { mutableStateOf(false) }
    var permsGranted by remember { mutableStateOf(Permissions.granted(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permsGranted = Permissions.granted(ctx)
    }

    LaunchedEffect(Unit) { vm.refreshCacheSize() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NexaKit · Settings", style = MaterialTheme.typography.headlineMedium, color = Glass.TextPrimary)
                Text("Appearance • Gemini Vault • Defaults • Storage", style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
            }
            if (online) OnlineBadge() else OfflineBadge(compact = true)
        }

        // ------------------------------------------------------------------ 1. Appearance (Dark / Light / System)
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Appearance")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Glass.CardFillSubtle)
                    .border(1.dp, Glass.Stroke, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val selected = mode == themeMode
                    val icon = when (mode) {
                        ThemeMode.SYSTEM -> Icons.Rounded.PhoneAndroid
                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .then(
                                if (selected) Modifier
                                    .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.18f else 0.14f))
                                    .border(1.dp, Glass.Cyan.copy(alpha = 0.5f), RoundedCornerShape(11.dp))
                                else Modifier
                            )
                            .clickable { vm.setThemeMode(mode) }
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = if (selected) Glass.Cyan else Glass.TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) Glass.TextPrimary else Glass.TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Hint("Liquid Glass adapts frosted contrast automatically across Light & Dark themes.")
        }

        // ------------------------------------------------------------------ 2. Gemini AI Configuration + Compact Dropdown
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = Glass.Cyan, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Gemini AI Configuration", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    Hint("API key encrypted locally in EncryptedSharedPreferences")
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassTextField(
                value = keyInput,
                onValueChange = vm::onKeyChanged,
                label = "Google Gemini API Key",
                placeholder = "AIza…",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { revealKey = !revealKey }) {
                        Icon(
                            if (revealKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle key visibility",
                            tint = Glass.TextSecondary
                        )
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Save Key", icon = Icons.Rounded.Key, onClick = vm::saveKey, compact = true, modifier = Modifier.weight(1f))
                GlassButton("Verify Key", icon = Icons.Rounded.CloudDone, primary = false, compact = true, onClick = vm::testConnection, modifier = Modifier.weight(1f))
                if (keyInput.isNotBlank()) {
                    GlassIconButton(Icons.Rounded.Delete, "Remove Key", vm::clearKey, tint = Glass.Danger)
                }
            }

            if (status != null) {
                Spacer(Modifier.height(8.dp))
                Text(status!!, style = MaterialTheme.typography.bodySmall, color = Glass.Emerald)
            }

            when (val c = connection) {
                ConnectionState.Idle -> Unit
                ConnectionState.Testing -> {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Glass.Cyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Pinging Gemini API…", style = MaterialTheme.typography.bodySmall, color = Glass.Cyan)
                    }
                }
                is ConnectionState.Connected -> {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Glass.Emerald.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f))
                            .border(1.dp, Glass.Emerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Glass.Emerald, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Verified! ${c.modelCount} Gemini models available.", style = MaterialTheme.typography.bodySmall, color = Glass.Emerald)
                    }
                }
                is ConnectionState.Failed -> {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Glass.Danger.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f))
                            .border(1.dp, Glass.Danger.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = Glass.Danger, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(c.message, style = MaterialTheme.typography.bodySmall, color = Glass.Danger)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Gemini Model")
            Spacer(Modifier.height(8.dp))
            ModelDropdownSelector(selectedModel = model, onSelectModel = vm::setModel)
        }

        // ------------------------------------------------------------------ 3. Default Export Preferences
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Default Export Preferences")
            Spacer(Modifier.height(8.dp))
            LabeledSlider(
                label = "Default Image Compression Quality",
                valueText = "$defaultQuality%",
                value = defaultQuality.toFloat(),
                range = 10f..95f,
                onChange = { vm.setDefaultQuality(it.toInt()) }
            )
            SectionLabel("Default PDF Page Size")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(PdfPageMode.entries, defaultPdfMode, { it.label }, vm::setDefaultPdfPageMode)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-Strip EXIF Metadata", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                    Hint("Removes GPS & device metadata by default in Compressor")
                }
                GlassSwitch(autoStripExif, vm::setAutoStripExif)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Open / Share Action Sheet", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                    Hint("Show quick actions automatically after every export")
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(Glass.Emerald.copy(alpha = if (Glass.IsDark) 0.10f else 0.08f))
                    .border(1.dp, Glass.Emerald.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(Glass.CardFillSubtle)
                    .border(1.dp, Glass.Stroke, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.AutoDelete, null, tint = Glass.Cyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Temporary Cache", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                    Text(FileUtils.formatBytes(cacheBytes), style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
                }
                GlassButton("Clear Cache", primary = false, compact = true, enabled = cacheBytes > 0, onClick = vm::clearCache)
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

            Spacer(Modifier.height(12.dp))
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(Glass.CardFillSubtle)
                            .border(1.dp, Glass.Stroke, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
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
                GlassCard(Modifier.fillMaxWidth(), onClick = { Actions.open(ctx, f) }, contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (f.isImage) {
                            UriImage(f.uri, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), maxDim = 180)
                        } else {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Description, null, tint = Glass.Cyan)
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

        // ------------------------------------------------------------------ 6. About NexaKit
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("About NexaKit")
            Spacer(Modifier.height(6.dp))
            Text("NexaKit v1.2.0 (Build 3)", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Hint("True Liquid Glass UI • Offline Media & 12-Tool PDF Suite • Local ML Kit OCR + Turbo SSE Gemini AI • Room History Vault & Share Target.")
            Spacer(Modifier.height(4.dp))
            Text("Package: com.mediaforge.studio", style = MaterialTheme.typography.labelSmall, color = Glass.TextDim)
        }
    }
}

@Composable
private fun ModelDropdownSelector(
    selectedModel: String,
    onSelectModel: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var editingCustom by remember { mutableStateOf(false) }
    var customInput by remember(selectedModel) { mutableStateOf(selectedModel) }

    val activeLabel = RECOMMENDED_MODELS.find { it.id == selectedModel }?.displayLabel
        ?: "$selectedModel (Custom)"

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Glass.CardFillSubtle)
                .border(1.dp, Glass.Stroke, RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(activeLabel, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("SSE streaming • thinkingBudget=0 on Flash models", style = MaterialTheme.typography.labelSmall, color = Glass.TextDim)
            }
            Icon(Icons.Rounded.ExpandMore, "Select model", tint = Glass.Cyan)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Glass.Surface)
                .border(1.dp, Glass.Stroke, RoundedCornerShape(12.dp))
        ) {
            RECOMMENDED_MODELS.forEach { choice ->
                val isSelected = choice.id == selectedModel
                DropdownMenuItem(
                    text = {
                        Text(
                            choice.displayLabel,
                            color = if (isSelected) Glass.Cyan else Glass.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Rounded.Check, null, tint = Glass.Cyan, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        expanded = false
                        editingCustom = false
                        onSelectModel(choice.id)
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        "Custom model ID…",
                        color = Glass.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingIcon = {
                    Icon(Icons.Rounded.Edit, null, tint = Glass.TextSecondary, modifier = Modifier.size(16.dp))
                },
                onClick = {
                    expanded = false
                    editingCustom = true
                }
            )
        }
    }

    if (editingCustom) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                value = customInput,
                onValueChange = { customInput = it },
                label = "Custom Model ID",
                placeholder = "e.g. gemini-2.5-flash",
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            GlassButton(
                text = "Set",
                compact = true,
                enabled = customInput.isNotBlank(),
                onClick = {
                    onSelectModel(customInput.trim())
                    editingCustom = false
                }
            )
        }
    }
}
