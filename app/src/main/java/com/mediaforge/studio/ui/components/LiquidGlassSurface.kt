package com.mediaforge.studio.ui.components

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
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

/**
 * Native GPU RenderEffect frosted glass blur is available on Android 12+ (API 31+).
 * Earlier Android versions (API 26–30) automatically fall back to a lightweight translucent
 * monochrome surface without expensive CPU blur passes.
 */
fun supportsNativeLiquidGlassBlur(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= Build.VERSION_CODES.S

/** Primary Haze state carrying ambient backdrop + scrolling content for floating navigation chrome only (API 31+). */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/** Retained for compatibility; static content cards intentionally avoid backdrop blur overdraw. */
val LocalBackdropHazeState = compositionLocalOf<HazeState?> { null }

@Composable
fun plainGlassSheetColor(isDark: Boolean = Glass.IsDark): Color =
    if (isDark) Color(0xCC0D131D) else Color(0xD9F4F8FC)

@Composable
fun plainGlassBorderColor(isDark: Boolean = Glass.IsDark, strong: Boolean = false): Color =
    if (isDark) Color.White.copy(alpha = if (strong) 0.35f else 0.24f)
    else Color.White.copy(alpha = if (strong) 0.88f else 0.72f)

/**
 * True Clear Translucent Glass vertical sheen fill:
 * Dark Mode: `0.05f` white top -> `0.015f` white bottom (clear, never muddy black).
 * Light Mode: `0.55f` white top -> `0.30f` white bottom (airy frosted refraction).
 */
@Composable
fun clearGlassFillBrush(isDark: Boolean = Glass.IsDark): Brush = remember(isDark) {
    if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.015f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.30f)
            )
        )
    }
}

/**
 * Diagonal Specular Rim Highlight (`1.dp` border):
 * Dark Mode: `0.35f` white top-left -> `0.06f` white bottom-right.
 * Light Mode: `0.85f` white top-left -> `0.20f` white bottom-right.
 */
@Composable
fun diagonalGlassBorder(isDark: Boolean = Glass.IsDark, strong: Boolean = false): Brush =
    remember(isDark, strong) {
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (strong) 0.42f else 0.35f),
                    Color.White.copy(alpha = if (strong) 0.16f else 0.12f),
                    Color.White.copy(alpha = 0.06f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (strong) 0.92f else 0.85f),
                    Color.White.copy(alpha = if (strong) 0.55f else 0.46f),
                    Color.White.copy(alpha = 0.20f)
                )
            )
        }
    }

