package com.laurasheehan.royalmiles.core.plan

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import java.time.DayOfWeek
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
        assertEquals(LocalDate.of(2026, 8, 24), plan.startDate)
        assertEquals(raceDate, plan.weeks.last().sessions.last().date)
    }

    @Test
    fun `weeks are numbered 1 through N starting on Mondays`() {
        val plan = TrainingPlanGenerator.generate(raceDate = raceDate, today = today)

        plan.weeks.forEachIndexed { index, week ->
            assertEquals(index + 1, week.weekNumber)
            assertEquals(DayOfWeek.MONDAY, week.startDate.dayOfWeek)
        }
    }

    @Test
    fun `regular weeks put easy run on Tuesday and strength plus short easy run on Wednesday`() {
        val plan = TrainingPlanGenerator.generate(raceDate = raceDate, today = today)

        val regularWeeks = plan.weeks.filter { it.phase != TrainingPhase.TAPER }
        assertTrue(regularWeeks.isNotEmpty())

        regularWeeks.forEach { week ->
            val tuesday = week.sessions.filter { it.date.dayOfWeek == DayOfWeek.TUESDAY }
            val wednesday = week.sessions.filter { it.date.dayOfWeek == DayOfWeek.WEDNESDAY }
            assertEquals(listOf(SessionType.EASY_RUN), tuesday.map { it.type })
            assertEquals(listOf(SessionType.STRENGTH, SessionType.EASY_RUN), wednesday.map { it.type })
        }
    }

    @Test
    fun `remaining plan after completed week one uses exact revised ladder and three runs per week`() {
        val plan = TrainingPlanGenerator.generate(
            raceDate = raceDate,
            today = LocalDate.of(2026, 8, 30),
            peakLongRunKm = 15.0,
        )

        assertEquals(LocalDate.of(2026, 8, 31), plan.startDate)
        assertEquals(listOf(7.0, 9.0, 11.0, 13.0, 15.0), plan.weeks.take(5).map { it.longRunKm })

        val expectedRuns = listOf(
            listOf(4.0, 3.0, 7.0),
            listOf(5.0, 3.5, 9.0),
            listOf(5.5, 4.0, 11.0),
            listOf(6.0, 4.5, 13.0),
            listOf(6.0, 4.5, 15.0),
        )
        plan.weeks.take(5).forEachIndexed { index, week ->
            val runs = week.sessions
                .filter { it.type == SessionType.EASY_RUN || it.type == SessionType.LONG_RUN }
                .mapNotNull { it.targetDistanceKm }
            assertEquals(expectedRuns[index], runs)
        }
        assertEquals(
            listOf(3.0, 2.0, 21.1),
            plan.weeks.last().sessions
                .filter { it.type == SessionType.EASY_RUN || it.type == SessionType.RACE }
                .mapNotNull { it.targetDistanceKm },
        )
        plan.weeks.forEach { week ->
            val runCount = week.sessions.count {
                it.type == SessionType.EASY_RUN || it.type == SessionType.LONG_RUN || it.type == SessionType.RACE
            }
            assertEquals(3, runCount)
        }
    }

    @Test
    fun `long runs peak at 15km exactly one week before the race`() {
        val plan = TrainingPlanGenerator.generate(raceDate = raceDate, today = today, peakLongRunKm = 15.0)

        val peakWeek = plan.weeks.first { it.phase == TrainingPhase.PEAK }
        assertEquals(15.0, peakWeek.longRunKm)
        assertEquals(raceDate.minusWeeks(1), peakWeek.sessions.first { it.type == SessionType.LONG_RUN }.date)

        val longRunDistances = plan.weeks
            .filter { it.phase != TrainingPhase.TAPER }
            .mapNotNull { it.longRunKm }
        assertEquals(longRunDistances, longRunDistances.sorted(), "long run distance should increase every week")
    }

    @Test
    fun `target notes are attached to long runs and race`() {
        val plan = TrainingPlanGenerator.generate(
            raceDate = raceDate,
            today = LocalDate.of(2026, 8, 30),
            peakLongRunKm = 15.0,
        )

        val longRuns = plan.weeks.take(5).map { it.sessions.first { session -> session.type == SessionType.LONG_RUN } }
        assertTrue(longRuns.filterNot { it.targetDistanceKm == 13.0 }.all { it.notes.contains("Keep heart rate under 150") })
        assertTrue(longRuns.first { it.targetDistanceKm == 13.0 }.notes.startsWith("Gate check."))
        assertTrue(plan.weeks.last().sessions.first { it.type == SessionType.RACE }.notes.startsWith("First 14 km"))
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
