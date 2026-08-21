package com.arabicflashcards.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Nile,
    onPrimary = Cream,
    primaryContainer = NileLight,
    onPrimaryContainer = Cream,
    secondary = Gold,
    onSecondary = Charcoal,
    secondaryContainer = GoldLight,
    onSecondaryContainer = Charcoal,
    tertiary = Terracotta,
    onTertiary = Cream,
    tertiaryContainer = TerracottaLight,
    onTertiaryContainer = Charcoal,
    background = Cream,
    onBackground = Charcoal,
    surface = Sand,
    onSurface = Charcoal,
    surfaceVariant = Sand,
    onSurfaceVariant = Charcoal.copy(alpha = 0.7f),
    outline = NileLight,
    outlineVariant = Sand,
    error = ErrorRed
)

private val DarkColors = darkColorScheme(
    primary = GoldLight,
    onPrimary = NileDark,
    primaryContainer = Nile,
    onPrimaryContainer = GoldLight,
    secondary = Gold,
    onSecondary = NileDark,
    secondaryContainer = NileLight,
    onSecondaryContainer = Cream,
    tertiary = TerracottaLight,
    onTertiary = NileDark,
    tertiaryContainer = Terracotta,
    onTertiaryContainer = Cream,
    background = NileDark,
    onBackground = Cream,
    surface = Nile,
    onSurface = Cream,
    surfaceVariant = Nile,
    onSurfaceVariant = Cream.copy(alpha = 0.7f),
    outline = TerracottaLight,
    outlineVariant = Nile,
    error = ErrorRed
)

@Composable
fun ArabicFlashcardsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        androidx.compose.runtime.SideEffect {
            activity?.window?.let { window ->
                window.statusBarColor = colorScheme.primary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
