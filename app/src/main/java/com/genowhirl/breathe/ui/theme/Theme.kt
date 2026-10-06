package com.genowhirl.breathe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.genowhirl.breathe.model.Phase
import com.genowhirl.breathe.model.ThemeMode

/** One colour per stage of the breath, shared by the app, the notification and the widget. */
object PhaseColors {
    val inhale = Color(0xFF6FD3BE)
    val holdIn = Color(0xFFB39DF0)
    val exhale = Color(0xFF6E9FF0)
    val holdOut = Color(0xFFF2AE94)

    fun of(phase: Phase): Color = when (phase) {
        Phase.INHALE -> inhale
        Phase.HOLD_IN -> holdIn
        Phase.EXHALE -> exhale
        Phase.HOLD_OUT -> holdOut
    }

    fun argb(phase: Phase): Int = of(phase).toArgb()
}

@Immutable
data class Ambience(
    val top: Color,
    val bottom: Color,
    val glow: Color,
    val track: Color,
    val isDark: Boolean,
)

val LocalAmbience = staticCompositionLocalOf {
    Ambience(Color(0xFF0D1324), Color(0xFF1B2440), Color(0xFF2A3A66), Color(0x33FFFFFF), true)
}

private val NightAmbience = Ambience(
    top = Color(0xFF0B1020),
    bottom = Color(0xFF1A2342),
    glow = Color(0xFF34477E),
    track = Color(0x26FFFFFF),
    isDark = true,
)

private val DayAmbience = Ambience(
    top = Color(0xFFF6F3EE),
    bottom = Color(0xFFE3EAF6),
    glow = Color(0xFFCBD8F2),
    track = Color(0x1F1B2440),
    isDark = false,
)

private val NightScheme = darkColorScheme(
    primary = PhaseColors.inhale,
    onPrimary = Color(0xFF06251E),
    primaryContainer = Color(0xFF1F3B44),
    onPrimaryContainer = Color(0xFFCFF3EA),
    secondary = PhaseColors.holdIn,
    onSecondary = Color(0xFF1E1240),
    secondaryContainer = Color(0xFF2E2A52),
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiary = PhaseColors.holdOut,
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF131A30),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF1C2540),
    onSurfaceVariant = Color(0xFFA9B2CC),
    surfaceContainer = Color(0xFF161E37),
    surfaceContainerHigh = Color(0xFF1C2540),
    surfaceContainerHighest = Color(0xFF232D4C),
    outline = Color(0xFF3A4566),
    outlineVariant = Color(0xFF283252),
)

private val DayScheme = lightColorScheme(
    primary = Color(0xFF1F8F7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF0E7),
    onPrimaryContainer = Color(0xFF00382D),
    secondary = Color(0xFF6A55C2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6DEFF),
    onSecondaryContainer = Color(0xFF22134F),
    tertiary = Color(0xFFC0694A),
    background = Color(0xFFF6F3EE),
    onBackground = Color(0xFF1B2133),
    surface = Color(0xFFFFFCF8),
    onSurface = Color(0xFF1B2133),
    surfaceVariant = Color(0xFFE9ECF3),
    onSurfaceVariant = Color(0xFF545C72),
    surfaceContainer = Color(0xFFF1EFEB),
    surfaceContainerHigh = Color(0xFFEBE9E6),
    surfaceContainerHighest = Color(0xFFE4E3E1),
    outline = Color(0xFFB8BECC),
    outlineVariant = Color(0xFFD9DDE6),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.ExtraLight, letterSpacing = (-1).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.ExtraLight),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Light),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Light),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Light, letterSpacing = 0.5.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Normal),
        labelLarge = t.labelLarge.copy(letterSpacing = 0.6.sp),
    )
}

val PhaseLabelStyle = TextStyle(fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = 2.sp)

@Composable
fun BreatheTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalAmbience provides if (dark) NightAmbience else DayAmbience) {
        MaterialTheme(
            colorScheme = if (dark) NightScheme else DayScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
