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
    private val raceName = "Royal Parks Half Marathon"
    private val plan = TrainingPlanGenerator.generate(raceName = raceName, raceDate = raceDate, today = today)

    @Test
    fun `xp rewards long runs more than easy runs of the same distance`() {
        val easy = CompletedSession(today, SessionType.EASY_RUN, TrainingPhase.BASE, distanceKm = 5.0)
        val long = CompletedSession(today, SessionType.LONG_RUN, TrainingPhase.BUILD, distanceKm = 5.0)
        assertTrue(GamificationEngine.xpFor(long) > GamificationEngine.xpFor(easy))
    }

    @Test
    fun `level increases as xp accumulates`() {
        assertEquals("Couch to Comeback", GamificationEngine.levelFor(0).title)
        assertEquals("Base Builder", GamificationEngine.levelFor(60).title)
        assertEquals("Race Ready", GamificationEngine.levelFor(5000).title)
    }

    /** All loggable sessions in the plan, as if every one of them had been ticked off. */
    private fun everySession(): List<CompletedSession> = plan.weeks
        .flatMap { it.sessions }
        .filter { it.isLoggable }
        .map { CompletedSession(it.date, it.type, it.phase, it.targetDistanceKm, it.targetDurationMin) }

    @Test
    fun `the top level is reachable within the plan the generator produces`() {
        val ceiling = GamificationEngine.totalXp(everySession())
        val top = GamificationEngine.levels.last()
        assertTrue(
            top.xpRequired < ceiling,
            "Top level ${top.title} needs ${top.xpRequired} XP but the whole plan only yields $ceiling",
        )
    }

    @Test
    fun `every level is reachable within the plan the generator produces`() {
        val ceiling = GamificationEngine.totalXp(everySession())
        GamificationEngine.levels.forEach { level ->
            assertTrue(level.xpRequired <= ceiling, "Level ${level.number} (${level.title}) is unreachable")
        }
    }

    @Test
    fun `finishing the race tops out the curve even on partial adherence`() {
        val race = plan.weeks.last().sessions.last()
        val completions = listOf(
            CompletedSession(race.date, SessionType.RACE, TrainingPhase.TAPER, race.targetDistanceKm),
        )
        val xp = GamificationEngine.totalXp(completions)
        assertTrue(xp < GamificationEngine.levels.last().xpRequired, "test is vacuous if the race alone tops the curve")
        assertEquals("Race Ready", GamificationEngine.levelFor(xp, raceCompleted = true).title)
        assertEquals(null, GamificationEngine.xpToNextLevel(xp, raceCompleted = true))
    }

    @Test
    fun `week streak counts consecutive weeks with anything logged`() {
        // Sunday 23 Aug 2026, counting back over three weeks that each contain one session.
        val completions = listOf(
            CompletedSession(today, SessionType.EASY_RUN, TrainingPhase.BASE, 5.0),
            CompletedSession(today.minusWeeks(1), SessionType.STRENGTH, TrainingPhase.BASE),
            CompletedSession(today.minusWeeks(2), SessionType.YOGA, TrainingPhase.BASE),
            CompletedSession(today.minusWeeks(4), SessionType.EASY_RUN, TrainingPhase.BASE, 3.0),
        )
        assertEquals(3, GamificationEngine.currentWeekStreak(completions, asOf = today))
    }

    @Test
    fun `a rest day cannot break the streak`() {
        // The bug this replaced: Monday is a rest day in every generated week and carries no tick
        // button, so a day-streak collapsed to zero every Tuesday morning. Log Tue-Sun of one week
        // and the streak must still stand on the Tuesday of the next.
        val weekStart = LocalDate.of(2026, 8, 24)
        val completions = (1L..6L).map {
            CompletedSession(weekStart.plusDays(it), SessionType.EASY_RUN, TrainingPhase.BASE, 5.0)
        }
        val nextTuesday = weekStart.plusWeeks(1).plusDays(1)
        assertEquals(1, GamificationEngine.currentWeekStreak(completions, asOf = nextTuesday))
    }

    @Test
    fun `a quiet start to the week does not reset the streak`() {
        val weekStart = LocalDate.of(2026, 8, 24)
        val completions = listOf(
            CompletedSession(weekStart.plusDays(6), SessionType.LONG_RUN, TrainingPhase.BUILD, 8.0),
            CompletedSession(weekStart.minusWeeks(1).plusDays(6), SessionType.LONG_RUN, TrainingPhase.BUILD, 7.0),
        )
        // Monday of the following week, nothing logged yet: still two, not zero.
        assertEquals(2, GamificationEngine.currentWeekStreak(completions, asOf = weekStart.plusWeeks(1)))
    }

    @Test
    fun `a whole week off does reset the streak`() {
        val weekStart = LocalDate.of(2026, 8, 24)
        val completions = listOf(
            CompletedSession(weekStart.plusDays(2), SessionType.STRENGTH, TrainingPhase.BASE),
        )
        assertEquals(0, GamificationEngine.currentWeekStreak(completions, asOf = weekStart.plusWeeks(2)))
    }

    @Test
    fun `week streak badges are achievable under the generated plan`() {
        val badges = GamificationEngine.evaluateBadges(everySession(), plan)
        assertTrue(Badge.WEEK_STREAK_3 in badges)
        assertTrue(Badge.WEEK_STREAK_6 in badges)
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
    fun `reaching the peak distance unlocks peak conqueror even a day late`() {
        val peakWeek = plan.weeks.first { it.phase == TrainingPhase.PEAK }
        val peakLongRun = peakWeek.sessions.first { it.type == SessionType.LONG_RUN }
        val completions = listOf(
            // Deliberately a day off the scheduled date: the badge is about the distance.
            CompletedSession(
                peakLongRun.date.plusDays(1),
                SessionType.LONG_RUN,
                TrainingPhase.PEAK,
                peakLongRun.targetDistanceKm,
            ),
        )
        val badges = GamificationEngine.evaluateBadges(completions, plan)
        assertTrue(Badge.PEAK_CONQUEROR in badges)
        assertTrue(Badge.DOUBLE_DIGITS in badges)
        assertTrue(Badge.FIFTEEN_KM in badges)
    }

    @Test
    fun `no badge can be permanently lost by missing a session`() {
        // Every badge must be reachable from a history that skipped the whole first week —
        // the all-or-nothing badges this replaced could not be.
        val afterAMissedWeek = plan.weeks.drop(1)
            .flatMap { it.sessions }
            .filter { it.isLoggable }
            .map { CompletedSession(it.date, it.type, it.phase, it.targetDistanceKm, it.targetDurationMin) }
        val badges = GamificationEngine.evaluateBadges(afterAMissedWeek, plan)
        val missing = Badge.entries.filterNot { it in badges }
        assertTrue(missing.isEmpty(), "Unreachable after one missed week: $missing")
    }

    @Test
    fun `badges accumulate and never regress as sessions are added`() {
        val everything = everySession()
        var previous = emptySet<Badge>()
        everything.indices.forEach { i ->
            val badges = GamificationEngine.evaluateBadges(everything.take(i + 1), plan)
            assertTrue(previous.all { it in badges }, "Badge lost at session ${i + 1}: ${previous - badges}")
            previous = badges
        }
    }

    @Test
    fun `cycling distance does not count toward running badges`() {
        val completions = listOf(
            CompletedSession(today, SessionType.CYCLE, TrainingPhase.BASE, distanceKm = 60.0, durationMin = 120),
        )
        val badges = GamificationEngine.evaluateBadges(completions, plan)
        assertTrue(Badge.FIFTY_KM !in badges)
        assertTrue(Badge.DOUBLE_DIGITS !in badges)
        assertTrue(Badge.FIRST_SESSION in badges)
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
