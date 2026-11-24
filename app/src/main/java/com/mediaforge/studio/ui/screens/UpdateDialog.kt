package com.mediaforge.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediaforge.studio.BuildConfig
import com.mediaforge.studio.core.Actions
import com.mediaforge.studio.data.AppUpdateInfo
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.liquidGlassChrome
import com.mediaforge.studio.ui.components.plainGlassSheetColor
import com.mediaforge.studio.ui.theme.Glass

@Composable
fun AppUpdateDialog(
    update: AppUpdateInfo?,
    onDismiss: () -> Unit
) {
    if (update == null) return
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(28.dp)

    Dialog(
        onDismissRequest = onDismiss,
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
                    tintColor = plainGlassSheetColor(),
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
                        .background(Glass.CardFill)
                        .border(1.dp, Glass.Stroke, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = Glass.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Pembaruan Tersedia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Glass.TextPrimary
                    )
                    Hint("DocPix v${BuildConfig.VERSION_NAME} → ${update.tagName}")
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Glass.CardFill)
                    .border(1.dp, Glass.Stroke, RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                SectionLabel("Catatan Rilis")
                Spacer(Modifier.height(6.dp))
                Text(
                    update.releaseNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.TextSecondary
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    text = "Nanti",
                    primary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "Update Sekarang",
                    icon = Icons.Rounded.Download,
                    primary = true,
                    onClick = {
                        val targetUrl = update.apkDownloadUrl ?: update.htmlUrl
                        if (targetUrl.isNotBlank()) Actions.openUrl(ctx, targetUrl)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
