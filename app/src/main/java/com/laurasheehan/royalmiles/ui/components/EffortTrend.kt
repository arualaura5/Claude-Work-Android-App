package com.laurasheehan.royalmiles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.laurasheehan.royalmiles.core.progress.EffortReading
import com.laurasheehan.royalmiles.ui.theme.ColorYoga
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import java.time.format.DateTimeFormatter

private val effortDateFormat = DateTimeFormatter.ofPattern("d/M")

/**
 * The last few "how did that feel" ratings, tallest and greenest being the best sessions.
 *
 * Each bar is dated and labelled. Unlabelled bars meant the chart could only be read by someone who
 * already remembered every session in it, which is nobody.
 */
@Composable
fun EffortTrend(readings: List<EffortReading>, modifier: Modifier = Modifier, barHeight: Int = 48) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            readings.forEach { reading ->
                val clamped = reading.rating.coerceIn(1, 5)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((barHeight * clamped / 5f).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(effortColor(clamped)),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            readings.forEach { reading ->
                Text(
                    reading.date.format(effortDateFormat),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Text(
            "1 rough · 5 strong",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Rough reads red, strong reads green.
 *
 * It used to render 1-2 in blush pink, which is a celebration colour everywhere else in this app —
 * so the worst sessions looked like the best ones.
 */
@Composable
private fun effortColor(rating: Int): Color = when (rating) {
    1, 2 -> MaterialTheme.colorScheme.error
    3 -> ComebackGold
    else -> ColorYoga
}
