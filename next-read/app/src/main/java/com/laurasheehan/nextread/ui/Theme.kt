package com.laurasheehan.nextread.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** "Lavender Hour", the look she chose: soft lilac, violet for anything tappable, apricot and pink as bright notes. */
@Immutable
data class Accents(
    val apricot: Color,
    val onApricot: Color,
    val apricotSoft: Color,
    val apricotText: Color,
    val pinkSoft: Color,
    val warn: Color,
    val good: Color,
    val muted: Color,
)

private val LightAccents = Accents(
    apricot = Color(0xFFFF9A5C), onApricot = Color(0xFF3D1A00), apricotSoft = Color(0xFFFFE7D8),
    apricotText = Color(0xFF8A4210), pinkSoft = Color(0xFFFFEAF3), warn = Color(0xFFB0245F),
    good = Color(0xFF1D9A70), muted = Color(0xFF6B6488),
)

private val DarkAccents = Accents(
    apricot = Color(0xFFFFAB78), onApricot = Color(0xFF2B1200), apricotSoft = Color(0xFF3D2B25),
    apricotText = Color(0xFFFFC59E), pinkSoft = Color(0xFF3B1F36), warn = Color(0xFFFF86BB),
    good = Color(0xFF5FD3A6), muted = Color(0xFFA7A0C8),
)

val LocalAccents = staticCompositionLocalOf { LightAccents }

private val LightColors = lightColorScheme(
    primary = Color(0xFF7656F5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE6FF),
    onPrimaryContainer = Color(0xFF2A1B6E),
    secondary = Color(0xFFFF9A5C),
    onSecondary = Color(0xFF3D1A00),
    background = Color(0xFFF3F0FF),
    onBackground = Color(0xFF241D3D),
    surface = Color(0xFFF3F0FF),
    onSurface = Color(0xFF241D3D),
    surfaceVariant = Color(0xFFECE7FF),
    onSurfaceVariant = Color(0xFF6B6488),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF8F6FF),
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFCFC6F2),
    outlineVariant = Color(0xFFDDD6F5),
    error = Color(0xFFB0245F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA993FF),
    onPrimary = Color(0xFF160F33),
    primaryContainer = Color(0xFF312959),
    onPrimaryContainer = Color(0xFFE6DFFF),
    secondary = Color(0xFFFFAB78),
    onSecondary = Color(0xFF2B1200),
    background = Color(0xFF17142A),
    onBackground = Color(0xFFEDE9FF),
    surface = Color(0xFF17142A),
    onSurface = Color(0xFFEDE9FF),
    surfaceVariant = Color(0xFF2B2549),
    onSurfaceVariant = Color(0xFFA7A0C8),
    surfaceContainerLowest = Color(0xFF221D3A),
    surfaceContainerLow = Color(0xFF221D3A),
    surfaceContainer = Color(0xFF221D3A),
    surfaceContainerHigh = Color(0xFF2B2549),
    outline = Color(0xFF4A4275),
    outlineVariant = Color(0xFF383160),
    error = Color(0xFFFF86BB),
)

/** Titles in a soft bookish serif; everything else in the system's clean sans. */
private val Titles = FontFamily.Serif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Titles, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Titles, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Titles, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

val NoteStyle = TextStyle(fontFamily = Titles, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 24.sp)

@Composable
fun NextReadTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalAccents provides if (dark) DarkAccents else LightAccents) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
    }
}
