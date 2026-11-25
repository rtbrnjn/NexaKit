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
    if (isDark) Color(0xD6101829) else Color(0xE0F2F7FC)

@Composable
fun plainGlassBorderColor(isDark: Boolean = Glass.IsDark, strong: Boolean = false): Color =
    if (isDark) Color.White.copy(alpha = if (strong) 0.38f else 0.26f)
    else Color.White.copy(alpha = if (strong) 0.90f else 0.76f)

/**
 * True Clear Translucent Glass vertical sheen fill:
 * Dark Mode: `0.06f` white top -> `0.018f` white bottom over luminous aurora orbs.
 * Light Mode: `0.58f` white top -> `0.30f` white bottom floating cleanly over daylight studio orbs.
 */
@Composable
fun clearGlassFillBrush(isDark: Boolean = Glass.IsDark): Brush = remember(isDark) {
    if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.018f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.58f),
                Color.White.copy(alpha = 0.30f)
            )
        )
    }
}

/**
 * Diagonal Specular Rim Highlight (`1.dp` border):
 * Dark Mode: `0.38f` white top-left -> `0.08f` white bottom-right.
 * Light Mode: `0.88f` white top-left -> `0.24f` white bottom-right.
 */
@Composable
fun diagonalGlassBorder(isDark: Boolean = Glass.IsDark, strong: Boolean = false): Brush =
    remember(isDark, strong) {
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (strong) 0.45f else 0.38f),
                    Color.White.copy(alpha = if (strong) 0.18f else 0.14f),
                    Color.White.copy(alpha = 0.08f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (strong) 0.94f else 0.88f),
                    Color.White.copy(alpha = if (strong) 0.58f else 0.48f),
                    Color.White.copy(alpha = 0.24f)
                )
            )
        }
    }

// ------------------------------------------------------------------ Luminous Deep Aurora & Daylight Studio Backdrop

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    backdropHaze: HazeState? = LocalBackdropHazeState.current,
    chromeHaze: HazeState? = LocalHazeState.current,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = Glass.IsDark
    val activeChromeHaze = if (supportsNativeLiquidGlassBlur()) chromeHaze else null

    val baseTop = if (isDark) Color(0xFF10192D) else Color(0xFFF7FAFE)
    val baseMid = if (isDark) Color(0xFF0D1320) else Color(0xFFF0F5FA)
    val baseBot = if (isDark) Color(0xFF0B101C) else Color(0xFFE9F1F9)

    // Luminous Mesh Orbs tuned for vivid glass refraction while preserving WCAG AA text contrast everywhere.
    val orbPrimary = if (isDark) Color(0xFF2563EB).copy(alpha = 0.32f) else Color(0xFF60A5FA).copy(alpha = 0.42f)
    val orbSecondary = if (isDark) Color(0xFF06B6D4).copy(alpha = 0.26f) else Color(0xFF34D399).copy(alpha = 0.32f)
    val orbTertiary = if (isDark) Color(0xFF6366F1).copy(alpha = 0.24f) else Color(0xFFA78BFA).copy(alpha = 0.30f)
    val beamPrimary = if (isDark) Color.White.copy(alpha = 0.055f) else Color.White.copy(alpha = 0.40f)
    val beamTint = if (isDark) Color(0xFF38BDF8).copy(alpha = 0.05f) else Color(0xFF93C5FD).copy(alpha = 0.14f)
    val dotColor = if (isDark) Color.White.copy(alpha = 0.055f) else Color(0xFF0F172A).copy(alpha = 0.055f)

    // Static GPU-cached architectural aurora & daylight studio canvas (zero per-frame invalidation, 60/120fps locked).
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .let { if (activeChromeHaze != null) it.hazeSource(activeChromeHaze, zIndex = 0f) else it }
                .drawWithCache {
                    val w = size.width
                    val h = size.height

                    val baseWash = Brush.verticalGradient(
                        colors = listOf(baseTop, baseMid, baseBot),
                        startY = 0f,
                        endY = h
                    )

                    val centerCobalt = Offset(w * 0.14f, h * 0.13f)
                    val radiusCobalt = (w * 0.94f).coerceAtLeast(1f)
                    val brushCobalt = Brush.radialGradient(
                        colors = listOf(orbPrimary, orbPrimary.copy(alpha = orbPrimary.alpha * 0.35f), Color.Transparent),
                        center = centerCobalt,
                        radius = radiusCobalt
                    )

                    val centerCyan = Offset(w * 0.90f, h * 0.44f)
                    val radiusCyan = (w * 0.86f).coerceAtLeast(1f)
                    val brushCyan = Brush.radialGradient(
                        colors = listOf(orbSecondary, orbSecondary.copy(alpha = orbSecondary.alpha * 0.32f), Color.Transparent),
                        center = centerCyan,
                        radius = radiusCyan
                    )

                    val centerViolet = Offset(w * 0.22f, h * 0.84f)
                    val radiusViolet = (w * 0.92f).coerceAtLeast(1f)
                    val brushViolet = Brush.radialGradient(
                        colors = listOf(orbTertiary, orbTertiary.copy(alpha = orbTertiary.alpha * 0.30f), Color.Transparent),
                        center = centerViolet,
                        radius = radiusViolet
                    )

                    // Subtle architectural diagonal light beam to anchor glass specular rim refraction
                    val diagonalBeam = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.36f to Color.Transparent,
                            0.48f to beamPrimary,
                            0.55f to beamTint,
                            0.67f to Color.Transparent,
                            1.00f to Color.Transparent
                        ),
                        start = Offset(0f, h * 0.08f),
                        end = Offset(w, h * 0.72f)
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
                        drawRect(brush = baseWash)
                        drawCircle(brush = brushCobalt, center = centerCobalt, radius = radiusCobalt)
                        drawCircle(brush = brushCyan, center = centerCyan, radius = radiusCyan)
                        drawCircle(brush = brushViolet, center = centerViolet, radius = radiusViolet)
                        drawRect(brush = diagonalBeam)
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
