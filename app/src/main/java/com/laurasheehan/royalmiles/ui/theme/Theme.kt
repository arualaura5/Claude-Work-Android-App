package com.laurasheehan.royalmiles.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RoyalMilesDarkColors = darkColorScheme(
    primary = RoyalPurpleLight,
    onPrimary = MidnightNavy,
    primaryContainer = RoyalPurpleDeep,
    onPrimaryContainer = ShimmerSilver,
    secondary = ComebackGold,
    onSecondary = MidnightNavy,
    tertiary = BlushPink,
    onTertiary = MidnightNavy,
    background = MidnightNavy,
    onBackground = ShimmerSilver,
    surface = NavySurface,
    onSurface = ShimmerSilver,
    surfaceVariant = NavySurfaceElevated,
    onSurfaceVariant = ShimmerSilverDim,
    outline = ShimmerSilverDim,
)

private val RoyalMilesLightColors = lightColorScheme(
    primary = RoyalPurple,
    onPrimary = Color.White,
    primaryContainer = RoyalPurpleLight,
    onPrimaryContainer = MidnightNavy,
    secondary = ComebackGold,
    onSecondary = InkOnLight,
    tertiary = BlushPink,
    onTertiary = Color.White,
    background = CloudLavender,
    onBackground = InkOnLight,
    surface = Color.White,
    onSurface = InkOnLight,
    surfaceVariant = CloudLavender,
    onSurfaceVariant = MutedOnLight,
    outline = MutedOnLight,
)

@Composable
fun RoyalMilesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) RoyalMilesDarkColors else RoyalMilesLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RoyalMilesTypography,
        content = content,
    )
}
