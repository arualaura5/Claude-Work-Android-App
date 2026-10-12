package com.laura.royaltasks.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.laura.royaltasks.data.Reminder
import com.laura.royaltasks.data.Repeat
import com.laura.royaltasks.data.nextOccurrence
import com.laura.royaltasks.ui.theme.tabular
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private val TimeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val WeekdayFormat = DateTimeFormatter.ofPattern("EEE")
private val DateFormatShort = DateTimeFormatter.ofPattern("EEE d MMM")

/** "Today 18:00", "Tomorrow 09:00", "Thu 09:00", "Mon 2 Nov 09:00". */
fun whenLabel(millis: Long, now: LocalDateTime = LocalDateTime.now()): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
    val day = when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days in 2..6 -> at.format(WeekdayFormat)
        else -> at.format(DateFormatShort)
    }
    return "$day ${at.toLocalTime().format(TimeFormat)}"
}

/** What the task card shows: the next reminder, and how it repeats. Null once a one-off has passed. */
fun reminderLabel(reminder: Reminder): String? {
    val next = nextOccurrence(reminder, System.currentTimeMillis(), ZoneId.systemDefault()) ?: return null
    val repeat = if (reminder.repeat == Repeat.ONCE) "" else " · ${reminder.repeat.label.lowercase()}"
    return whenLabel(next) + repeat
}

/** Bell for the add row and the edit box: outlined when empty, filled purple when set. */
@Composable
fun ReminderBell(set: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (set) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationAdd,
            contentDescription = if (set) "Change reminder" else "Add reminder",
            tint = if (set) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Small "🔔 Tomorrow 09:00 · daily" line under a task. */
@Composable
fun ReminderLine(label: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            Icons.Filled.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun millisAt(date: LocalDate, time: LocalTime): Long =
    date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Quick picks relative to now, skipping any that have already passed today. */
private fun presets(now: LocalDateTime): List<Pair<String, Long>> {
    val today = now.toLocalDate()
    val inAnHour = now.plusHours(1).truncatedTo(ChronoUnit.MINUTES)
    return buildList {
        add("In 1 hour" to inAnHour.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        if (now.toLocalTime().isBefore(LocalTime.of(17, 0))) {
            add("This evening" to millisAt(today, LocalTime.of(18, 0)))
        }
        add("Tomorrow 9am" to millisAt(today.plusDays(1), LocalTime.of(9, 0)))
        add("Monday 9am" to millisAt(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), LocalTime.of(9, 0)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderDialog(
    initial: Reminder?,
    onSave: (Reminder) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val now = remember { LocalDateTime.now() }
    val choices = remember { presets(now) }
    var at by remember { mutableLongStateOf(initial?.atMillis ?: choices.first { it.first == "Tomorrow 9am" }.second) }
    var repeat by remember { mutableStateOf(initial?.repeat ?: Repeat.ONCE) }
    // A repeating reminder whose first time has passed is still fine: it moves on to the next one.
    val valid = nextOccurrence(Reminder(at, repeat), System.currentTimeMillis(), ZoneId.systemDefault()) != null

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    fun pickDateAndTime() {
        val current = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDateTime()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        at = millisAt(LocalDate.of(year, month + 1, day), LocalTime.of(hour, minute))
                    },
                    current.hour,
                    current.minute,
                    DateFormat.is24HourFormat(context)
                ).show()
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Remind me", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // The chosen time is the thing that matters, so it's the largest.
                Column {
                    Text(
                        text = whenLabel(at, now),
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!valid) {
                        Text(
                            text = "That time has passed. Pick a later one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { (label, millis) ->
                        FilterChip(
                            selected = at == millis,
                            onClick = { at = millis },
                            label = { Text(label) },
                            colors = chipColors
                        )
                    }
                    AssistChip(
                        onClick = { pickDateAndTime() },
                        label = { Text("Pick date & time") },
                        leadingIcon = {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Text(
                    text = "REPEAT",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Repeat.entries.forEach { option ->
                        FilterChip(
                            selected = repeat == option,
                            onClick = { repeat = option },
                            label = { Text(option.label) },
                            colors = chipColors
                        )
                    }
                }
                if (repeat != Repeat.ONCE) {
                    Text(
                        text = "Repeats until you tick it off.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Reminder(at, repeat)) }, enabled = valid) { Text("Set reminder") }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
