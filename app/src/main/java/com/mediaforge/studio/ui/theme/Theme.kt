package com.mediaforge.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mediaforge.studio.data.ThemeMode

@Immutable
data class GlassPalette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val cardFill: Color,
    val cardFillSubtle: Color,
    val navFill: Color,
    val stroke: Color,
    val cyan: Color,
    val emerald: Color,
    val indigo: Color,
    val amber: Color,
    val danger: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDim: Color
)

val DarkGlassPalette = GlassPalette(
    isDark = true,
    bg = Color(0xFF0B0E13),
    surface = Color(0xFF131821),
    cardFill = Color.White.copy(alpha = 0.06f),
    cardFillSubtle = Color.White.copy(alpha = 0.04f),
    navFill = Color(0xA60F131A),
    stroke = Color.White.copy(alpha = 0.12f),
    cyan = Color(0xFFB8C7D9),
    emerald = Color(0xFF8AB59D),
    indigo = Color(0xFF94A3B8),
    amber = Color(0xFFCBB28A),
    danger = Color(0xFFE07A8B),
    onAccent = Color(0xFF0B0E13),
    textPrimary = Color(0xFFF5F7FA),
    textSecondary = Color(0xFFA3AEBE),
    textDim = Color(0xFF687385)
)

val LightGlassPalette = GlassPalette(
    isDark = false,
    bg = Color(0xFFF4F6F9),
    surface = Color(0xFFFFFFFF),
    cardFill = Color.White.copy(alpha = 0.70f),
    cardFillSubtle = Color.White.copy(alpha = 0.50f),
    navFill = Color(0xBDFFFFFF),
    stroke = Color(0xFF0F172A).copy(alpha = 0.09f),
    cyan = Color(0xFF1E293B),
    emerald = Color(0xFF2F6F58),
    indigo = Color(0xFF475569),
    amber = Color(0xFF8C6232),
    danger = Color(0xFFB83B4E),
    onAccent = Color.White,
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textDim = Color(0xFF7D899B)
)

val LocalGlassPalette = staticCompositionLocalOf { DarkGlassPalette }

/** High-translucency True Liquid Glass design tokens across Light & Dark themes. */
object Glass {
    val current: GlassPalette
        @Composable @ReadOnlyComposable get() = LocalGlassPalette.current

    val IsDark: Boolean @Composable @ReadOnlyComposable get() = current.isDark
    val Bg: Color @Composable @ReadOnlyComposable get() = current.bg
    val Surface: Color @Composable @ReadOnlyComposable get() = current.surface
    val CardFill: Color @Composable @ReadOnlyComposable get() = current.cardFill
    val CardFillSubtle: Color @Composable @ReadOnlyComposable get() = current.cardFillSubtle
    val NavFill: Color @Composable @ReadOnlyComposable get() = current.navFill
    val Stroke: Color @Composable @ReadOnlyComposable get() = current.stroke
    val Cyan: Color @Composable @ReadOnlyComposable get() = current.cyan
    val Emerald: Color @Composable @ReadOnlyComposable get() = current.emerald
    val Indigo: Color @Composable @ReadOnlyComposable get() = current.indigo
    val Amber: Color @Composable @ReadOnlyComposable get() = current.amber
    val Danger: Color @Composable @ReadOnlyComposable get() = current.danger
    val OnAccent: Color @Composable @ReadOnlyComposable get() = current.onAccent
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = current.textSecondary
    val TextDim: Color @Composable @ReadOnlyComposable get() = current.textDim
}

private val DarkScheme = darkColorScheme(
    primary = DarkGlassPalette.cyan,
    onPrimary = DarkGlassPalette.onAccent,
    secondary = DarkGlassPalette.emerald,
    onSecondary = DarkGlassPalette.onAccent,
    tertiary = DarkGlassPalette.amber,
    background = DarkGlassPalette.bg,
    onBackground = DarkGlassPalette.textPrimary,
    surface = DarkGlassPalette.surface,
    onSurface = DarkGlassPalette.textPrimary,
    surfaceVariant = Color(0xFF182032),
    onSurfaceVariant = DarkGlassPalette.textSecondary,
    outline = DarkGlassPalette.stroke,
    error = DarkGlassPalette.danger
)

private val LightScheme = lightColorScheme(
    primary = LightGlassPalette.cyan,
    onPrimary = LightGlassPalette.onAccent,
    secondary = LightGlassPalette.emerald,
    onSecondary = LightGlassPalette.onAccent,
    tertiary = LightGlassPalette.amber,
    background = LightGlassPalette.bg,
    onBackground = LightGlassPalette.textPrimary,
    surface = LightGlassPalette.surface,
    onSurface = LightGlassPalette.textPrimary,
    surfaceVariant = Color(0xFFE6EEF5),
    onSurfaceVariant = LightGlassPalette.textSecondary,
    outline = LightGlassPalette.stroke,
    error = LightGlassPalette.danger
)

private val Type = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.5.sp)
)

@Composable
fun isThemeDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MediaForgeTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isThemeDark(themeMode)
    val palette = if (dark) DarkGlassPalette else LightGlassPalette
    val scheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalGlassPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}
