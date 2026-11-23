package com.mediaforge.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediaforge.studio.PendingPdfSave
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.OutputKind
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.liquidGlassChrome
import com.mediaforge.studio.ui.theme.Glass

@Composable
fun PdfRenameDialog(
    pending: PendingPdfSave?,
    saving: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (pending == null) return

    var fileName by remember(pending.id) { mutableStateOf(pending.defaultStem) }
    val resolvedName = FileUtils.normalizePdfFilename(fileName, pending.defaultStem)
    val folderPath = Storage.folderPath(OutputKind.DOCUMENT)
    val shape = RoundedCornerShape(28.dp)

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .liquidGlassChrome(
                    shape = shape,
                    blurRadius = 28.dp,
                    shadowElevation = 24.dp,
                    strongBorder = true
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Glass.CardFillSubtle)
                        .border(1.dp, Glass.Stroke, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = Glass.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Simpan File PDF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Glass.TextPrimary
                    )
                    Hint("${pending.toolName} • ${FileUtils.formatBytes(pending.tempFile.length())}")
                }
            }

            GlassTextField(
                value = fileName,
                onValueChange = { txt -> fileName = txt.take(80) },
                label = "Nama File PDF",
                placeholder = pending.defaultStem,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (!saving) onConfirm(fileName)
                }),
                trailing = {
                    Text(
                        ".pdf",
                        style = MaterialTheme.typography.labelLarge,
                        color = Glass.Cyan,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Glass.CardFillSubtle)
                    .border(1.dp, Glass.Stroke, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SectionLabel("Lokasi Penyimpanan")
                Text(
                    "$folderPath/$resolvedName",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    text = "Batal",
                    primary = false,
                    enabled = !saving,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = if (saving) "Menyimpan…" else "Simpan PDF",
                    icon = Icons.Rounded.Download,
                    primary = true,
                    enabled = !saving,
                    onClick = { onConfirm(fileName) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
