package com.laurasheehan.royalmiles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import java.time.format.DateTimeFormatter

/** e.g. "Tue 1 Sep" — the day matters as much as the distance when scanning a week. */
private val cardDateFormat = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * @param onSkip long-press action, writing the session off as not done. Clearing a miss should be
 *   cheaper than the miss was — before this it took four taps through the edit screen, so misses
 *   simply accumulated in the calendar as unresolved empty circles instead.
 * @param onRate records how a completed session felt. Shown inline on a completed, unrated card so
 *   rating costs one tap on the card you just ticked, rather than a trip into the edit screen —
 *   which is why almost nothing had a rating and the trend had nothing to read.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionCard(
    session: SessionEntity,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: (() -> Unit)? = null,
    onRate: ((Int) -> Unit)? = null,
) {
    val accent = session.type.accentColor()
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (onSkip != null && session.isLoggable && session.isOutstanding) {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSkip()
                    }
                } else {
                    null
                },
            ),
        // A replaced session is settled history — it should recede rather than compete with the
        // sessions still to do. Half-strength surface lets the card blend toward the background,
        // which recedes correctly in both themes; surfaceVariant would not, being *lighter* than
        // surface on dark. Text stays well clear of AA against the blend: 7.03:1 dark, 5.88:1
        // light. Deliberately not applied to a plain skip — owning a missed session is the point,
        // and fading it would undo that.
        colors = CardDefaults.cardColors(
            containerColor = if (session.supersededByCoach) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (session.isCompleted || session.supersededByCoach) 0.dp else 2.dp,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (session.isSkipped) 0.10f else 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    session.type.icon(),
                    contentDescription = null,
                    tint = if (session.isSkipped) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (session.isCompleted || session.isSkipped) TextDecoration.LineThrough else null,
                    color = if (session.isSkipped) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = sessionSubtitle(session),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (session.isCompleted) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Completed", tint = ComebackGold)
            } else if (session.supersededByCoach) {
                // A session she and her coach agreed to change is not a session she failed to do.
                // Same struck-through row so the day still reads honestly, but stated as what it
                // was — replaced — and in muted ink rather than the red reserved for a real miss.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = "Replaced",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "Replaced",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (session.isSkipped) {
                // Struck through and in red, at her request. Owning a missed session is the point —
                // the app shouldn't be so careful about her feelings that the state is hard to read.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = "Didn't do it",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "Didn't do it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (session.isLoggable) {
                IconButton(onClick = onToggleComplete) {
                    Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "Mark complete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (onRate != null && session.isCompleted && session.effortRating == null) {
            EffortPrompt(onRate = onRate, modifier = Modifier.padding(top = 10.dp))
        }
        }
    }
}

/**
 * "How did that feel?", 1 to 5, inline on the card. Disappears once answered.
 *
 * Self-reported and unscored — it feeds the fatigue trend and nothing else. Nothing here earns XP,
 * and a low number is never treated as a worse answer than a high one.
 */
@Composable
private fun EffortPrompt(onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "How did that feel?",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { rating ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onRate(rating) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$rating",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * A completed session shows what she actually did; anything else shows what's planned. "You ran
 * 5.2km" beats "5km planned" every time, and the card was previously showing the target even after
 * the session was logged.
 */
private fun sessionSubtitle(session: SessionEntity): String {
    val parts = mutableListOf(session.date.format(cardDateFormat))
    val distance = if (session.isCompleted) session.actualDistanceKm ?: session.targetDistanceKm else session.targetDistanceKm
    val duration = if (session.isCompleted) session.actualDurationMin ?: session.targetDurationMin else session.targetDurationMin
    distance?.let { parts.add("${formatKm(it)}km") }
    duration?.let { parts.add("$it min") }
    if (session.optional && !session.isCompleted) parts.add("optional")
    // Rest days carry no distance or duration, so name the type rather than showing a bare date.
    if (parts.size == 1) parts.add(session.type.label())
    return parts.joinToString(" · ")
}

/**
 * One decimal place at most. Rounds before formatting because this now renders summed distances —
 * week and lifetime totals — and raw double arithmetic prints 13.299999999999999 otherwise.
 */
fun formatKm(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
