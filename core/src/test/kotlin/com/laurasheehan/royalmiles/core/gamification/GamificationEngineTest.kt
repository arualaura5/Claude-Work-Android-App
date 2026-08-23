package com.laurasheehan.royalmiles.core.gamification

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.core.plan.TrainingPlanGenerator
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GamificationEngineTest {

    private val today = LocalDate.of(2026, 8, 23)
    private val raceDate = LocalDate.of(2026, 10, 11)
    private val plan = TrainingPlanGenerator.generate(raceDate = raceDate, today = today)

    @Test
    fun `xp rewards long runs more than easy runs of the same distance`() {
        val easy = CompletedSession(today, SessionType.EASY_RUN, TrainingPhase.BASE, distanceKm = 5.0)
        val long = CompletedSession(today, SessionType.LONG_RUN, TrainingPhase.BUILD, distanceKm = 5.0)
        assertTrue(GamificationEngine.xpFor(long) > GamificationEngine.xpFor(easy))
    }

    @Test
    fun `level increases as xp accumulates`() {
        assertEquals("Couch to Comeback", GamificationEngine.levelFor(0).title)
        assertEquals("Base Builder", GamificationEngine.levelFor(150).title)
        assertEquals("Race Ready", GamificationEngine.levelFor(5000).title)
    }

    @Test
    fun `current streak counts consecutive days including today`() {
        val completions = listOf(
            CompletedSession(today, SessionType.EASY_RUN, TrainingPhase.BASE, 5.0),
            CompletedSession(today.minusDays(1), SessionType.STRENGTH, TrainingPhase.BASE),
            CompletedSession(today.minusDays(2), SessionType.YOGA, TrainingPhase.BASE),
            CompletedSession(today.minusDays(5), SessionType.EASY_RUN, TrainingPhase.BASE, 3.0),
        )
        assertEquals(3, GamificationEngine.currentStreak(completions, asOf = today))
    }

    @Test
    fun `streak resets to zero once a day is missed`() {
        val completions = listOf(
            CompletedSession(today.minusDays(3), SessionType.EASY_RUN, TrainingPhase.BASE, 5.0),
        )
        assertEquals(0, GamificationEngine.currentStreak(completions, asOf = today))
    }

    @Test
    fun `first session unlocks first steps badge`() {
        val completions = listOf(
            CompletedSession(plan.startDate, SessionType.STRENGTH, TrainingPhase.BASE),
        )
        val badges = GamificationEngine.evaluateBadges(completions, plan)
        assertTrue(Badge.FIRST_SESSION in badges)
        assertTrue(Badge.PEAK_CONQUEROR !in badges)
    }

    @Test
    fun `completing the peak long run unlocks peak conqueror`() {
        val peakWeek = plan.weeks.first { it.phase == TrainingPhase.PEAK }
        val peakLongRun = peakWeek.sessions.first { it.type == SessionType.LONG_RUN }
        val completions = listOf(
            CompletedSession(peakLongRun.date, SessionType.LONG_RUN, TrainingPhase.PEAK, peakLongRun.targetDistanceKm),
        )
        val badges = GamificationEngine.evaluateBadges(completions, plan)
        assertTrue(Badge.PEAK_CONQUEROR in badges)
        assertTrue(Badge.DOUBLE_DIGITS in badges)
    }

    @Test
    fun `completing every base phase session unlocks base camp cleared`() {
        val baseSessions = plan.weeks.filter { it.phase == TrainingPhase.BASE }
            .flatMap { it.sessions }
            .filter { it.isLoggable }
        val completions = baseSessions.map {
            CompletedSession(it.date, it.type, it.phase, it.targetDistanceKm, it.targetDurationMin)
        }
        val badges = GamificationEngine.evaluateBadges(completions, plan)
        assertTrue(Badge.BASE_COMPLETE in badges)
    }

    @Test
    fun `finishing the race unlocks royal parks finisher`() {
        val raceSession = plan.weeks.last().sessions.last()
        val completions = listOf(
            CompletedSession(raceSession.date, SessionType.RACE, TrainingPhase.TAPER, raceSession.targetDistanceKm),
        )
        val badges = GamificationEngine.evaluateBadges(completions, plan)
        assertTrue(Badge.RACE_DAY in badges)
    }
}
