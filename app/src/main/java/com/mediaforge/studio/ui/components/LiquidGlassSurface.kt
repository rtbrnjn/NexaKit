package com.mediaforge.studio.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.ui.theme.Glass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** Primary Haze state carrying both ambient backdrop (zIndex 0) and scrolling UI content (zIndex 1) for floating bars. */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/** Backdrop-only Haze state carrying only `LiquidGlassBackdrop` so cards blur glowing ambient blobs cleanly without cycles. */
val LocalBackdropHazeState = compositionLocalOf<HazeState?> { null }

@Composable
fun diagonalGlassBorder(isDark: Boolean = Glass.IsDark, strong: Boolean = false): Brush {
    val startAlpha = when {
        isDark && strong -> 0.22f
        isDark -> 0.14f
        strong -> 0.92f
        else -> 0.72f
    }
    val endAlpha = if (isDark) 0.10f else 0.16f
    val startColor = if (isDark) Color.White else Color(0xFFCBD5E1)
    val endColor = if (isDark) Color.White else Color(0xFF94A3B8)
    return remember(isDark, strong) {
        Brush.linearGradient(
            colors = listOf(startColor.copy(alpha = startAlpha), endColor.copy(alpha = endAlpha)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }
}

// ------------------------------------------------------------------ Minimal Monochrome Ambient Backdrop

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
        animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blobA"
    )
    val driftB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blobB"
    )

    val glowPrimary = if (isDark) Color.White else Color.White
    val glowSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFFCBD5E1)

    val alphaA = if (isDark) (0.032f + 0.012f * driftA) else (0.50f + 0.08f * driftA)
    val alphaB = if (isDark) (0.025f + 0.010f * driftB) else (0.18f + 0.05f * driftB)

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .let { if (backdropHaze != null) it.hazeSource(backdropHaze, zIndex = 0f) else it }
                .let { if (chromeHaze != null) it.hazeSource(chromeHaze, zIndex = 0f) else it }
                .background(bg)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val centerA = Offset(w * (0.24f + 0.06f * driftA), h * (0.18f + 0.04f * driftB))
                    drawCircle(
                        brush = Brush.radialGradient(listOf(glowPrimary.copy(alpha = alphaA), Color.Transparent), centerA, w * 0.80f),
                        center = centerA,
                        radius = w * 0.80f
                    )
                    val centerB = Offset(w * (0.78f - 0.06f * driftB), h * (0.72f - 0.05f * driftA))
                    drawCircle(
                        brush = Brush.radialGradient(listOf(glowSecondary.copy(alpha = alphaB), Color.Transparent), centerB, w * 0.74f),
                        center = centerB,
                        radius = w * 0.74f
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
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = if (isDark) 0.14f else 0.50f), Color.Transparent),
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
        if (isDark) Color.White.copy(alpha = 0.14f)
        else Color.White.copy(alpha = 0.90f)
    }
    val pillBorder = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0xFF0F172A).copy(alpha = 0.10f)

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

// ------------------------------------------------------------------ Layered Frosted Glass Cards

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
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = if (isDark) 0.05f else 0.35f), Color.Transparent),
                startY = 0f,
                endY = 16.dp.toPx()
            )
        )
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
