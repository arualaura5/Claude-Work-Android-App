package com.laurasheehan.royalmiles.core.progress

import com.laurasheehan.royalmiles.core.gamification.CompletedSession
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekSummaryTest {

    private val monday = LocalDate.of(2026, 8, 24)

    private fun run(date: LocalDate, km: Double, min: Int = 30) =
        CompletedSession(date, SessionType.EASY_RUN, TrainingPhase.BASE, km, min)

    @Test
    fun `summarises only the week asked for`() {
        val all = listOf(
            run(monday.plusDays(1), 5.0),
            run(monday.plusDays(6), 8.0),
            run(monday.plusWeeks(1).plusDays(1), 6.0),
        )
        val summary = WeekSummaries.summarise(all, monday)
        assertEquals(2, summary.sessions)
        assertEquals(13.0, summary.distanceKm)
        assertEquals(8.0, summary.longestRunKm)
    }

    @Test
    fun `sunday belongs to the week that started on monday`() {
        val sunday = monday.plusDays(6)
        assertEquals(monday, WeekSummaries.weekCommencing(sunday))
        val summary = WeekSummaries.summarise(listOf(run(sunday, 8.0)), monday)
        assertEquals(1, summary.sessions)
    }

    @Test
    fun `flags a furthest-yet run`() {
        val all = listOf(run(monday.minusWeeks(1).plusDays(6), 7.0), run(monday.plusDays(6), 8.0))
        assertTrue(WeekSummaries.summarise(all, monday).isFurthestYet)
    }

    @Test
    fun `does not flag furthest yet when an earlier week went further`() {
        val all = listOf(run(monday.minusWeeks(1).plusDays(6), 11.0), run(monday.plusDays(6), 8.0))
        assertFalse(WeekSummaries.summarise(all, monday).isFurthestYet)
    }

    @Test
    fun `an untrained week summarises as empty rather than negative`() {
        val summary = WeekSummaries.summarise(listOf(run(monday.minusWeeks(1), 5.0)), monday)
        assertTrue(summary.isEmpty)
        assertEquals(0.0, summary.distanceKm)
        assertEquals(null, summary.longestRunKm)
    }

    @Test
    fun `cycling counts as a session but not as running distance`() {
        val all = listOf(
            CompletedSession(monday.plusDays(5), SessionType.CYCLE, TrainingPhase.BASE, 30.0, 60),
            run(monday.plusDays(6), 8.0),
        )
        val summary = WeekSummaries.summarise(all, monday)
        assertEquals(2, summary.sessions)
        assertEquals(8.0, summary.distanceKm)
        assertEquals(8.0, summary.longestRunKm)
    }
}
