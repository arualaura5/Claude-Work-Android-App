package com.laurasheehan.royalmiles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun SessionCard(
    session: SessionEntity,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = session.type.accentColor()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(session.type.icon(), contentDescription = null, tint = accent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (session.isCompleted) TextDecoration.LineThrough else null,
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
                // Stated plainly and quietly — acknowledged, not flagged as a failure.
                Text(
                    "Didn't do it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (session.isLoggable) {
                IconButton(onClick = onToggleComplete) {
                    Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "Mark complete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun sessionSubtitle(session: SessionEntity): String {
    val parts = mutableListOf(session.date.format(cardDateFormat))
    session.targetDistanceKm?.let { parts.add("${formatKm(it)}km") }
    session.targetDurationMin?.let { parts.add("${it} min") }
    if (session.optional) parts.add("optional")
    // Rest days carry no distance or duration, so name the type rather than showing a bare date.
    if (parts.size == 1) parts.add(session.type.label())
    return parts.joinToString(" · ")
}

fun formatKm(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
