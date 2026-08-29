package com.laurasheehan.royalmiles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.laurasheehan.royalmiles.core.gamification.Badge
import com.laurasheehan.royalmiles.core.progress.WeekSummary
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.theme.ComebackGoldSoft
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import com.laurasheehan.royalmiles.ui.theme.RoyalPurpleLight
import java.time.format.DateTimeFormatter

private val wrapDateFormat = DateTimeFormatter.ofPattern("d MMM")

/**
 * The long-run ladder: 8, 11, 14, 17, then the race.
 *
 * The one picture that says "you are rebuilding" in the units an endurance athlete actually feels,
 * and safe in a way almost nothing else here would be — distance only, never pace, so no bar on it
 * can be measured against a time she used to run.
 */
@Composable
fun LongRunProgression(
    points: List<LongRunPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val furthest = points.maxOf { it.km }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("The long run ladder", style = MaterialTheme.typography.titleMedium)
            Text(
                "Kilometres — where you are now, and every long run to race day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                points.forEach { point ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            formatKm(point.km),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (point.isNext || point.done) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                point.done -> ComebackGold
                                point.isNext -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                .height((96.0 * (point.km / furthest)).coerceAtLeast(8.0).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .then(
                                    if (point.isNext) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = RoyalPurpleLight,
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .background(barBrush(point)),
                        )
                    }
                }
            }
        }
    }
}

/** One rung of the long-run ladder. */
data class LongRunPoint(
    val km: Double,
    val done: Boolean,
    val isNext: Boolean,
    val isRace: Boolean,
)

@Composable
private fun barBrush(point: LongRunPoint): Brush = when {
    point.done && point.isRace -> Brush.verticalGradient(listOf(ComebackGoldSoft, BlushPink))
    point.done -> Brush.verticalGradient(listOf(ComebackGoldSoft, ComebackGold))
    point.isRace -> Brush.verticalGradient(listOf(RoyalPurpleLight, BlushPink))
    else -> Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f),
        ),
    )
}

/**
 * The Sunday wrap: what the week held, stated as facts that accumulate.
 *
 * There is deliberately no denominator and no mention of anything missed. This card exists to be
 * a guaranteed good moment at the end of every week, including the weeks that went badly — so the
 * only thing it is allowed to do is name what happened.
 */
@Composable
fun WeekWrapCard(summary: WeekSummary, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(RoyalPurple, BlushPink)))
                .padding(20.dp),
        ) {
            Text(
                "Week of ${summary.weekCommencing.format(wrapDateFormat)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                buildString {
                    append(summary.sessions)
                    append(if (summary.sessions == 1) " session" else " sessions")
                    if (summary.distanceKm > 0) append(" · ${formatKm(summary.distanceKm)}km")
                    if (summary.minutes > 0) append(" · ${summary.minutes} min")
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp),
            )
            summary.longestRunKm?.let { longest ->
                Text(
                    if (summary.isFurthestYet) {
                        "Longest run ${formatKm(longest)}km — furthest you've been since starting."
                    } else {
                        "Longest run ${formatKm(longest)}km."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ComebackGoldSoft,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                "That's the week. It's banked.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) {
                Text("Got it", color = Color.White)
            }
        }
    }
}

/**
 * The unlock moment. Badges used to just quietly turn gold in a row she had to scroll to — for an
 * app built around shimmer and gold, that was the biggest unused asset in it.
 */
@Composable
fun CelebrationDialog(
    headline: String,
    detail: String,
    badges: List<Badge>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shimmer(listOf(RoyalPurple, BlushPink, ComebackGold, RoyalPurpleLight, RoyalPurple), widthPx = 700f)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    headline,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (badges.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        badges.forEach { badge -> BadgeChip(badge = badge, unlocked = true) }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Nice", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
