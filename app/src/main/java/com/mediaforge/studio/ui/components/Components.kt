package com.mediaforge.studio.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.core.BitmapUtils
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.Progress
import com.mediaforge.studio.vm.UiMessage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Primary Haze state carrying both ambient backdrop (zIndex 0) and scrolling UI content (zIndex 1) for floating bars. */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/** Backdrop-only Haze state carrying only `LiquidGlassBackdrop` so cards blur glowing ambient blobs cleanly without cycles. */
val LocalBackdropHazeState = compositionLocalOf<HazeState?> { null }

@Composable
fun diagonalGlassBorder(isDark: Boolean = Glass.IsDark, strong: Boolean = false): Brush {
    val startAlpha = when {
        isDark && strong -> 0.42f
        isDark -> 0.35f
        strong -> 0.95f
        else -> 0.88f
    }
    val endAlpha = when {
        isDark -> 0.06f
        else -> 0.16f
    }
    val endColor = if (isDark) Color.White else Color(0xFF94A3B8)
    return remember(isDark, strong) {
        Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = startAlpha), endColor.copy(alpha = endAlpha)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }
}

// ------------------------------------------------------------------ Breathing Ambient Mesh Backdrop

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    backdropHaze: HazeState? = LocalBackdropHazeState.current,
    chromeHaze: HazeState? = LocalHazeState.current,
    content: @Composable BoxScope.() -> Unit
) {
    val bg = Glass.Bg
    val isDark = Glass.IsDark
    val transition = rememberInfiniteTransition(label = "ambientMesh")

    val driftA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(15_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blobA"
    )
    val driftB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blobB"
    )
    val driftC by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blobC"
    )

    val sapphire = if (isDark) Color(0xFF2563EB) else Color(0xFF7DD3FC)
    val emerald = if (isDark) Color(0xFF10B981) else Color(0xFF6EE7B7)
    val indigo = if (isDark) Color(0xFF6366F1) else Color(0xFFA5B4FC)

    val alphaA = if (isDark) (0.26f + 0.06f * driftA) else (0.32f + 0.06f * driftA)
    val alphaB = if (isDark) (0.22f + 0.06f * driftB) else (0.28f + 0.06f * driftB)
    val alphaC = if (isDark) (0.17f + 0.05f * driftC) else (0.20f + 0.05f * driftC)

    Box(modifier.fillMaxSize()) {
        // Mesh blobs layer isolated as a sibling underneath screen content so Haze captures clean glow refractions.
        Box(
            Modifier
                .fillMaxSize()
                .let { if (backdropHaze != null) it.hazeSource(backdropHaze, zIndex = 0f) else it }
                .let { if (chromeHaze != null) it.hazeSource(chromeHaze, zIndex = 0f) else it }
                .background(bg)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    // Soft sapphire aura top-left / mid-left
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(sapphire.copy(alpha = alphaA), Color.Transparent),
                            center = Offset(w * (0.20f + 0.08f * driftA), h * (0.24f + 0.05f * driftB)),
                            radius = w * 0.78f
                        ),
                        center = Offset(w * (0.20f + 0.08f * driftA), h * (0.24f + 0.05f * driftB)),
                        radius = w * 0.78f
                    )
                    // Emerald-mint glow center-right
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(emerald.copy(alpha = alphaB), Color.Transparent),
                            center = Offset(w * (0.82f - 0.08f * driftB), h * (0.44f + 0.07f * driftA)),
                            radius = w * 0.72f
                        ),
                        center = Offset(w * (0.82f - 0.08f * driftB), h * (0.44f + 0.07f * driftA)),
                        radius = w * 0.72f
                    )
                    // Soft indigo glow lower-left / bottom
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(indigo.copy(alpha = alphaC), Color.Transparent),
                            center = Offset(w * (0.34f + 0.12f * driftC), h * (0.84f - 0.06f * driftB)),
                            radius = w * 0.68f
                        ),
                        center = Offset(w * (0.34f + 0.12f * driftC), h * (0.84f - 0.06f * driftB)),
                        radius = w * 0.68f
                    )
                }
        )
        content()
    }
}

