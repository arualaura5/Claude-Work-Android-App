package com.laura.royaltasks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

// Bolder than Material's baseline throughout, labels tracked out — Royal Miles type.
val RoyalTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontWeight = FontWeight.Medium),
    labelLarge = Base.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelSmall = Base.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
)

/** Tabular figures, for numbers that sit in columns or tick over. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
