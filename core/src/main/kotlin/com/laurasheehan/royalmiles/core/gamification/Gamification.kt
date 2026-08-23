package com.laurasheehan.royalmiles.core.gamification

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.core.model.TrainingPlan
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
    STREAK_7("Week Warrior", "Reach a 7-day streak"),
    STREAK_14("Fortnight Fire", "Reach a 14-day streak"),
    DOUBLE_DIGITS("Double Digits", "Complete a run of 10km or more"),
    BASE_COMPLETE("Base Camp Cleared", "Complete every planned session in the Base phase"),
    PEAK_CONQUEROR("Peak Conqueror", "Complete the peak long run"),
    STRENGTH_REGULAR("Strength Regular", "Complete 5 strength sessions"),
    ZEN_RUNNER("Zen Runner", "Complete 5 yoga sessions"),
    TAPER_DISCIPLINE("Taper Discipline", "Complete every non-optional taper-week session"),
    RACE_DAY("Royal Parks Finisher", "Complete the half marathon"),
}

object GamificationEngine {

    val levels: List<Level> = listOf(
        Level(1, "Couch to Comeback", 0),
        Level(2, "Base Builder", 100),
        Level(3, "Finding Rhythm", 250),
        Level(4, "Momentum", 450),
        Level(5, "Long Run Legend", 700),
        Level(6, "Taper Master", 1000),
        Level(7, "Race Ready", 1350),
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

    fun levelFor(totalXp: Int): Level = levels.last { totalXp >= it.xpRequired }

    fun xpToNextLevel(totalXp: Int): Int? {
        val current = levelFor(totalXp)
        val next = levels.firstOrNull { it.xpRequired > current.xpRequired } ?: return null
        return next.xpRequired - totalXp
    }

    fun currentStreak(completions: List<CompletedSession>, asOf: LocalDate = LocalDate.now()): Int {
        val days = completions.map { it.date }.toSortedSet()
        if (days.isEmpty()) return 0
        var cursor = if (days.contains(asOf)) asOf else days.last()
        if (ChronoUnit.DAYS.between(cursor, asOf) > 1) return 0
        var streak = 0
        while (days.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun longestStreak(completions: List<CompletedSession>): Int {
        val days = completions.map { it.date }.toSortedSet().toList()
        if (days.isEmpty()) return 0
        var longest = 1
        var running = 1
        for (i in 1 until days.size) {
            running = if (ChronoUnit.DAYS.between(days[i - 1], days[i]) == 1L) running + 1 else 1
            longest = maxOf(longest, running)
        }
        return longest
    }

    fun evaluateBadges(completions: List<CompletedSession>, plan: TrainingPlan): Set<Badge> {
        val unlocked = mutableSetOf<Badge>()
        if (completions.isNotEmpty()) unlocked += Badge.FIRST_SESSION

        val streak = longestStreak(completions)
        if (streak >= 7) unlocked += Badge.STREAK_7
        if (streak >= 14) unlocked += Badge.STREAK_14

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