@Composable
fun LiquidBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    LiquidGlassBackdrop(modifier = modifier, content = content)

// ------------------------------------------------------------------ True Liquid Glass Chrome & Docks

/** Applies frosted Haze blur, diagonal specular border (35%->6% white), and soft ambient elevation shadow. */
@Composable
fun Modifier.liquidGlassChrome(
    shape: Shape = RoundedCornerShape(28.dp),
    hazeState: HazeState? = LocalHazeState.current,
    blurRadius: Dp = 24.dp,
    shadowElevation: Dp = 14.dp,
    tintColor: Color = Glass.NavFill,
    strongBorder: Boolean = false
): Modifier {
    val isDark = Glass.IsDark
    val fallbackBg = Glass.Surface
    val borderBrush = diagonalGlassBorder(isDark = isDark, strong = strongBorder)
    val hazeStyle = remember(fallbackBg, tintColor, blurRadius) {
        HazeStyle(
            backgroundColor = fallbackBg,
            tint = HazeTint(tintColor),
            blurRadius = blurRadius,
            noiseFactor = 0.05f
        )
    }
    val base = this
        .shadow(
            elevation = if (isDark) shadowElevation else (shadowElevation * 0.65f),
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.09f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.08f)
        )
        .clip(shape)
    val frosted = if (hazeState != null) {
        base.hazeEffect(state = hazeState, style = hazeStyle)
    } else {
        base.background(tintColor)
    }
    return frosted
        .drawBehind {
            // Subtle inner top specular gleam along the glass rim
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.14f else 0.50f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = 18.dp.toPx()
                )
            )
        }
        .border(1.dp, borderBrush, shape)
}

/** Floating frosted glass top header matching the Liquid Glass dock aesthetic. */
@Composable
fun LiquidGlassTopHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = RoundedCornerShape(24.dp),
                hazeState = hazeState,
                blurRadius = 24.dp,
                shadowElevation = 10.dp
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Glass.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** Spring-animated sliding liquid pill segmented bar with real frosted Haze blur. */
@Composable
fun <T> GlassSegmentedBar(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current
) {
    val shape = RoundedCornerShape(26.dp)
    val pillShape = RoundedCornerShape(22.dp)
    val isDark = Glass.IsDark
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)

    val pillFill = remember(isDark) {
        if (isDark) {
            Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0.22f), Color(0xFF93C5FD).copy(alpha = 0.22f))
            )
        } else {
            Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.80f))
            )
        }
    }
    val pillBorder = diagonalGlassBorder(isDark = isDark, strong = true)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = shape,
                hazeState = hazeState,
                blurRadius = 22.dp,
                shadowElevation = 8.dp
            )
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 360f),
            label = "segmentSlide"
        )

        Box(Modifier.fillMaxWidth().height(40.dp)) {
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .then(
                        if (!isDark) Modifier.shadow(4.dp, pillShape, ambientColor = Color.Black.copy(alpha = 0.08f))
                        else Modifier
                    )
                    .clip(pillShape)
                    .background(pillFill)
                    .border(1.dp, pillBorder, pillShape)
            )

            Row(Modifier.fillMaxSize()) {
                items.forEach { item ->
                    val isSelected = item == selected
                    val interaction = remember { MutableInteractionSource() }
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(pillShape)
                            .clickable(interactionSource = interaction, indication = null) { onSelect(item) }
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon(item),
                            null,
                            tint = if (isSelected) (if (isDark) Glass.TextPrimary else Glass.Cyan) else Glass.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            label(item),
                            color = if (isSelected) Glass.TextPrimary else Glass.TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Layered Frosted Glass Cards & Pills

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && onClick != null) 0.982f else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press"
    )
    val cardFill = Glass.CardFill
    val fallbackBg = Glass.Surface
    val isDark = Glass.IsDark
    val borderBrush = diagonalGlassBorder(isDark = isDark)
    val backdropHaze = LocalBackdropHazeState.current
    val hazeStyle = remember(fallbackBg, cardFill) {
        HazeStyle(
            backgroundColor = fallbackBg,
            tint = HazeTint(cardFill),
            blurRadius = 24.dp,
            noiseFactor = 0.04f
        )
    }

    var m = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(
            elevation = if (isDark) 8.dp else 6.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.28f else 0.06f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.25f else 0.05f)
        )
        .clip(shape)

    m = if (backdropHaze != null) {
        m.hazeEffect(state = backdropHaze, style = hazeStyle)
    } else {
        m.background(cardFill)
    }

    m = m.drawBehind {
        // Top inner specular light gleam
        drawRect(
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) 0.11f else 0.45f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = 20.dp.toPx()
            )
        )
        if (accent != null) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = if (isDark) 0.14f else 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.5f, 0f),
                    radius = size.width * 0.85f
                )
            )
        }
    }.border(1.dp, borderBrush, shape)

    if (onClick != null) {
        m = m.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    Column(m.padding(contentPadding), content = content)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) = LiquidGlassCard(
    modifier = modifier,
    onClick = onClick,
    accent = accent,
    contentPadding = contentPadding,
    content = content
)

