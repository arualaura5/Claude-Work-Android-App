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

/**
 * Every badge here is monotonic: once earned it can never be taken away, and nothing can make one
 * permanently unreachable. That rules out the all-or-nothing shape the set used to have — "complete
 * every session in the Base phase" locked itself forever the first week she caught a cold, and then
 * sat padlocked on the dashboard for the rest of the block as a standing record of it. These count
 * acts instead: sessions logged, ground covered, distances reached. A bad week delays a badge; it
 * can no longer destroy one.
 *
 * Declaration order is roughly the order they fall, and the dashboard shows the unlocked ones plus
 * the next few rather than the whole set, so the screen is never mostly padlocks.
 */
enum class Badge(val title: String, val description: String) {
    FIRST_SESSION("First Steps", "Log your first session"),
    FIRST_5K("Five Back", "Run 5km in a single session"),
    TEN_SESSIONS("Ten Logged", "Log ten sessions"),
    WEEK_STREAK_3("Three in a Row", "Train in three consecutive weeks"),
    FIRST_8K("Eight Back", "Run 8km in a single session"),
    FIFTY_KM("Fifty Kilometres", "Cover 50km of running in total"),
    STRENGTH_REGULAR("Strength Regular", "Complete five strength sessions"),
    ZEN_RUNNER("Zen Runner", "Complete five yoga sessions"),
    DOUBLE_DIGITS("Double Digits", "Run 10km or more in a single session"),
    TWENTY_FIVE_SESSIONS("Twenty-Five Logged", "Log twenty-five sessions"),
    WEEK_STREAK_6("Six in a Row", "Train in six consecutive weeks"),
    FIFTEEN_KM("Fifteen Back", "Run 15km in a single session"),
    HUNDRED_KM("Century Club", "Cover 100km of running in total"),
    PEAK_CONQUEROR("Peak Conqueror", "Reach the plan's peak long-run distance"),
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

    /** Distance only counts toward running badges when it was actually run. */
    private val CompletedSession.isRun: Boolean
        get() = type == SessionType.EASY_RUN || type == SessionType.LONG_RUN || type == SessionType.RACE

    fun evaluateBadges(completions: List<CompletedSession>, plan: TrainingPlan): Set<Badge> {
        val unlocked = mutableSetOf<Badge>()
        if (completions.isNotEmpty()) unlocked += Badge.FIRST_SESSION
        if (completions.size >= 10) unlocked += Badge.TEN_SESSIONS
        if (completions.size >= 25) unlocked += Badge.TWENTY_FIVE_SESSIONS

        val streak = longestWeekStreak(completions)
        if (streak >= 3) unlocked += Badge.WEEK_STREAK_3
        if (streak >= 6) unlocked += Badge.WEEK_STREAK_6

        val runs = completions.filter { it.isRun }
        val furthest = runs.maxOfOrNull { it.distanceKm ?: 0.0 } ?: 0.0
        if (furthest >= 5.0) unlocked += Badge.FIRST_5K
        if (furthest >= 8.0) unlocked += Badge.FIRST_8K
        if (furthest >= 10.0) unlocked += Badge.DOUBLE_DIGITS
        if (furthest >= 15.0) unlocked += Badge.FIFTEEN_KM
        // Date-independent, so doing the peak long run a day late still counts.
        if (furthest >= plan.peakLongRunKm) unlocked += Badge.PEAK_CONQUEROR

        val totalKm = runs.sumOf { it.distanceKm ?: 0.0 }
        if (totalKm >= 50.0) unlocked += Badge.FIFTY_KM
        if (totalKm >= 100.0) unlocked += Badge.HUNDRED_KM

        if (completions.count { it.type == SessionType.STRENGTH } >= 5) unlocked += Badge.STRENGTH_REGULAR
        if (completions.count { it.type == SessionType.YOGA } >= 5) unlocked += Badge.ZEN_RUNNER

        if (completions.any { it.type == SessionType.RACE }) unlocked += Badge.RACE_DAY

        return unlocked
    }
}
