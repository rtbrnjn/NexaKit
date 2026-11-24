package com.mediaforge.studio.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.core.Actions
import com.mediaforge.studio.data.RECOMMENDED_MODELS
import com.mediaforge.studio.data.ThemeMode
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassSegmentedBar
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.plainGlassBorderColor
import com.mediaforge.studio.ui.components.plainGlassSheetColor
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.ConnectionState

private enum class GuideProvider(val label: String, val icon: ImageVector) {
    GROQ("Groq (Cepat)", Icons.Rounded.Bolt),
    GEMINI("Google Gemini", Icons.Rounded.AutoAwesome)
}

@Composable
internal fun ApiKeyVaultSection(
    title: String,
    subtitle: String,
    fieldLabel: String,
    placeholder: String,
    keyInput: String,
    onKeyChanged: (String) -> Unit,
    onSave: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
    connection: ConnectionState,
    statusText: String?,
    verifiedUnit: String,
    accent: Color
) {
    var reveal by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = accent)
        Spacer(Modifier.height(2.dp))
        Hint(subtitle)
        Spacer(Modifier.height(8.dp))

        GlassTextField(
            value = keyInput,
            onValueChange = onKeyChanged,
            label = fieldLabel,
            placeholder = placeholder,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "Toggle visibility",
                        tint = Glass.TextSecondary
                    )
                }
            }
        )

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = "Save",
                icon = Icons.Rounded.Key,
                primary = true,
                compact = true,
                onClick = onSave,
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                text = "Verify Key",
                icon = Icons.Rounded.CloudDone,
                primary = false,
                compact = true,
                onClick = onVerify,
                modifier = Modifier.weight(1.15f),
                trailing = if (connection is ConnectionState.Connected) {
                    {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Glass.Emerald.copy(alpha = if (Glass.IsDark) 0.25f else 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = Glass.Emerald, modifier = Modifier.size(12.dp))
                        }
                    }
                } else null
            )
            if (keyInput.isNotBlank()) {
                GlassIconButton(Icons.Rounded.Delete, "Delete $verifiedUnit key", onDelete, tint = Glass.Danger)
            }
        }

        if (statusText != null) {
            Spacer(Modifier.height(8.dp))
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = Glass.Emerald)
        }

        when (connection) {
            ConnectionState.Idle -> Unit
            ConnectionState.Testing -> {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Pinging $verifiedUnit API…", style = MaterialTheme.typography.bodySmall, color = accent)
                }
            }
            is ConnectionState.Connected -> {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Glass.Emerald.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f))
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Glass.Emerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Verified! ${connection.modelCount} $verifiedUnit models available.", style = MaterialTheme.typography.bodySmall, color = Glass.Emerald)
                }
            }
            is ConnectionState.Failed -> {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Glass.Danger.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f))
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Glass.Danger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(connection.message, style = MaterialTheme.typography.bodySmall, color = Glass.Danger)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApiKeyTutorialSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableStateOf(GuideProvider.GROQ) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = plainGlassSheetColor(),
        contentColor = Glass.TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.16f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.TipsAndUpdates, null, tint = Glass.Cyan, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Cara Mendapatkan API Key Gratis", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    Hint("Pilih penyedia AI untuk melihat langkah & tombol cepat")
                }
            }

            GlassSegmentedBar(
                items = GuideProvider.entries,
                selected = tab,
                label = { it.label },
                icon = { it.icon },
                onSelect = { tab = it }
            )

            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(140))) },
                label = "apiGuideTab"
            ) { provider ->
                when (provider) {
                    GuideProvider.GROQ -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Hint("Direkomendasikan — Super Cepat (Llama 3.3 & Llama 4 Scout)")
                        TutorialStepRow(1, "Buka console.groq.com di browser.", accent = Glass.Emerald)
                        TutorialStepRow(2, "Login gratis menggunakan akun Google atau GitHub kamu.", accent = Glass.Emerald)
                        TutorialStepRow(3, "Pilih menu API Keys di panel kiri, lalu klik tombol Create API Key.", accent = Glass.Emerald)
                        TutorialStepRow(4, "Beri nama bebas (misal: DocPix), salin kode berawalan gsk_..., lalu tempel di kolom atas dan tekan Save.", accent = Glass.Emerald)
                        Spacer(Modifier.height(4.dp))
                        GlassButton(
                            text = "Buka Groq Console",
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            primary = true,
                            onClick = { Actions.openUrl(ctx, "https://console.groq.com") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    GuideProvider.GEMINI -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Hint("Mesin cadangan & analisis multimoda Google Gemini 3.8 Flash")
                        TutorialStepRow(1, "Buka aistudio.google.com/apikey di browser.", accent = Glass.Cyan)
                        TutorialStepRow(2, "Login dengan akun Google, klik Create API Key, lalu salin kode AIza... ke kolom Gemini di atas.", accent = Glass.Cyan)
                        Spacer(Modifier.height(4.dp))
                        GlassButton(
                            text = "Buka Google AI Studio",
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            primary = true,
                            onClick = { Actions.openUrl(ctx, "https://aistudio.google.com/apikey") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            GlassButton(
                text = "Tutup Panduan",
                primary = false,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TutorialStepRow(step: Int, text: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Glass.CardFillSubtle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (Glass.IsDark) 0.20f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$step", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Glass.TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun ThemePillSelector(
    themeMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val modes = ThemeMode.entries
    val selectedIndex = modes.indexOf(themeMode).coerceAtLeast(0)
    val isDark = Glass.IsDark
    val pillShape = RoundedCornerShape(50)
    val borderColor = plainGlassBorderColor(isDark = isDark, strong = true)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(pillShape)
            .background(Glass.CardFill)
            .border(1.dp, Glass.Stroke, pillShape)
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / modes.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 360f),
            label = "themePillSlide"
        )

        Box(Modifier.fillMaxWidth().height(40.dp)) {
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .then(
                        if (!isDark) Modifier.shadow(4.dp, pillShape, ambientColor = Color.Black.copy(alpha = 0.08f))
                        else Modifier
                    )
                    .clip(pillShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.92f))
                    .border(1.dp, borderColor, pillShape)
            )

            Row(Modifier.fillMaxSize()) {
                modes.forEach { mode ->
                    val selected = mode == themeMode
                    val icon = when (mode) {
                        ThemeMode.SYSTEM -> Icons.Rounded.PhoneAndroid
                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                    }
                    val interaction = remember { MutableInteractionSource() }
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(pillShape)
                            .clickable(interactionSource = interaction, indication = null) { onSelect(mode) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = if (selected) (if (isDark) Color.White else Glass.Cyan) else Glass.TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) Glass.TextPrimary else Glass.TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModelDropdownSelector(
    selectedModel: String,
    onSelectModel: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var editingCustom by remember { mutableStateOf(false) }
    var customInput by remember(selectedModel) { mutableStateOf(selectedModel) }
    val borderColor = plainGlassBorderColor(isDark = Glass.IsDark)
    val popupBg = plainGlassSheetColor(isDark = Glass.IsDark)

    val activeLabel = RECOMMENDED_MODELS.find { it.id == selectedModel }?.displayLabel
        ?: "$selectedModel (Custom)"

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Glass.CardFill)
                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(activeLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("SSE streaming • thinkingLevel tuned automatically", style = MaterialTheme.typography.labelSmall, color = Glass.TextDim)
            }
            Icon(Icons.Rounded.ExpandMore, "Select model", tint = Glass.TextSecondary)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = popupBg,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, borderColor)
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
                placeholder = "e.g. gemini-3.8-flash",
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