@Composable
fun LiquidGlassPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    compact: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(50)
    val isDark = Glass.IsDark
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.974f else 1f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "pillBtn"
    )

    val borderBrush = diagonalGlassBorder(isDark = isDark, strong = primary)
    val primaryBrush = remember(isDark) {
        if (isDark) {
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.34f),
                    Color(0xFF6366F1).copy(alpha = 0.30f),
                    Color(0xFF10B981).copy(alpha = 0.32f)
                )
            )
        } else {
            Brush.horizontalGradient(
                listOf(Color(0xFF0284C7), Color(0xFF059669))
            )
        }
    }
    val contentColor = when {
        primary -> Color.White
        else -> Glass.TextPrimary
    }

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.44f }
            .height(if (compact) 40.dp else 52.dp)
            .shadow(
                elevation = if (primary) (if (isDark) 10.dp else 6.dp) else 0.dp,
                shape = shape,
                ambientColor = if (primary) Glass.Cyan.copy(alpha = 0.35f) else Color.Transparent,
                spotColor = if (primary) Glass.Cyan.copy(alpha = 0.35f) else Color.Transparent
            )
            .clip(shape)
            .then(
                if (primary) {
                    Modifier
                        .background(primaryBrush)
                        .border(1.dp, borderBrush, shape)
                } else {
                    Modifier
                        .background(Glass.CardFill)
                        .border(1.dp, borderBrush, shape)
                }
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 15.dp else 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(if (compact) 17.dp else 19.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    compact: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) = LiquidGlassPill(
    text = text,
    onClick = onClick,
    modifier = modifier,
    icon = icon,
    primary = primary,
    enabled = enabled,
    compact = compact,
    trailing = trailing
)

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
    val shape = RoundedCornerShape(50)
    val isDark = Glass.IsDark
    Box(
        modifier
            .clip(shape)
            .background(
                if (selected) color.copy(alpha = if (isDark) 0.22f else 0.15f)
                else Glass.CardFillSubtle
            )
            .then(
                if (selected) Modifier.border(1.dp, color.copy(alpha = 0.55f), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            maxLines = 1,
            color = if (selected) (if (isDark) Color.White else color) else Glass.TextSecondary,
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
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Glass.CardFill,
            unfocusedContainerColor = Glass.CardFillSubtle,
            focusedBorderColor = Glass.Cyan.copy(alpha = 0.7f),
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
            uncheckedBorderColor = Color.Transparent
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
fun OnlineBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Glass.Emerald.copy(alpha = if (Glass.IsDark) 0.16f else 0.12f))
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
    bottomPadding: Dp = 118.dp,
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
