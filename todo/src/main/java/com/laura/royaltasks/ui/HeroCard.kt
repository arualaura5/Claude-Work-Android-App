package com.laura.royaltasks.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laura.royaltasks.data.DAILY_GOAL
import com.laura.royaltasks.data.Progress
import com.laura.royaltasks.data.titleFor
import com.laura.royaltasks.ui.theme.ComebackGold
import com.laura.royaltasks.ui.theme.tabular

private val OnHero = Color.White
private val OnHeroMuted = Color.White.copy(alpha = 0.85f)

@Composable
fun HeroCard(
    progress: Progress,
    today: Long,
    doneToday: Int,
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = progress.xpIntoLevel / progress.xpForThisLevel.toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "xp"
    )
    val streak = progress.streakAsOf(today)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RoyalGradient)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "LEVEL ${progress.level}",
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = OnHeroMuted,
                modifier = Modifier.weight(1f)
            )
            if (streak > 0) {
                Text(
                    text = "🔥 $streak-day streak",
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = OnHero
                )
            }
            IconButton(onClick = onToggleSound) {
                Icon(
                    imageVector = if (soundEnabled) {
                        Icons.AutoMirrored.Filled.VolumeUp
                    } else {
                        Icons.AutoMirrored.Filled.VolumeOff
                    },
                    contentDescription = if (soundEnabled) "Mute sounds" else "Turn sounds on",
                    tint = OnHeroMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(Modifier.padding(end = 12.dp)) {
            Text(
                text = progress.title,
                style = MaterialTheme.typography.headlineLarge,
                color = OnHero
            )
            Spacer(Modifier.height(14.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(ComebackGold)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    text = "${progress.xpIntoLevel} / ${progress.xpForThisLevel} XP",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = OnHeroMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Next: ${titleFor(progress.level + 1)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnHeroMuted
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(DAILY_GOAL) { i ->
                    Text(
                        text = "👑",
                        fontSize = 20.sp,
                        modifier = Modifier.alpha(if (i < doneToday) 1f else 0.3f)
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = dailyLine(doneToday),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = OnHero
                )
            }
        }
    }
}

private fun dailyLine(doneToday: Int): String = when {
    doneToday == 0 -> "Pick one. Knock it out."
    doneToday < DAILY_GOAL -> "${DAILY_GOAL - doneToday} more for today's crown."
    else -> "Crown earned. Royal work."
}
