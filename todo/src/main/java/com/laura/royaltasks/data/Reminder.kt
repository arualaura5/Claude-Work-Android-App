package com.laura.royaltasks.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class Repeat(val label: String) {
    ONCE("Once"),
    DAILY("Daily"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Weekly")
}

/**
 * When to nudge about a task. [atMillis] is the first reminder; a repeating
 * one keeps the same time of day (and, for weekly, the same weekday) until
 * the task is ticked off.
 */
data class Reminder(val atMillis: Long, val repeat: Repeat = Repeat.ONCE)

/** The next time this reminder should fire strictly after [nowMillis], or null if it's finished. */
fun nextOccurrence(reminder: Reminder, nowMillis: Long, zone: ZoneId): Long? {
    // The first reminder is exactly the moment she picked.
    if (reminder.atMillis > nowMillis) return reminder.atMillis
    if (reminder.repeat == Repeat.ONCE) return null

    val first = Instant.ofEpochMilli(reminder.atMillis).atZone(zone)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val time = first.toLocalTime()
    fun at(date: LocalDate): ZonedDateTime = ZonedDateTime.of(date, time, zone)

    var date = now.toLocalDate()
    val due: (LocalDate) -> Boolean = when (reminder.repeat) {
        Repeat.DAILY -> { _ -> true }
        Repeat.WEEKDAYS -> { d -> d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY }
        Repeat.WEEKLY -> { d -> d.dayOfWeek == first.dayOfWeek }
        Repeat.ONCE -> { _ -> false }
    }
    while (!due(date) || !at(date).isAfter(now)) date = date.plusDays(1)
    return at(date).toInstant().toEpochMilli()
}