// ------------------------------------------------------------------ Ambient Soft Light Orbs + Micro-Dot Grid Backdrop

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    backdropHaze: HazeState? = LocalBackdropHazeState.current,
    chromeHaze: HazeState? = LocalHazeState.current,
    content: @Composable BoxScope.() -> Unit
) {
    val bg = Glass.Bg
    val isDark = Glass.IsDark
    val activeChromeHaze = if (supportsNativeLiquidGlassBlur()) chromeHaze else null

    val orbPrimary = if (isDark) Color(0xFF1D4ED8).copy(alpha = 0.20f) else Color(0xFF93C5FD).copy(alpha = 0.38f)
    val orbSecondary = if (isDark) Color(0xFF0E7490).copy(alpha = 0.18f) else Color(0xFFA7F3D0).copy(alpha = 0.30f)
    val orbAccent = if (isDark) Color(0xFF1D4ED8).copy(alpha = 0.11f) else Color(0xFF93C5FD).copy(alpha = 0.20f)
    val dotColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFF0F172A).copy(alpha = 0.05f)

    // Static GPU-cached orbs + 22.dp micro-dot grid for crisp glass refraction at locked 60/120fps.
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .let { if (activeChromeHaze != null) it.hazeSource(activeChromeHaze, zIndex = 0f) else it }
                .background(bg)
                .drawWithCache {
                    val w = size.width
                    val h = size.height

                    val centerA = Offset(w * 0.20f, h * 0.16f)
                    val radiusA = (w * 0.85f).coerceAtLeast(1f)
                    val brushA = Brush.radialGradient(
                        colors = listOf(orbPrimary, Color.Transparent),
                        center = centerA,
                        radius = radiusA
                    )

                    val centerB = Offset(w * 0.82f, h * 0.74f)
                    val radiusB = (w * 0.82f).coerceAtLeast(1f)
                    val brushB = Brush.radialGradient(
                        colors = listOf(orbSecondary, Color.Transparent),
                        center = centerB,
                        radius = radiusB
                    )

                    val centerC = Offset(w * 0.78f, h * 0.30f)
                    val radiusC = (w * 0.62f).coerceAtLeast(1f)
                    val brushC = Brush.radialGradient(
                        colors = listOf(orbAccent, Color.Transparent),
                        center = centerC,
                        radius = radiusC
                    )

                    val spacingPx = 22.dp.toPx().coerceAtLeast(12f)
                    val dotSizePx = 1.4.dp.toPx().coerceAtLeast(1.5f)
                    val cols = (w / spacingPx).toInt()
                    val rows = (h / spacingPx).toInt()
                    val points = ArrayList<Offset>((cols + 1) * (rows + 1))
                    var y = spacingPx * 0.5f
                    while (y < h) {
                        var x = spacingPx * 0.5f
                        while (x < w) {
                            points.add(Offset(x, y))
                            x += spacingPx
                        }
                        y += spacingPx
                    }

                    onDrawBehind {
                        drawCircle(brush = brushA, center = centerA, radius = radiusA)
                        drawCircle(brush = brushC, center = centerC, radius = radiusC)
                        drawCircle(brush = brushB, center = centerB, radius = radiusB)
                        if (points.isNotEmpty()) {
                            drawPoints(
                                points = points,
                                pointMode = PointMode.Points,
                                color = dotColor,
                                strokeWidth = dotSizePx,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
        )
        content()
    }
}

@Composable
fun LiquidBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    LiquidGlassBackdrop(modifier = modifier, content = content)

// ------------------------------------------------------------------ True Clear Liquid Glass Chrome & Docks

/**
 * Applies clear frosted Liquid Glass chrome to floating navigation bars & dialogs:
 * - Light `12.dp–16.dp` GPU RenderEffect Haze blur on Android 12+ (API 31+) with clear tint
 * - Diagonal specular rim highlight (`0.35f -> 0.06f` Dark / `0.85f -> 0.20f` Light)
 * - Lightweight non-blur translucent fallback on Android 8–11 (API 26–30)
 */
@Composable
fun Modifier.liquidGlassChrome(
    shape: Shape = RoundedCornerShape(28.dp),
    hazeState: HazeState? = LocalHazeState.current,
    blurRadius: Dp = 14.dp,
    shadowElevation: Dp = 12.dp,
    tintColor: Color = Glass.NavFill,
    strongBorder: Boolean = false
): Modifier {
    val isDark = Glass.IsDark
    val fallbackBg = Glass.Bg
    val fallbackSheet = plainGlassSheetColor(isDark = isDark)
    val glassFill = clearGlassFillBrush(isDark = isDark)
    val borderBrush = diagonalGlassBorder(isDark = isDark, strong = strongBorder)
    val activeHaze = if (supportsNativeLiquidGlassBlur()) hazeState else null

    val base = this
        .shadow(
            elevation = if (isDark) shadowElevation else (shadowElevation * 0.55f),
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.34f else 0.08f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.34f else 0.07f)
        )
        .clip(shape)

    val frosted = if (activeHaze != null) {
        val hazeStyle = remember(fallbackBg, tintColor, blurRadius) {
            HazeStyle(
                backgroundColor = fallbackBg,
                tint = HazeTint(tintColor),
                blurRadius = blurRadius,
                noiseFactor = 0.02f
            )
        }
        base
            .hazeEffect(state = activeHaze, style = hazeStyle)
            .background(glassFill)
    } else {
        base
            .background(fallbackSheet)
            .background(glassFill)
    }
    return frosted.border(1.dp, borderBrush, shape)
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
                blurRadius = 14.dp,
                shadowElevation = 8.dp
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
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f))
            )
        } else {
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.90f), Color.White.copy(alpha = 0.64f))
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
                blurRadius = 14.dp,
                shadowElevation = 6.dp
            )
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
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

// ------------------------------------------------------------------ True Clear Translucent Content Cards (Zero Blur Overdraw)

/**
 * True Clear Translucent Liquid Glass card:
 * - Dark Mode: `0.05f -> 0.015f` white vertical sheen + `0.35f -> 0.06f` diagonal specular border.
 * - Light Mode: `0.55f -> 0.30f` white vertical sheen + `0.85f -> 0.20f` diagonal specular border.
 * Avoids GPU blur overdraw on scrolling content and only allocates press-scale state when clickable.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val isDark = Glass.IsDark
    val glassFill = clearGlassFillBrush(isDark = isDark)
    val borderBrush = diagonalGlassBorder(isDark = isDark, strong = false)

    val interactiveModifier = if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.982f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "press"
        )
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(glassFill)
            .border(1.dp, borderBrush, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else {
        modifier
            .clip(shape)
            .background(glassFill)
            .border(1.dp, borderBrush, shape)
    }

    Column(interactiveModifier.padding(contentPadding), content = content)
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
