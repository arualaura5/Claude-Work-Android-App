package com.laura.royaltasks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Every role the UI touches is defined explicitly in both themes, so Material
// never substitutes a baseline colour from outside the palette.
private val LightColors = lightColorScheme(
    primary = RoyalPurple,
    onPrimary = Color.White,
    primaryContainer = CloudLavender,
    onPrimaryContainer = InkOnLight,
    inversePrimary = RoyalPurpleLight,
    secondary = BlushPink,
    onSecondary = Color.White,
    secondaryContainer = CloudLavender,
    onSecondaryContainer = InkOnLight,
    tertiary = RoyalPurple,
    onTertiary = Color.White,
    tertiaryContainer = CloudLavender,
    onTertiaryContainer = InkOnLight,
    background = CloudLavender,
    onBackground = InkOnLight,
    surface = Color.White,
    onSurface = InkOnLight,
    surfaceVariant = CloudLavender,
    onSurfaceVariant = MutedOnLight,
    surfaceTint = RoyalPurple,
    inverseSurface = InkOnLight,
    inverseOnSurface = CloudLavender,
    outline = ShimmerSilverDim,
    outlineVariant = ShimmerSilver,
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = CloudLavender,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = CloudLavender
)

private val DarkColors = darkColorScheme(
    primary = RoyalPurpleLight,
    onPrimary = MidnightNavy,
    primaryContainer = DeepPurpleContainer,
    onPrimaryContainer = CloudLavender,
    inversePrimary = RoyalPurple,
    secondary = BlushPink,
    onSecondary = MidnightNavy,
    secondaryContainer = DeepPurpleContainer,
    onSecondaryContainer = CloudLavender,
    tertiary = RoyalPurpleLight,
    onTertiary = MidnightNavy,
    tertiaryContainer = DeepPurpleContainer,
    onTertiaryContainer = CloudLavender,
    background = MidnightNavy,
    onBackground = CloudLavender,
    surface = NavySurface,
    onSurface = CloudLavender,
    surfaceVariant = NavySurfaceHigh,
    onSurfaceVariant = ShimmerSilverDim,
    surfaceTint = RoyalPurpleLight,
    inverseSurface = CloudLavender,
    inverseOnSurface = InkOnLight,
    outline = ShimmerSilverDim,
    outlineVariant = NavySurfaceHigh,
    scrim = Color.Black,
    surfaceBright = NavySurfaceHigh,
    surfaceDim = MidnightNavy,
    surfaceContainerLowest = MidnightNavy,
    surfaceContainerLow = NavySurface,
    surfaceContainer = NavySurface,
    surfaceContainerHigh = NavySurfaceHigh,
    surfaceContainerHighest = NavySurfaceHigh
)

@Composable
fun RoyalTasksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RoyalTypography,
        content = content
    )
}
