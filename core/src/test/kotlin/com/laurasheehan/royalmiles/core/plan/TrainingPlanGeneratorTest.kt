package com.laurasheehan.royalmiles.core.plan

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingPlanGeneratorTest {

    private val today = LocalDate.of(2026, 8, 23) // Sunday
    private val raceDate = LocalDate.of(2026, 10, 11) // Sunday, 7 weeks out

    @Test
    fun `plan spans exactly 7 weeks from the Monday of this week to race week`() {
        val plan = TrainingPlanGenerator.generate(raceDate = raceDate, today = today)

        assertEquals(7, plan.weeks.size)
        assertEquals(LocalDate.of(2026, 8, 24), plan.startDate) // upcoming Monday (today, Aug 23, is a Sunday)
        assertEquals(raceDate, plan.weeks.last().sessions.last().date)
    }

    @Test
    fun `weeks are numbered 1 through N starting on Mondays`() {
        val plan = TrainingPlanGenerator.generate(raceDate = raceDate, today = today)

        plan.weeks.forEachIndexed { index, week ->
            assertEquals(index + 1, week.weekNumber)
            assertEquals(java.time.DayOfWeek.MONDAY, week.startDate.dayOfWeek)
        }
    }

    @Test
    fun `first two weeks are base phase with no session over 5km`() {
        val plan = TrainingPlanGenerator.generate(raceDate = raceDate, today = today)

        val baseWeeks = plan.weeks.take(2)
        assertTrue(baseWeeks.all { it.phase == TrainingPhase.BASE })
        val maxDistance = baseWeeks.flatMap { it.sessions }.mapNotNull { it.targetDistanceKm }.max()
        assertTrue(maxDistance <= 5.0, "expected base phase to stay <=5km, was $maxDistance")
    }

    @Test
    fun `long run ramps up and peaks at 17km exactly one week before the race`() {
        val plan = TrainingPlanGenerator.generate(raceDate = raceDate, today = today, peakLongRunKm = 17.0)

        val peakWeek = plan.weeks.first { it.phase == TrainingPhase.PEAK }
        assertEquals(17.0, peakWeek.longRunKm)
        assertEquals(raceDate.minusWeeks(1), peakWeek.sessions.first { it.type == SessionType.LONG_RUN }.date)

        val longRunDistances = plan.weeks
            .filter { it.phase == TrainingPhase.BUILD || it.phase == TrainingPhase.PEAK }
            .mapNotNull { it.longRunKm }
        assertEquals(longRunDistances, longRunDistances.sorted(), "long run distance should increase every week")
    }

    @Test
    fun `taper week ends with race day and nothing scheduled after it`() {
        val plan = TrainingPlanGenerator.generate(raceDate = raceDate, today = today)

        val taperWeek = plan.weeks.last()
        assertEquals(TrainingPhase.TAPER, taperWeek.phase)
        assertEquals(raceDate, taperWeek.sessions.last().date)
        assertEquals(SessionType.RACE, taperWeek.sessions.last().type)
        assertTrue(taperWeek.sessions.none { it.date.isAfter(raceDate) })
    }

    @Test
    fun `a shorter runway still produces a valid plan ending on race day`() {
        val closeRaceDate = today.plusWeeks(3)
        val plan = TrainingPlanGenerator.generate(raceDate = closeRaceDate, today = today)

        assertEquals(closeRaceDate, plan.weeks.last().sessions.last().date)
        assertTrue(plan.weeks.isNotEmpty())
    }
}
