package com.mediaforge.studio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediaforge.studio.BuildConfig
import com.mediaforge.studio.core.Actions
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.data.AppUpdateInfo
import com.mediaforge.studio.data.UpdateDownloadState
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.liquidGlassChrome
import com.mediaforge.studio.ui.components.plainGlassSheetColor
import com.mediaforge.studio.ui.theme.Glass

@Composable
fun AppUpdateDialog(
    update: AppUpdateInfo?,
    downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    onStartDownload: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onInstallPermissionResult: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    autoTriggerInstaller: Boolean = true
) {
    if (update == null) return
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(28.dp)
    val isDownloading = downloadState is UpdateDownloadState.Downloading

    var autoTriggeredInstallForPath by remember(update.tagName) { mutableStateOf<String?>(null) }
    var autoPromptedPermissionForPath by remember(update.tagName) { mutableStateOf<String?>(null) }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = Actions.canInstallPackages(ctx)
        onInstallPermissionResult(granted)
    }

    LaunchedEffect(downloadState, autoTriggerInstaller) {
        if (!autoTriggerInstaller) return@LaunchedEffect
        when (downloadState) {
            is UpdateDownloadState.ReadyToInstall -> {
                val path = downloadState.apkFile.absolutePath
                if (autoTriggeredInstallForPath != path) {
                    autoTriggeredInstallForPath = path
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    Actions.installApk(ctx, downloadState.apkFile)
                }
            }
            is UpdateDownloadState.NeedsInstallPermission -> {
                val path = downloadState.apkFile.absolutePath
                if (autoPromptedPermissionForPath != path) {
                    autoPromptedPermissionForPath = path
                    runCatching {
                        unknownSourcesLauncher.launch(Actions.buildInstallPermissionIntent(ctx))
                    }
                }
            }
            else -> Unit
        }
    }

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .liquidGlassChrome(
                    shape = shape,
                    blurRadius = 16.dp,
                    shadowElevation = 20.dp,
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
                        imageVector = when (downloadState) {
                            is UpdateDownloadState.ReadyToInstall -> Icons.Rounded.CheckCircle
                            is UpdateDownloadState.NeedsInstallPermission -> Icons.Rounded.Security
                            is UpdateDownloadState.Failed -> Icons.Rounded.ErrorOutline
                            else -> Icons.Rounded.SystemUpdate
                        },
                        contentDescription = null,
                        tint = when (downloadState) {
                            is UpdateDownloadState.ReadyToInstall -> Glass.Emerald
                            is UpdateDownloadState.NeedsInstallPermission -> Glass.Amber
                            is UpdateDownloadState.Failed -> Glass.Danger
                            else -> Glass.TextPrimary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when (downloadState) {
                            is UpdateDownloadState.Downloading -> "Mengunduh Pembaruan"
                            is UpdateDownloadState.ReadyToInstall -> "Unduhan Selesai (100%)"
                            is UpdateDownloadState.NeedsInstallPermission -> "Izin Instalasi Diperlukan"
                            is UpdateDownloadState.Failed -> "Unduhan Gagal"
                            UpdateDownloadState.Idle -> "Pembaruan Tersedia"
                        },
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
                    .heightIn(max = 190.dp)
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

            when (downloadState) {
                is UpdateDownloadState.Downloading -> {
                    val pct = downloadState.progressPercent
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Glass.CardFill)
                            .border(1.dp, Glass.Stroke, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Mengunduh DocPix.apk…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Glass.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (pct != null) "$pct%" else "…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Glass.Cyan
                            )
                        }
                        if (pct != null) {
                            LinearProgressIndicator(
                                progress = { (pct / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Glass.Cyan,
                                trackColor = Glass.Stroke
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Glass.Cyan,
                                trackColor = Glass.Stroke
                            )
                        }
                        val sizeText = if (downloadState.totalBytes > 0L) {
                            "${FileUtils.formatBytes(downloadState.downloadedBytes)} / ${FileUtils.formatBytes(downloadState.totalBytes)}"
                        } else {
                            "${FileUtils.formatBytes(downloadState.downloadedBytes)} terunduh"
                        }
                        Hint(sizeText)
                    }

                    GlassButton(
                        text = "Batalkan Unduhan",
                        icon = Icons.Rounded.Close,
                        primary = false,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCancelDownload()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is UpdateDownloadState.NeedsInstallPermission -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Glass.Amber.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f))
                            .border(1.dp, Glass.Amber.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Izinkan pemasangan dari sumber ini",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Glass.TextPrimary
                        )
                        Hint("DocPix-update.apk sudah selesai diunduh (100%). Aktifkan izin pemasangan aplikasi untuk DocPix, lalu instalasi akan berjalan otomatis saat kembali.")
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
                            text = "Buka Izin",
                            icon = Icons.Rounded.Security,
                            primary = true,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                runCatching {
                                    unknownSourcesLauncher.launch(Actions.buildInstallPermissionIntent(ctx))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is UpdateDownloadState.ReadyToInstall -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Glass.Emerald.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f))
                            .border(1.dp, Glass.Emerald.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "DocPix-update.apk siap dipasang",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Glass.TextPrimary
                        )
                        Hint("Ukuran: ${FileUtils.formatBytes(downloadState.apkFile.length())} • Data & API Key tersimpan tetap aman.")
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassButton(
                            text = "Tutup",
                            primary = false,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = "Pasang Sekarang",
                            icon = Icons.Rounded.InstallMobile,
                            primary = true,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (Actions.canInstallPackages(ctx)) {
                                    Actions.installApk(ctx, downloadState.apkFile)
                                } else {
                                    onInstallPermissionResult(false)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is UpdateDownloadState.Failed -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Glass.Danger.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f))
                            .border(1.dp, Glass.Danger.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            downloadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.TextPrimary
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
                            text = "Coba Lagi",
                            icon = Icons.Rounded.Refresh,
                            primary = true,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStartDownload()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                UpdateDownloadState.Idle -> {
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
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStartDownload()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
