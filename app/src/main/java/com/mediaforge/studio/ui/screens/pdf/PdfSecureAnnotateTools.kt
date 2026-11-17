package com.mediaforge.studio.ui.screens.pdf

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.pdf.PageNumberFormat
import com.mediaforge.studio.pdf.PageNumberPosition
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.EmptyPicker
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassSwitch
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.LabeledSlider
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.PageNumbersViewModel
import com.mediaforge.studio.vm.ProtectPdfViewModel
import com.mediaforge.studio.vm.UnlockPdfViewModel
import com.mediaforge.studio.vm.WatermarkPdfViewModel

@Composable
internal fun PageNumbersScreen(vm: PageNumbersViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val opts by vm.options.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Add Page Numbers",
        subtitle = "Stamp clean footer numbering across pages",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF File",
                subtitle = "Applies crisp footer numbers on every page",
                icon = Icons.Rounded.FormatListNumbered,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Number Format")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(PageNumberFormat.entries, opts.format, { it.label }, vm::setFormat)

            Spacer(Modifier.height(12.dp))
            SectionLabel("Position")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(PageNumberPosition.entries, opts.position, { it.label }, vm::setPosition)

            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = "Font Size",
                valueText = "${opts.fontSize.toInt()} pt",
                value = opts.fontSize,
                range = 8f..16f,
                onChange = vm::setFontSize
            )
            LabeledSlider(
                label = "Start Number",
                valueText = "${opts.startNumber}",
                value = opts.startNumber.toFloat(),
                range = 1f..50f,
                onChange = { vm.setStartNumber(it.toInt()) }
            )
        }

        GlassButton(
            text = "Apply Page Numbers",
            icon = Icons.Rounded.Download,
            enabled = src != null,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun WatermarkPdfScreen(vm: WatermarkPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val text by vm.text.collectAsState()
    val opacity by vm.opacity.collectAsState()
    val color by vm.color.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Add Text Watermark",
        subtitle = "Diagonal translucent stamp across every page",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF File",
                subtitle = "Stamp CONFIDENTIAL, DRAFT or custom text",
                icon = Icons.Rounded.WaterDrop,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            GlassTextField(
                value = text,
                onValueChange = vm::setText,
                label = "Watermark Text",
                placeholder = "CONFIDENTIAL",
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            LabeledSlider(
                label = "Opacity",
                valueText = "${(opacity * 100).toInt()}%",
                value = opacity,
                range = 0.05f..0.75f,
                onChange = vm::setOpacity
            )
            SectionLabel("Ink Color")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFF64748B.toInt(), 0xFF0284C7.toInt(), 0xFF059669.toInt(), 0xFFE11D48.toInt(), 0xFF1E293B.toInt()).forEach { argb ->
                    val selected = color == argb
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(if (selected) 2.5.dp else 1.dp, if (selected) Glass.Cyan else Glass.Stroke, CircleShape)
                            .clickable { vm.setColor(argb) }
                    )
                }
            }
        }

        GlassButton(
            text = "Apply Watermark",
            icon = Icons.Rounded.Download,
            enabled = src != null && text.isNotBlank(),
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun ProtectPdfScreen(vm: ProtectPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val userPass by vm.userPassword.collectAsState()
    val ownerPass by vm.ownerPassword.collectAsState()
    val allowPrint by vm.allowPrint.collectAsState()
    val allowCopy by vm.allowCopy.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    var showPass by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Protect / Encrypt PDF",
        subtitle = "128-bit password protection via PDFBox",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF to Encrypt",
                subtitle = "Requires password to open the exported PDF",
                icon = Icons.Rounded.Lock,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            PdfPasswordField(
                value = userPass,
                onValueChange = vm::setUserPassword,
                label = "Open Password (min 4 chars)",
                placeholder = "Enter password",
                showPassword = showPass,
                onToggleVisibility = { showPass = !showPass }
            )
            Spacer(Modifier.height(10.dp))
            PdfPasswordField(
                value = ownerPass,
                onValueChange = vm::setOwnerPassword,
                label = "Owner Password (optional)",
                placeholder = "Defaults to open password",
                showPassword = showPass
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow Printing", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                    Hint("Permits PDF viewers to print the document")
                }
                GlassSwitch(allowPrint, vm::setAllowPrint)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow Text Copy", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                    Hint("Permits copying text content from the PDF")
                }
                GlassSwitch(allowCopy, vm::setAllowCopy)
            }
        }

        GlassButton(
            text = "Encrypt & Save PDF",
            icon = Icons.Rounded.Lock,
            enabled = src != null && userPass.length >= 4,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun UnlockPdfScreen(vm: UnlockPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val password by vm.password.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    var showPass by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Unlock / Decrypt PDF",
        subtitle = "Remove password restrictions from owned PDFs",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a Protected PDF",
                subtitle = "Enter the current password to save an unlocked copy",
                icon = Icons.Rounded.LockOpen,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background((if (src.isEncrypted) Glass.Amber else Glass.Emerald).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (src.isEncrypted) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            null,
                            tint = if (src.isEncrypted) Glass.Amber else Glass.Emerald
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(src.name, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (src.isEncrypted) "Password Protected" else "Already Unlocked") + " • ${FileUtils.formatBytes(src.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (src.isEncrypted) Glass.Amber else Glass.Emerald
                        )
                    }
                    GlassButton("Change", onClick = { picker.launch(arrayOf("application/pdf")) }, primary = false, compact = true)
                }
            }

            if (src.isEncrypted) {
                GlassCard(Modifier.fillMaxWidth()) {
                    PdfPasswordField(
                        value = password,
                        onValueChange = vm::setPassword,
                        label = "PDF Password",
                        placeholder = "Enter document password",
                        showPassword = showPass,
                        onToggleVisibility = { showPass = !showPass }
                    )
                }
            }
        }

        GlassButton(
            text = "Unlock & Save PDF",
            icon = Icons.Rounded.LockOpen,
            enabled = src != null && src.isEncrypted,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
