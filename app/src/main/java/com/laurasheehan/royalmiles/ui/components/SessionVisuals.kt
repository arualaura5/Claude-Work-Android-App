package com.laurasheehan.royalmiles.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.ui.theme.ColorCycle
import com.laurasheehan.royalmiles.ui.theme.ColorEasyRun
import com.laurasheehan.royalmiles.ui.theme.ColorLongRun
import com.laurasheehan.royalmiles.ui.theme.ColorRace
import com.laurasheehan.royalmiles.ui.theme.ColorRest
import com.laurasheehan.royalmiles.ui.theme.ColorStrength
import com.laurasheehan.royalmiles.ui.theme.ColorSwim
import com.laurasheehan.royalmiles.ui.theme.ColorYoga

fun SessionType.accentColor(): Color = when (this) {
    SessionType.EASY_RUN -> ColorEasyRun
    SessionType.LONG_RUN -> ColorLongRun
    SessionType.STRENGTH -> ColorStrength
    SessionType.YOGA -> ColorYoga
    SessionType.CYCLE -> ColorCycle
    SessionType.SWIM -> ColorSwim
    SessionType.REST -> ColorRest
    SessionType.RACE -> ColorRace
}

fun SessionType.icon(): ImageVector = when (this) {
    SessionType.EASY_RUN -> Icons.Filled.DirectionsRun
    SessionType.LONG_RUN -> Icons.Filled.Terrain
    SessionType.STRENGTH -> Icons.Filled.FitnessCenter
    SessionType.YOGA -> Icons.Filled.SelfImprovement
    SessionType.CYCLE -> Icons.Filled.DirectionsBike
    SessionType.SWIM -> Icons.Filled.Pool
    SessionType.REST -> Icons.Filled.Hotel
    SessionType.RACE -> Icons.Filled.EmojiEvents
}

fun SessionType.label(): String = when (this) {
    SessionType.EASY_RUN -> "Easy run"
    SessionType.LONG_RUN -> "Long run"
    SessionType.STRENGTH -> "Strength"
    SessionType.YOGA -> "Yoga"
    SessionType.CYCLE -> "Cycle"
    SessionType.SWIM -> "Swim"
    SessionType.REST -> "Rest"
    SessionType.RACE -> "Race"
}
