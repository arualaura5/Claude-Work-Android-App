package com.laurasheehan.royalmiles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ColorYoga
import com.laurasheehan.royalmiles.ui.theme.ComebackGold

/** A light bar trend of the last few "how did that feel" ratings — an early nudge if things are trending sore, not fresh. */
@Composable
fun EffortTrend(ratings: List<Int>, modifier: Modifier = Modifier, barHeight: Int = 48) {
    Row(
        modifier = modifier.height(barHeight.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ratings.forEach { rating ->
            val clamped = rating.coerceIn(1, 5)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction = clamped / 5f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(effortColor(clamped)),
            )
        }
    }
}

private fun effortColor(rating: Int): Color = when (rating) {
    1, 2 -> BlushPink
    3 -> ComebackGold
    else -> ColorYoga
}
