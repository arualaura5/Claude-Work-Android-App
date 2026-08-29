package com.laurasheehan.royalmiles.core.gamification

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.core.model.TrainingPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

data class CompletedSession(
    val date: LocalDate,
    val type: SessionType,
    val phase: TrainingPhase,
    val distanceKm: Double? = null,
    val durationMin: Int? = null,
)

data class Level(val number: Int, val title: String, val xpRequired: Int)

enum class Badge(val title: String, val description: String) {
    FIRST_SESSION("First Steps", "Complete your first logged session"),
    WEEK_STREAK_3("Three in a Row", "Train in three consecutive weeks"),
    WEEK_STREAK_6("Six in a Row", "Train in six consecutive weeks"),
    DOUBLE_DIGITS("Double Digits", "Complete a run of 10km or more"),
    BASE_COMPLETE("Base Camp Cleared", "Complete every planned session in the Base phase"),
    PEAK_CONQUEROR("Peak Conqueror", "Complete the peak long run"),
    STRENGTH_REGULAR("Strength Regular", "Complete 5 strength sessions"),
    ZEN_RUNNER("Zen Runner", "Complete 5 yoga sessions"),
    TAPER_DISCIPLINE("Taper Discipline", "Complete every non-optional taper-week session"),
    RACE_DAY("Royal Parks Finisher", "Complete the half marathon"),
}

object GamificationEngine {

    /**
     * Fitted to the plan the generator actually produces, not a generic curve. A 7-week block
     * seeded in late August tops out at 965 XP with every session logged including the optional
     * spins, so the old thresholds put level 6 (1000) and level 7 (1350) out of reach entirely —
     * perfect adherence finished the race stuck on level 5. These land a level-up in every week
     * of the block at roughly two-thirds adherence, and leave the top of the curve for race day.
     *
     * GamificationEngineTest checks the top level against the XP the generator actually yields, so
     * a change to the plan or the XP table that puts it back out of reach fails a test.
     */
    val levels: List<Level> = listOf(
        Level(1, "Couch to Comeback", 0),
        Level(2, "Base Builder", 55),
        Level(3, "Finding Rhythm", 130),
        Level(4, "Momentum", 230),
        Level(5, "Long Run Legend", 350),
        Level(6, "Taper Master", 500),
        Level(7, "Race Ready", 700),
    )

    fun xpFor(session: CompletedSession): Int = when (session.type) {
        SessionType.REST -> 0
        SessionType.EASY_RUN -> 10 + ((session.distanceKm ?: 0.0) * 2).roundToInt()
        SessionType.LONG_RUN -> 20 + ((session.distanceKm ?: 0.0) * 3).roundToInt()
        SessionType.STRENGTH -> 15
        SessionType.YOGA -> 10
        SessionType.CYCLE -> 10 + (session.durationMin ?: 0) / 10
        SessionType.RACE -> 200
    }

    fun totalXp(completions: List<CompletedSession>): Int = completions.sumOf { xpFor(it) }

    /**
     * Finishing the race tops the curve out regardless of XP. Crossing the line at the Royal Parks
     * Half *is* the thing the plan was for, and the app should not spend that day telling her she
     * is 200 XP short of "Race Ready".
     */
    fun levelFor(totalXp: Int, raceCompleted: Boolean = false): Level =
        if (raceCompleted) levels.last() else levels.last { totalXp >= it.xpRequired }

    fun xpToNextLevel(totalXp: Int, raceCompleted: Boolean = false): Int? {
        val current = levelFor(totalXp, raceCompleted)
        val next = levels.firstOrNull { it.xpRequired > current.xpRequired } ?: return null
        return next.xpRequired - totalXp
    }

    /**
     * Consecutive Monday-to-Sunday weeks containing at least one logged session, counting back
     * from [asOf].
     *
     * Deliberately week-granular. A day-streak cannot work against this plan: every week has a
     * rest day, rest days carry no tick button, so the streak was arithmetically guaranteed to
     * collapse to zero every Tuesday morning — the exact moment the plan asks for a run after
     * Monday off. A week counts if anything at all was logged in it, so a missed session never
     * breaks it; only a whole week off does.
     *
     * The week in progress is never held against her: with nothing logged yet this week the count
     * runs from last week instead, so the number can climb mid-week but never drops on a Monday.
     */
    fun currentWeekStreak(completions: List<CompletedSession>, asOf: LocalDate = LocalDate.now()): Int {
        if (completions.isEmpty()) return 0
        val weeks = completions.map { it.date.weekCommencing() }.toSet()
        val thisWeek = asOf.weekCommencing()
        var cursor = if (thisWeek in weeks) thisWeek else thisWeek.minusWeeks(1)
        var streak = 0
        while (cursor in weeks) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }

    fun longestWeekStreak(completions: List<CompletedSession>): Int {
        val weeks = completions.map { it.date.weekCommencing() }.toSortedSet().toList()
        if (weeks.isEmpty()) return 0
        var longest = 1
        var running = 1
        for (i in 1 until weeks.size) {
            running = if (ChronoUnit.WEEKS.between(weeks[i - 1], weeks[i]) == 1L) running + 1 else 1
            longest = maxOf(longest, running)
        }
        return longest
    }

    private fun LocalDate.weekCommencing(): LocalDate =
        with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun evaluateBadges(completions: List<CompletedSession>, plan: TrainingPlan): Set<Badge> {
        val unlocked = mutableSetOf<Badge>()
        if (completions.isNotEmpty()) unlocked += Badge.FIRST_SESSION

        val streak = longestWeekStreak(completions)
        if (streak >= 3) unlocked += Badge.WEEK_STREAK_3
        if (streak >= 6) unlocked += Badge.WEEK_STREAK_6

        if (completions.any { (it.distanceKm ?: 0.0) >= 10.0 }) unlocked += Badge.DOUBLE_DIGITS

        if (completions.count { it.type == SessionType.STRENGTH } >= 5) unlocked += Badge.STRENGTH_REGULAR
        if (completions.count { it.type == SessionType.YOGA } >= 5) unlocked += Badge.ZEN_RUNNER

        val completedDates = completions.map { it.date to it.type }.toSet()

        val baseSessions = plan.weeks.filter { it.phase == TrainingPhase.BASE }
            .flatMap { it.sessions }
            .filter { it.isLoggable }
        if (baseSessions.isNotEmpty() && baseSessions.all { (it.date to it.type) in completedDates }) {
            unlocked += Badge.BASE_COMPLETE
        }

        val peakLongRun = plan.weeks.firstOrNull { it.phase == TrainingPhase.PEAK }
            ?.sessions?.firstOrNull { it.type == SessionType.LONG_RUN }
        if (peakLongRun != null && (peakLongRun.date to peakLongRun.type) in completedDates) {
            unlocked += Badge.PEAK_CONQUEROR
        }

        val taperSessions = plan.weeks.filter { it.phase == TrainingPhase.TAPER }
            .flatMap { it.sessions }
            .filter { it.isLoggable && !it.optional && it.type != SessionType.RACE }
        if (taperSessions.isNotEmpty() && taperSessions.all { (it.date to it.type) in completedDates }) {
            unlocked += Badge.TAPER_DISCIPLINE
        }

        if (completions.any { it.type == SessionType.RACE }) unlocked += Badge.RACE_DAY

        return unlocked
    }
}
