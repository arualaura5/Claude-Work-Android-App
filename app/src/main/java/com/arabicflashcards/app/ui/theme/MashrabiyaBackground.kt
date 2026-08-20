package com.arabicflashcards.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A faint, tiled diamond lattice evoking mashrabiya screenwork — meant to sit
 * behind content at very low opacity, not to be read as a foreground pattern.
 */
@Composable
fun MashrabiyaBackground(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    alpha: Float = 0.05f
) {
    val tileDp = 44.dp
    Canvas(modifier = modifier.fillMaxSize()) {
        val tile = tileDp.toPx()
        val strokeStyle = Stroke(width = 1.2f)
        val lineColor = color.copy(alpha = alpha)
        val half = tile / 2.3f

        var y = -tile
        while (y < size.height + tile) {
            var x = -tile
            while (x < size.width + tile) {
                val cx = x + tile / 2
                val cy = y + tile / 2
                val diamond = Path().apply {
                    moveTo(cx, cy - half)
                    lineTo(cx + half, cy)
                    lineTo(cx, cy + half)
                    lineTo(cx - half, cy)
                    close()
                }
                drawPath(diamond, color = lineColor, style = strokeStyle)
                x += tile
            }
            y += tile
        }
    }
}
