package com.laura.royaltasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderTest {

    private val london = ZoneId.of("Europe/London")
    private fun ms(text: String) = LocalDateTime.parse(text).atZone(london).toInstant().toEpochMilli()
    private fun next(at: String, repeat: Repeat, now: String) =
        nextOccurrence(Reminder(ms(at), repeat), ms(now), london)

    @Test
    fun `once fires at its time, then is finished`() {
        assertEquals(ms("2026-10-13T09:00"), next("2026-10-13T09:00", Repeat.ONCE, "2026-10-12T20:00"))
        assertNull(next("2026-10-13T09:00", Repeat.ONCE, "2026-10-13T09:00"))
    }

    @Test
    fun `daily keeps its time of day`() {
        // Monday 12 Oct 2026, 08:00 reminder already passed today → tomorrow.
        assertEquals(ms("2026-10-13T08:00"), next("2026-10-01T08:00", Repeat.DAILY, "2026-10-12T10:00"))
        assertEquals(ms("2026-10-12T18:00"), next("2026-10-01T18:00", Repeat.DAILY, "2026-10-12T10:00"))
    }

    @Test
    fun `weekdays skip the weekend`() {
        // Friday 16 Oct 18:00 has passed → Monday 19 Oct.
        assertEquals(ms("2026-10-19T09:00"), next("2026-10-12T09:00", Repeat.WEEKDAYS, "2026-10-16T18:00"))
        assertEquals(ms("2026-10-19T09:00"), next("2026-10-12T09:00", Repeat.WEEKDAYS, "2026-10-17T12:00"))
    }

    @Test
    fun `weekly stays on the same weekday`() {
        // First set on a Tuesday.
        assertEquals(ms("2026-10-20T19:30"), next("2026-10-06T19:30", Repeat.WEEKLY, "2026-10-13T19:30"))
        assertEquals(ms("2026-10-13T19:30"), next("2026-10-06T19:30", Repeat.WEEKLY, "2026-10-12T07:00"))
    }

    @Test
    fun `a future first reminder wins even when repeating`() {
        assertEquals(ms("2026-10-19T09:00"), next("2026-10-19T09:00", Repeat.DAILY, "2026-10-12T10:00"))
    }

    @Test
    fun `clock change keeps the local time`() {
        // UK clocks go back on Sunday 25 Oct 2026; 09:00 stays 09:00.
        assertEquals(ms("2026-10-26T09:00"), next("2026-10-20T09:00", Repeat.DAILY, "2026-10-25T10:00"))
    }

    @Test
    fun `reminders round-trip through JSON, and old tasks load without one`() {
        val task = Task(id = "a", title = "Bins", createdAt = 1L, reminder = Reminder(123L, Repeat.WEEKLY))
        assertEquals(listOf(task), listOf(task).toJsonString().toTasks())
        assertNull("""[{"id":"x","title":"Old"}]""".toTasks().single().reminder)
    }
}
