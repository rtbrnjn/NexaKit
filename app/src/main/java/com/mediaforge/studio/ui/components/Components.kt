package com.mediaforge.studio.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.core.BitmapUtils
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.Progress
import com.mediaforge.studio.vm.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ Calm Canvas Background

@Composable
fun LiquidBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val bg = Glass.Bg
    val topTint = Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.045f else 0.055f)
    val bottomTint = Glass.Emerald.copy(alpha = if (Glass.IsDark) 0.03f else 0.04f)
    Box(
        modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(topTint, Color.Transparent),
                        center = Offset(size.width * 0.2f, 0f),
                        radius = size.width * 0.9f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(bottomTint, Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height),
                        radius = size.width * 0.85f
                    )
                )
            },
        content = content
    )
}

// ------------------------------------------------------------------ Frosted Glass Cards & Controls

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && onClick != null) 0.982f else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press"
    )
    val cardFill = Glass.CardFill
    val stroke = Glass.Stroke
    val isDark = Glass.IsDark

    var m = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(
            if (!isDark) Modifier.shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.05f)
            ) else Modifier
        )
        .clip(shape)
        .background(cardFill)
        .border(1.dp, stroke, shape)

    if (accent != null) {
        val wash = accent.copy(alpha = if (isDark) 0.06f else 0.05f)
        m = m.drawBehind {
            drawRect(Brush.verticalGradient(listOf(wash, Color.Transparent)))
        }
    }
    if (onClick != null) {
        m = m.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    Column(m.padding(contentPadding), content = content)
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(if (compact) 13.dp else 16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.975f else 1f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "btn"
    )
    val contentColor = if (primary) Glass.OnAccent else Glass.TextPrimary
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.42f }
            .height(if (compact) 38.dp else 50.dp)
            .clip(shape)
            .then(
                if (primary) {
                    Modifier.background(Glass.Cyan)
                } else {
                    Modifier
                        .background(Glass.CardFill)
                        .border(1.dp, Glass.Stroke, shape)
                }
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 13.dp else 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(if (compact) 17.dp else 19.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = contentColor, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Glass.TextPrimary
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, description, tint = if (enabled) tint else Glass.TextDim, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Glass.Cyan
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = if (Glass.IsDark) 0.16f else 0.12f) else Glass.CardFillSubtle)
            .border(1.dp, if (selected) color.copy(alpha = 0.55f) else Glass.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            maxLines = 1,
            color = if (selected) color else Glass.TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { GlassChip(label(it), it == selected, { onSelect(it) }) }
    }
}

// ------------------------------------------------------------------ Inputs

@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Glass.TextSecondary, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = Glass.Cyan)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Glass.Cyan,
                activeTrackColor = Glass.Cyan,
                inactiveTrackColor = Glass.Stroke,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Glass.TextDim) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Glass.CardFill,
            unfocusedContainerColor = Glass.CardFillSubtle,
            focusedBorderColor = Glass.Cyan,
            unfocusedBorderColor = Glass.Stroke,
            focusedLabelColor = Glass.Cyan,
            unfocusedLabelColor = Glass.TextSecondary,
            cursorColor = Glass.Cyan,
            focusedTextColor = Glass.TextPrimary,
            unfocusedTextColor = Glass.TextPrimary
        )
    )
}

@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Glass.OnAccent,
            checkedTrackColor = Glass.Cyan,
            uncheckedThumbColor = Glass.TextSecondary,
            uncheckedTrackColor = Glass.CardFillSubtle,
            uncheckedBorderColor = Glass.Stroke
        )
    )
}

// ------------------------------------------------------------------ Typography helpers

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 2.dp),
        color = Glass.Cyan,
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
}

// ------------------------------------------------------------------ Badges & Banners

@Composable
fun OfflineBadge(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Glass.Amber.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f))
            .border(1.dp, Glass.Amber.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = Glass.Amber, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (compact) "Offline Mode" else "Offline Mode — All Media & PDF Tools Ready",
            color = Glass.Amber,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun OnlineBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Glass.Emerald.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f))
            .border(1.dp, Glass.Emerald.copy(alpha = 0.42f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Glass.Emerald))
        Spacer(Modifier.width(6.dp))
        Text("Gemini Online", color = Glass.Emerald, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun StatusBanner(message: UiMessage?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    val tint = if (message.isError) Glass.Danger else Glass.Emerald
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = if (Glass.IsDark) 0.12f else 0.08f))
            .border(1.dp, tint.copy(alpha = 0.42f), shape)
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
        GlassCard(Modifier.padding(32.dp).fillMaxWidth(0.82f)) {
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
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (title != null) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(26.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f))
                    .border(1.dp, Glass.Cyan.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Glass.Cyan, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
        }
    }
}
