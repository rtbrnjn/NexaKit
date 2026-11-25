package com.mediaforge.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.core.Actions
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.UriImage
import com.mediaforge.studio.ui.components.plainGlassSheetColor
import com.mediaforge.studio.ui.theme.Glass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultSheet(files: List<SavedFile>?, onDismiss: () -> Unit) {
    if (files.isNullOrEmpty()) return
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val totalBytes = files.sumOf { it.sizeBytes }
    val folder = files.first().folder

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
                        .background(Glass.CardFill)
                        .border(1.dp, Glass.Stroke, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Glass.Emerald, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (files.size == 1) "Saved Successfully" else "${files.size} Files Saved",
                        style = MaterialTheme.typography.titleMedium,
                        color = Glass.TextPrimary
                    )
                    Text(
                        "$folder • ${FileUtils.formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.uri.toString() }) { f ->
                    GlassCard(
                        Modifier.fillMaxWidth(),
                        onClick = { Actions.open(ctx, f) },
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (f.isImage) {
                                UriImage(
                                    f.uri,
                                    Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    maxDim = 180
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        when {
                                            f.isZip -> Icons.Rounded.FolderZip
                                            f.isPdf || f.name.endsWith(".docx", true) -> Icons.Rounded.Description
                                            else -> Icons.Rounded.Image
                                        },
                                        null,
                                        tint = Glass.Cyan
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    f.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Glass.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${f.tool} • ${FileUtils.formatBytes(f.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Glass.TextSecondary
                                )
                            }
                            GlassIconButton(
                                Icons.AutoMirrored.Rounded.OpenInNew,
                                "Open File",
                                onClick = { Actions.open(ctx, f) },
                                tint = Glass.Cyan
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    text = if (files.size == 1) "Open File" else "Open Folder",
                    icon = if (files.size == 1) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.FolderOpen,
                    primary = false,
                    onClick = {
                        if (files.size == 1) Actions.open(ctx, files.first())
                        else Actions.openFolder(ctx, folder)
                    },
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = if (files.size == 1) "Share" else "Share All",
                    icon = Icons.Rounded.Share,
                    primary = true,
                    onClick = { Actions.share(ctx, files) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (files.size == 1) {
                GlassButton(
                    text = "Browse Folder ($folder)",
                    icon = Icons.Rounded.FolderOpen,
                    primary = false,
                    compact = true,
                    onClick = { Actions.openFolder(ctx, folder) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
