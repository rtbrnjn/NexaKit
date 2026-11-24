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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FilterFrames
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.IncomingShare
import com.mediaforge.studio.ShareAction
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.plainGlassSheetColor
import com.mediaforge.studio.ui.theme.Glass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareIntakeSheet(
    share: IncomingShare?,
    onSelect: (ShareAction) -> Unit,
    onDismiss: () -> Unit
) {
    if (share == null || share.isEmpty) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Icon(Icons.Rounded.Share, null, tint = Glass.TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Open with DocPix", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    val summary = buildList {
                        if (share.images.isNotEmpty()) add("${share.images.size} image(s)")
                        if (share.pdfs.isNotEmpty()) add("${share.pdfs.size} PDF(s)")
                    }.joinToString(" • ")
                    Text("Received $summary • Choose a tool", style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
                }
            }

            if (share.pdfs.size == 1 && share.images.isEmpty()) {
                ShareOptionRow("Summarize / Ask AI Studio", "Instant Groq LPU & Gemini streaming Q&A", Icons.Rounded.AutoAwesome, Glass.Cyan) {
                    onSelect(ShareAction.AI_PDF_SUMMARY)
                }
                ShareOptionRow("Convert PDF to Word (.docx)", "Extract editable Word document", Icons.Rounded.Description, Glass.Emerald) {
                    onSelect(ShareAction.PDF_TO_WORD)
                }
                ShareOptionRow("Compress PDF", "Shrink size offline", Icons.Rounded.Compress, Glass.Cyan) {
                    onSelect(ShareAction.COMPRESS_PDF)
                }
                ShareOptionRow("Split / Extract Pages", "Pick specific pages visually", Icons.Rounded.ContentCut, Glass.Amber) {
                    onSelect(ShareAction.SPLIT_PDF)
                }
                ShareOptionRow("PDF to Images", "Export pages as PNG / JPG", Icons.Rounded.Image, Glass.Emerald) {
                    onSelect(ShareAction.PDF_TO_IMAGES)
                }
            } else if (share.pdfs.size >= 2) {
                ShareOptionRow("Merge ${share.pdfs.size} PDFs", "Combine into one unified PDF", Icons.AutoMirrored.Rounded.MergeType, Glass.Cyan) {
                    onSelect(ShareAction.MERGE_PDFS)
                }
            }

            if (share.images.isNotEmpty()) {
                ShareOptionRow(
                    if (share.images.size == 1) "Compress & Strip EXIF" else "Batch Compress ${share.images.size} Images",
                    "WEBP / JPEG • Remove GPS metadata",
                    Icons.Rounded.Compress,
                    Glass.Emerald
                ) { onSelect(ShareAction.COMPRESS_IMAGES) }

                if (share.images.size == 1) {
                    ShareOptionRow("AI Product Copywriter", "Generate title, 3 hooks & caption", Icons.Rounded.Campaign, Glass.Cyan) {
                        onSelect(ShareAction.AI_COPYWRITER)
                    }
                    ShareOptionRow("Product Mockup Framer", "Frame onto 1:1 / 4:5 / 9:16 canvas", Icons.Rounded.FilterFrames, Glass.Amber) {
                        onSelect(ShareAction.MOCKUP_IMAGE)
                    }
                }
                if (share.images.size in 2..4) {
                    ShareOptionRow("Grid Collage (${share.images.size} Photos)", "Combine into high-res PNG grid", Icons.Rounded.AutoAwesomeMosaic, Glass.Cyan) {
                        onSelect(ShareAction.COLLAGE_IMAGES)
                    }
                }
                ShareOptionRow("Convert Images to PDF", "Build A4 or fit-to-image PDF", Icons.Rounded.PictureAsPdf, Glass.Cyan) {
                    onSelect(ShareAction.IMAGES_TO_PDF)
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ShareOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
            }
        }
    }
}
