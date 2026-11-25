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
    if (isDark) Color(0xF211151E) else Color(0xF5F4F6F9)

@Composable
fun plainGlassBorderColor(isDark: Boolean = Glass.IsDark, strong: Boolean = false): Color =
    if (isDark) Color.White.copy(alpha = if (strong) 0.16f else 0.12f)
    else Color(0xFF0F172A).copy(alpha = if (strong) 0.13f else 0.09f)

@Composable
fun diagonalGlassBorder(isDark: Boolean = Glass.IsDark, strong: Boolean = false): Brush {
    val stroke = plainGlassBorderColor(isDark = isDark, strong = strong)
    return remember(stroke) {
        Brush.linearGradient(colors = listOf(stroke, stroke))
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
    val activeChromeHaze = if (supportsNativeLiquidGlassBlur()) chromeHaze else null

    val glowPrimary = Color.White
    val glowSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFFCBD5E1)
    val alphaA = if (isDark) 0.036f else 0.54f
    val alphaB = if (isDark) 0.028f else 0.20f

    // Static cached mist avoids continuous 60/120fps infinite-transition invalidation on the backdrop.
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .let { if (activeChromeHaze != null) it.hazeSource(activeChromeHaze, zIndex = 0f) else it }
                .background(bg)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val centerA = Offset(w * 0.26f, h * 0.19f)
                    val radiusA = (w * 0.80f).coerceAtLeast(1f)
                    val brushA = Brush.radialGradient(
                        colors = listOf(glowPrimary.copy(alpha = alphaA), Color.Transparent),
                        center = centerA,
                        radius = radiusA
                    )
                    val centerB = Offset(w * 0.76f, h * 0.71f)
                    val radiusB = (w * 0.74f).coerceAtLeast(1f)
                    val brushB = Brush.radialGradient(
                        colors = listOf(glowSecondary.copy(alpha = alphaB), Color.Transparent),
                        center = centerB,
                        radius = radiusB
                    )
                    onDrawBehind {
                        drawCircle(brush = brushA, center = centerA, radius = radiusA)
                        drawCircle(brush = brushB, center = centerB, radius = radiusB)
                    }
                }
        )
        content()
    }
}

@Composable
fun LiquidBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    LiquidGlassBackdrop(modifier = modifier, content = content)

// ------------------------------------------------------------------ True Liquid Glass Chrome & Docks

/**
 * Applies Liquid Glass frosted chrome to floating navigation controls (top header, bottom dock,
 * segmented bar). Uses native GPU RenderEffect Haze blur on Android 12+ (API 31+) and falls back
 * gracefully to a clean, low-cost translucent monochrome sheet on Android 8–11 (API 26–30).
 */
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
    val fallbackSheet = plainGlassSheetColor(isDark = isDark)
    val borderColor = plainGlassBorderColor(isDark = isDark, strong = strongBorder)
    val activeHaze = if (supportsNativeLiquidGlassBlur()) hazeState else null

    val base = this
        .shadow(
            elevation = if (isDark) shadowElevation else (shadowElevation * 0.65f),
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.09f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.08f)
        )
        .clip(shape)

    val frosted = if (activeHaze != null) {
        val hazeStyle = remember(fallbackBg, tintColor, blurRadius) {
            HazeStyle(
                backgroundColor = fallbackBg,
                tint = HazeTint(tintColor),
                blurRadius = blurRadius,
                noiseFactor = 0.03f
            )
        }
        base.hazeEffect(state = activeHaze, style = hazeStyle)
    } else {
        base.background(fallbackSheet)
    }
    return frosted.border(1.dp, borderColor, shape)
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

// ------------------------------------------------------------------ Plain Translucent Content Cards (Zero Blur Overdraw)

/**
 * Monochrome translucent content card (`0.06f` Dark / `0.70f` Light + `1.dp` border).
 * Intentionally avoids GPU RenderEffect blur and shadow overdraw inside scrolling lists, and only
 * attaches press-scale interaction state when [onClick] is provided.
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
    val cardFill = Glass.CardFill
    val borderColor = plainGlassBorderColor(isDark = Glass.IsDark)

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
            .background(cardFill)
            .border(1.dp, borderColor, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else {
        modifier
            .clip(shape)
            .background(cardFill)
            .border(1.dp, borderColor, shape)
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
