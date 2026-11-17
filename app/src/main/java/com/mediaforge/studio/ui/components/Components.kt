package com.mediaforge.studio.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.core.BitmapUtils
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.Progress
import com.mediaforge.studio.vm.UiMessage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ Badges & Banners

@Composable
fun OfflineBadge(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Glass.CardFillSubtle)
            .border(1.dp, Glass.Amber.copy(alpha = 0.40f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = Glass.Amber, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (compact) "Offline Mode" else "Offline Mode — Media & PDF Tools Ready",
            color = Glass.Amber,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun OnlineBadge(
    modifier: Modifier = Modifier,
    label: String = "Gemini Online",
    accent: Color = Glass.Emerald
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = if (Glass.IsDark) 0.16f else 0.12f))
            .border(1.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(6.dp))
        Text(label, color = accent, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun StatusBanner(message: UiMessage?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    val tint = if (message.isError) Glass.Danger else Glass.Emerald
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f))
            .border(1.dp, tint.copy(alpha = 0.38f), shape)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (message.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(message.text, color = Glass.TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Close, "Dismiss", tint = Glass.TextSecondary, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
fun ProgressOverlay(progress: Progress?) {
    if (progress == null) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            },
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassCard(Modifier.padding(32.dp).fillMaxWidth(0.82f)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (progress.fraction == null) {
                    CircularProgressIndicator(color = Glass.Cyan, trackColor = Glass.Stroke)
                } else {
                    LinearProgressIndicator(
                        progress = { progress.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Glass.Cyan,
                        trackColor = Glass.Stroke
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(progress.label, color = Glass.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ------------------------------------------------------------------ Screen Chrome

@Composable
fun ToolPage(
    title: String?,
    subtitle: String?,
    progress: Progress?,
    message: UiMessage?,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 120.dp,
    hazeState: HazeState? = LocalHazeState.current,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier.fillMaxSize()) {
        val baseScroll = Modifier
            .fillMaxSize()
            .let { if (hazeState != null) it.hazeSource(hazeState, zIndex = 1f) else it }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = topPadding, bottom = bottomPadding)

        Column(baseScroll, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (title != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Glass.TextPrimary)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = Glass.TextPrimary)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
                    }
                    trailing?.invoke()
                }
            }
            StatusBanner(message, onDismissMessage)
            content()
        }
        ProgressOverlay(progress)
    }
}

// ------------------------------------------------------------------ Images

object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 10).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    suspend fun get(ctx: android.content.Context, uri: Uri, maxDim: Int): Bitmap? {
        val key = "$uri@$maxDim"
        cache.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) { runCatching { BitmapUtils.decode(ctx, uri, maxDim) }.getOrNull() }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }
}

@Composable
fun UriImage(
    uri: Uri,
    modifier: Modifier = Modifier,
    maxDim: Int = 360,
    contentScale: ContentScale = ContentScale.Crop
) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri, maxDim) { value = ThumbCache.get(ctx, uri, maxDim) }
    Box(modifier.background(Glass.CardFillSubtle)) {
        bmp?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = contentScale)
        }
    }
}

@Composable
fun BitmapImage(bitmap: Bitmap?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = contentScale)
    }
}

@Composable
fun EmptyPicker(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    LiquidGlassCard(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        accent = Glass.Cyan,
        contentPadding = PaddingValues(28.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.16f else 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Glass.Cyan, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Hint(subtitle)
        }
    }
}
