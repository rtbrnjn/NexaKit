package com.mediaforge.studio.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.core.Actions
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.ui.theme.Glass

/**
 * Compact monochrome Liquid Glass card showing up to [maxItems] most recently processed files
 * with quick Open (`ACTION_VIEW`), Share (`ACTION_SEND`), and history-remove actions.
 */
@Composable
fun RecentCreationsCard(
    files: List<SavedFile>,
    onRemove: (SavedFile) -> Unit,
    modifier: Modifier = Modifier,
    maxItems: Int = 5
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val recent = files.take(maxItems)

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                tint = Glass.Cyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "Recent Creations",
                style = MaterialTheme.typography.titleMedium,
                color = Glass.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (recent.isNotEmpty()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Glass.CardFillSubtle)
                        .border(1.dp, Glass.Stroke, RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        "${recent.size} terakhir",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (recent.isEmpty()) {
            Hint("Belum ada file tersimpan. Hasil kompresi, mockup, kolase, atau PDF terbaru akan muncul di sini.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { file ->
                    RecentCreationRow(
                        file = file,
                        onOpen = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            Actions.open(ctx, file)
                        },
                        onShare = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            Actions.share(ctx, listOf(file))
                        },
                        onRemove = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRemove(file)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentCreationRow(
    file: SavedFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit
) {
    val rowShape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(Glass.CardFillSubtle)
            .border(1.dp, Glass.Stroke, rowShape)
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (file.isImage) {
            UriImage(
                uri = file.uri,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
                maxDim = 160
            )
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Glass.CardFill)
                    .border(1.dp, Glass.Stroke, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        file.isPdf -> Icons.Rounded.PictureAsPdf
                        file.isZip -> Icons.Rounded.FolderZip
                        file.name.endsWith(".docx", ignoreCase = true) -> Icons.Rounded.Description
                        else -> Icons.Rounded.Image
                    },
                    contentDescription = null,
                    tint = Glass.TextPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Glass.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${FileUtils.formatBytes(file.sizeBytes)} • ${FileUtils.formatDate(file.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        GlassIconButton(
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            description = "Buka ${file.name}",
            onClick = onOpen,
            tint = Glass.TextPrimary
        )
        GlassIconButton(
            icon = Icons.Rounded.Share,
            description = "Bagikan ${file.name}",
            onClick = onShare,
            tint = Glass.Cyan
        )
        GlassIconButton(
            icon = Icons.Rounded.DeleteOutline,
            description = "Hapus ${file.name} dari riwayat",
            onClick = onRemove,
            tint = Glass.TextDim
        )
    }
}
