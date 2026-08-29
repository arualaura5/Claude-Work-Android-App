package com.laurasheehan.royalmiles.ui.theme

import androidx.compose.ui.graphics.Color

// Royal Miles palette: deep purple + navy grounded, gold + pink for celebration, silver for shimmer.
val RoyalPurple = Color(0xFF6C3CE9)
val RoyalPurpleDeep = Color(0xFF4A22B0)
val RoyalPurpleLight = Color(0xFFB79BFF)
val MidnightNavy = Color(0xFF13102B)
val NavySurface = Color(0xFF1E1A3F)
val NavySurfaceElevated = Color(0xFF29234F)
val ComebackGold = Color(0xFFF5B700)
val ComebackGoldSoft = Color(0xFFFFD966)
val BlushPink = Color(0xFFFF4FA3)
val BlushPinkSoft = Color(0xFFFF9FCB)
val ShimmerSilver = Color(0xFFD9DBEA)
val ShimmerSilverDim = Color(0xFF9EA2C4)
val CloudLavender = Color(0xFFF3F0FF)
val InkOnLight = Color(0xFF1B1533)
/** Secondary text on light surfaces. ShimmerSilverDim is tuned for navy and washes out on white. */
val MutedOnLight = Color(0xFF615D80)

// "Didn't do it". Deliberate rather than inherited: Material's default error red is tuned for a
// different palette and reads as an app-level alert. Two values because a red legible on navy is
// washed out on white and vice versa.
val SkippedRed = Color(0xFFFF6B6B)
val SkippedRedOnLight = Color(0xFFC62F2F)

// Session-type accent colors, used consistently across cards, chips, and the calendar.
val ColorEasyRun = Color(0xFF57C4FF)
val ColorLongRun = Color(0xFFB79BFF)
val ColorStrength = Color(0xFFFF4FA3)
val ColorYoga = Color(0xFF6FE3B4)
val ColorCycle = Color(0xFFF5B700)
val ColorRest = Color(0xFF9EA2C4)
val ColorRace = Color(0xFFFF7A45)
