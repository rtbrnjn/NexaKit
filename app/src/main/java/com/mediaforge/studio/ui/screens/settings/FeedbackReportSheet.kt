package com.mediaforge.studio.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import java.net.URLEncoder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.BuildConfig
import com.mediaforge.studio.core.Actions
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassChip
import com.mediaforge.studio.ui.components.GlassSegmentedBar
import com.mediaforge.studio.ui.components.GlassSwitch
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.theme.Glass

internal enum class FeedbackKind(val label: String, val icon: ImageVector, val ghLabel: String) {
    BUG("Laporkan Bug", Icons.Rounded.BugReport, "bug"),
    SUGGESTION("Saran Fitur", Icons.Rounded.Lightbulb, "enhancement")
}

internal object FeedbackReportFormatter {
    private val CATEGORIES = listOf("Media Lab", "PDF Suite", "AI Studio", "UI Liquid Glass", "Lainnya")
    fun categories(): List<String> = CATEGORIES

    fun defaultDeviceSummary(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    fun formatBody(
        kind: FeedbackKind,
        category: String,
        title: String,
        detail: String,
        includeDeviceInfo: Boolean,
        appVersion: String = "DocPix v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
        deviceInfo: String = defaultDeviceSummary()
    ): String = buildString {
        appendLine("### ${kind.label} ($category)")
        appendLine("**Judul:** ${title.trim()}")
        appendLine()
        appendLine("**Deskripsi:**")
        appendLine(detail.trim())
        if (includeDeviceInfo) {
            appendLine()
            appendLine("---")
            appendLine("- **App:** $appVersion")
            appendLine("- **Device:** $deviceInfo")
        }
    }.trim()

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    fun buildGitHubIssueUrl(
        kind: FeedbackKind,
        category: String,
        title: String,
        detail: String,
        includeDeviceInfo: Boolean,
        appVersion: String = "DocPix v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
        deviceInfo: String = defaultDeviceSummary()
    ): String {
        val tag = if (kind == FeedbackKind.BUG) "[Bug]" else "[Saran]"
        val fullTitle = "$tag [$category] ${title.trim()}"
        val body = formatBody(kind, category, title, detail, includeDeviceInfo, appVersion, deviceInfo)
        return "https://github.com/rtbrnjn/NexaKit/issues/new" +
            "?title=${urlEncode(fullTitle)}" +
            "&body=${urlEncode(body)}" +
            "&labels=${urlEncode(kind.ghLabel)}"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackReportSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kind by remember { mutableStateOf(FeedbackKind.BUG) }
    var category by remember { mutableStateOf(FeedbackReportFormatter.categories().first()) }
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var includeDeviceInfo by remember { mutableStateOf(true) }

    val accent = if (kind == FeedbackKind.BUG) Glass.Amber else Glass.Cyan
    val ready = title.isNotBlank() && detail.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Glass.Surface,
        contentColor = Glass.TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (Glass.IsDark) 0.18f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(kind.icon, null, tint = accent, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Laporkan Bug atau Saran", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    Hint("Masukan langsung membantu pengembangan DocPix berikutnya")
                }
            }

            GlassSegmentedBar(
                items = FeedbackKind.entries,
                selected = kind,
                label = { it.label },
                icon = { it.icon },
                onSelect = { kind = it }
            )

            SectionLabel("Area Fitur")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedbackReportFormatter.categories().forEach { item ->
                    GlassChip(
                        text = item,
                        selected = category == item,
                        onClick = { category = item },
                        color = accent
                    )
                }
            }

            GlassTextField(
                value = title,
                onValueChange = { title = it.take(90) },
                label = "Judul Singkat",
                placeholder = if (kind == FeedbackKind.BUG) "Contoh: Hasil kompresi foto gagal tersimpan" else "Contoh: Tambah preset rasio IG Stories",
                singleLine = true
            )

            GlassTextField(
                value = detail,
                onValueChange = { detail = it.take(1500) },
                label = if (kind == FeedbackKind.BUG) "Langkah Kejadian & Detail Bug" else "Detail Saran / Ide Fitur",
                placeholder = if (kind == FeedbackKind.BUG) "Ceritakan urutan klik sebelum error terjadi…" else "Jelaskan fitur yang kamu harapkan…",
                minLines = 3,
                maxLines = 6
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sertakan info versi & perangkat", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                    Hint("DocPix v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • tanpa API key atau file pribadi")
                }
                GlassSwitch(includeDeviceInfo, { includeDeviceInfo = it })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    text = "Salin Laporan",
                    icon = Icons.Rounded.ContentCopy,
                    primary = false,
                    enabled = ready,
                    onClick = {
                        val report = FeedbackReportFormatter.formatBody(kind, category, title, detail, includeDeviceInfo)
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("DocPix Feedback", report))
                        Toast.makeText(ctx, "Laporan disalin ke clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "Kirim GitHub",
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    primary = true,
                    enabled = ready,
                    onClick = {
                        val url = FeedbackReportFormatter.buildGitHubIssueUrl(kind, category, title, detail, includeDeviceInfo)
                        Actions.openUrl(ctx, url)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            GlassButton(
                text = "Tutup",
                primary = false,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
