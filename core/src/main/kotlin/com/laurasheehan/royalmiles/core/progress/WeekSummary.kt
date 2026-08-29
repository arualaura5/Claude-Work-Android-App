package com.laurasheehan.royalmiles.core.progress

import com.laurasheehan.royalmiles.core.gamification.CompletedSession
import com.laurasheehan.royalmiles.core.model.SessionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * What a single Monday-to-Sunday week actually contained.
 *
 * Deliberately carries no denominator. There is no "3 of 5 sessions" and no adherence percentage
 * anywhere in here, because a number that falls when life gets in the way is the most demotivating
 * thing this app could show someone rebuilding. Everything on this object is a thing that happened.
 */
data class WeekSummary(
    val weekCommencing: LocalDate,
    val sessions: Int,
    val distanceKm: Double,
    val minutes: Int,
    /** Longest single run of the week, if she ran at all. */
    val longestRunKm: Double?,
    /** True when that run is the furthest she has been since starting. Worth saying out loud. */
    val isFurthestYet: Boolean,
) {
    val isEmpty: Boolean get() = sessions == 0
}

object WeekSummaries {

    fun weekCommencing(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private val CompletedSession.isRun: Boolean
        get() = type == SessionType.EASY_RUN || type == SessionType.LONG_RUN || type == SessionType.RACE

    /**
     * Summarises the week commencing [weekCommencing], given every session logged so far.
     *
     * [all] is the full history rather than just that week's sessions because [WeekSummary.isFurthestYet]
     * can only be judged against everything that came before it.
     */
    fun summarise(all: List<CompletedSession>, weekCommencing: LocalDate): WeekSummary {
        val monday = weekCommencing(weekCommencing)
        val thisWeek = all.filter { weekCommencing(it.date) == monday }
        val longest = thisWeek.filter { it.isRun }.mapNotNull { it.distanceKm }.maxOrNull()
        val bestBefore = all
            .filter { it.isRun && it.date.isBefore(monday) }
            .mapNotNull { it.distanceKm }
            .maxOrNull()

        return WeekSummary(
            weekCommencing = monday,
            sessions = thisWeek.size,
            distanceKm = thisWeek.filter { it.isRun }.sumOf { it.distanceKm ?: 0.0 },
            minutes = thisWeek.sumOf { it.durationMin ?: 0 },
            longestRunKm = longest,
            isFurthestYet = longest != null && (bestBefore == null || longest > bestBefore),
        )
    }
}
