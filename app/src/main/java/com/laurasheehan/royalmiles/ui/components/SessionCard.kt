package com.laurasheehan.royalmiles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
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
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionCard(
    session: SessionEntity,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: (() -> Unit)? = null,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (session.isCompleted) 0.dp else 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
