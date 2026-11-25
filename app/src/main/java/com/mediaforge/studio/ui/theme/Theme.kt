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
    bg = Color(0xFF090C10),
    surface = Color(0xFF101622),
    cardFill = Color.White.copy(alpha = 0.045f),
    cardFillSubtle = Color.White.copy(alpha = 0.03f),
    navFill = Color.White.copy(alpha = 0.06f),
    stroke = Color.White.copy(alpha = 0.16f),
    cyan = Color(0xFF93C5FD),
    emerald = Color(0xFF6EE7B7),
    indigo = Color(0xFF94A3B8),
    amber = Color(0xFFFCD34D),
    danger = Color(0xFFF472B6),
    onAccent = Color(0xFF090C10),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFB4C0D2),
    textDim = Color(0xFF728199)
)

val LightGlassPalette = GlassPalette(
    isDark = false,
    bg = Color(0xFFEDF2F7),
    surface = Color(0xFFF8FAFC),
    cardFill = Color.White.copy(alpha = 0.48f),
    cardFillSubtle = Color.White.copy(alpha = 0.36f),
    navFill = Color.White.copy(alpha = 0.50f),
    stroke = Color.White.copy(alpha = 0.65f),
    cyan = Color(0xFF1D4ED8),
    emerald = Color(0xFF0F766E),
    indigo = Color(0xFF475569),
    amber = Color(0xFFB45309),
    danger = Color(0xFFBE123C),
    onAccent = Color.White,
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textDim = Color(0xFF64748B)
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
