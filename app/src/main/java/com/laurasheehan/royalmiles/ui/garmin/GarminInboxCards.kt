package com.laurasheehan.royalmiles.ui.garmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.garmin.ActivityKind
import com.laurasheehan.royalmiles.data.garmin.AskReason
import com.laurasheehan.royalmiles.data.garmin.AutoLink
import com.laurasheehan.royalmiles.data.garmin.GarminInboxState
import com.laurasheehan.royalmiles.data.garmin.PendingActivity
import com.laurasheehan.royalmiles.data.health.ExternalWorkout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the dashboard does with her answers; each maps to one GarminInbox call. */
data class GarminInboxActions(
    val confirm: (PendingActivity, SessionEntity) -> Unit = { _, _ -> },
    val swap: (PendingActivity, SessionEntity) -> Unit = { _, _ -> },
    val extra: (PendingActivity) -> Unit = {},
    val ignore: (PendingActivity) -> Unit = {},
    val keep: (AutoLink) -> Unit = {},
    val notThisOne: (AutoLink) -> Unit = {},
)

/** New Garmin activities at the top of the dashboard: linked ones to glance at, others to place. */
@Composable
fun GarminInboxCards(state: GarminInboxState, actions: GarminInboxActions, today: LocalDate = LocalDate.now()) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.autoLinked.forEach { AutoLinkCard(it, actions, today) }
        state.pending.forEach { AskCard(it, actions, today) }
    }
}

@Composable
private fun AutoLinkCard(link: AutoLink, actions: GarminInboxActions, today: LocalDate) {
    val decision = link.decision
    val noun = decision.activityKind?.let { runCatching { ActivityKind.valueOf(it).noun }.getOrNull() } ?: "session"
    GarminCard(title = "Matched to your planned $noun") {
        Text(
            "From Garmin: " + summary(decision.distanceKm, decision.durationMin, decision.avgHeartRate, decision.activityKind),
            style = MaterialTheme.typography.bodyMedium,
        )
        link.session?.let { session ->
            Text(
                "Planned: ${session.title}${planned(session)}, ${day(session.date, today)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "It's been counted as done. Is this the session you did?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { actions.keep(link) }) { Text("Yes, that's right") }
            TextButton(onClick = { actions.notThisOne(link) }) { Text("No, not this one") }
        }
    }
}

@Composable
private fun AskCard(item: PendingActivity, actions: GarminInboxActions, today: LocalDate) {
    val w = item.workout
    val ask = item.ask
    GarminCard(title = "From Garmin, ${day(w.localDate, today)}: which session was this?") {
        Text(
            summary(w.distanceKm, w.durationMinutes, w.avgHeartRate, item.kind.name, name = w.title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(reason(item), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ask.sameKindThatDay.forEach { session ->
                Choice("It was ${session.title}${planned(session)}") { actions.confirm(item, session) }
            }
            ask.plannedThatDay.forEach { session ->
                Choice("Swap ${session.title} for this") { actions.swap(item, session) }
            }
            ask.nearby.take(2).forEach { session ->
                val when_ = if (session.date.isAfter(w.localDate)) "done early" else "done late"
                Choice("It was ${dayPossessive(session.date, today)} ${session.title}, $when_") { actions.confirm(item, session) }
            }
            Choice("Extra, not part of the plan") { actions.extra(item) }
            TextButton(onClick = { actions.ignore(item) }, modifier = Modifier.fillMaxWidth()) { Text("Ignore it") }
        }
    }
}

@Composable
private fun Choice(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GarminCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Watch, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

private fun reason(item: PendingActivity): String {
    val w = item.workout
    val ask = item.ask
    val noun = item.kind.noun
    return when (ask.reason) {
        AskReason.DIFFERENT_SIZE -> {
            val session = ask.sameKindThatDay.first()
            val shorter = compareToPlan(w, session) < 0
            "The plan had ${session.title}${planned(session)}. This was much ${if (shorter) "shorter" else "longer"}."
        }
        AskReason.DIFFERENT_KIND -> "The plan had ${ask.plannedThatDay.joinToString(" and ") { it.title }}. This was a $noun."
        AskReason.NOTHING_PLANNED -> "Nothing was planned that day."
        AskReason.SEVERAL -> "More than one ${noun.substringBefore(' ')} was planned that day."
        AskReason.SHE_SAID_NOT_THIS -> "You said it wasn't the session it was linked to."
    }
}

/** Negative when the activity came in under the plan. */
private fun compareToPlan(w: ExternalWorkout, session: SessionEntity): Int {
    val km = w.distanceKm
    val target = session.targetDistanceKm
    return if (km != null && target != null) km.compareTo(target) else w.durationMinutes.compareTo(session.targetDurationMin ?: w.durationMinutes)
}

private fun planned(session: SessionEntity): String = when {
    session.targetDistanceKm != null -> " (${plannedKm(session.targetDistanceKm)} planned)"
    session.targetDurationMin != null -> " (${session.targetDurationMin} min planned)"
    else -> ""
}

internal fun summary(distanceKm: Double?, minutes: Int?, avgHr: Int?, kind: String?, name: String? = null): String {
    val noun = kind?.let { runCatching { ActivityKind.valueOf(it).noun }.getOrNull() } ?: "activity"
    val parts = listOfNotNull(
        distanceKm?.let { "${km(it)} $noun" } ?: noun.replaceFirstChar { it.uppercase() },
        minutes?.let { "$it min" },
        avgHr?.let { "avg HR $it" },
    )
    val text = parts.joinToString(" · ")
    return if (name.isNullOrBlank()) text else "$name: $text"
}

/** As Garmin shows it: 9.03 km. */
private fun km(value: Double) = String.format(Locale.UK, "%.2f km", value)

/** Plans are round numbers: 16 km, 7.5 km. */
private fun plannedKm(value: Double) =
    if (value == value.toLong().toDouble()) "${value.toLong()} km" else String.format(Locale.UK, "%.1f km", value)

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

internal fun day(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "today"
    today.minusDays(1) -> "yesterday"
    today.plusDays(1) -> "tomorrow"
    else -> date.format(dayFormat)
}

private fun dayPossessive(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "today's"
    today.minusDays(1) -> "yesterday's"
    today.plusDays(1) -> "tomorrow's"
    else -> date.format(dayFormat) + "'s"
}
