package com.laurasheehan.royalmiles.core.sync

import com.laurasheehan.royalmiles.core.model.SessionType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * A finished workout as read from Health Connect, reduced to what matching needs.
 *
 * [sourceActivityId] is the source app's own id (Garmin's activity id). It is what makes importing
 * repeatable: a workout whose id is already stored on a session is never imported twice.
 */
data class WorkoutFacts(
    val sourceActivityId: String?,
    val date: LocalDate,
    /** Null when the workout isn't a kind of training the plan knows about (a walk, say). */
    val type: SessionType?,
    val distanceKm: Double?,
)

/** A plan row, reduced to what matching needs. */
data class SessionFacts(
    val id: Long,
    val date: LocalDate,
    val type: SessionType,
    val targetDistanceKm: Double?,
    val isCompleted: Boolean,
    val isSkipped: Boolean,
    val completedAt: LocalDate?,
    val sourceActivityId: String?,
)

sealed interface ImportDecision {
    /** Already on a session, not a kind of training the plan tracks, or has no id to dedupe on. */
    data object Skip : ImportDecision

    /** Log it against this outstanding planned session. */
    data class Match(val sessionId: Long) : ImportDecision

    /**
     * This session was already ticked off by hand on the workout's day, with no workout attached.
     * Fill in the real numbers rather than logging the same run a second time.
     */
    data class Fill(val sessionId: Long) : ImportDecision

    /** Nothing in the plan fits — record it as an extra session so it still shows up. */
    data object AddExtra : ImportDecision
}

/**
 * Decides, for each workout, where it belongs in the plan. Pure, so it can be tested without
 * Health Connect or a database; the app applies the decisions.
 *
 * Mirrors the manual Sync screen's rule — a planned session within [MATCH_WINDOW_DAYS] days of the
 * workout — and picks the closest one, using distance to break ties so a long run lands on the long
 * run slot rather than a same-distance-from-today easy run.
 */
object WorkoutImportPlanner {
    const val MATCH_WINDOW_DAYS = 2L

    private val RUN_TYPES = setOf(SessionType.EASY_RUN, SessionType.LONG_RUN, SessionType.RACE)

    fun plan(workouts: List<WorkoutFacts>, sessions: List<SessionFacts>): List<ImportDecision> {
        val linkedIds = sessions.mapNotNull { it.sourceActivityId?.takeIf(String::isNotBlank) }.toMutableSet()
        val claimed = mutableSetOf<Long>()

        return workouts.map { workout ->
            val id = workout.sourceActivityId?.takeIf(String::isNotBlank)
            val type = workout.type
            if (id == null || type == null || id in linkedIds) return@map ImportDecision.Skip
            linkedIds += id

            val filled = sessions.firstOrNull { session ->
                session.id !in claimed &&
                    session.isCompleted &&
                    session.sourceActivityId.isNullOrBlank() &&
                    compatible(type, session.type) &&
                    (session.completedAt ?: session.date) == workout.date
            }
            if (filled != null) {
                claimed += filled.id
                return@map ImportDecision.Fill(filled.id)
            }

            val match = sessions
                .filter { session ->
                    session.id !in claimed &&
                        !session.isCompleted &&
                        !session.isSkipped &&
                        compatible(type, session.type) &&
                        abs(ChronoUnit.DAYS.between(session.date, workout.date)) <= MATCH_WINDOW_DAYS
                }
                .minWithOrNull(
                    compareBy<SessionFacts>(
                        { abs(ChronoUnit.DAYS.between(it.date, workout.date)) },
                        { distanceGap(it, workout) },
                        { it.id },
                    ),
                )
            if (match != null) {
                claimed += match.id
                ImportDecision.Match(match.id)
            } else {
                ImportDecision.AddExtra
            }
        }
    }

    /** Health Connect can't tell an easy run from a long one, so any run fits any run slot. */
    private fun compatible(workoutType: SessionType, sessionType: SessionType): Boolean =
        if (workoutType in RUN_TYPES) sessionType in RUN_TYPES else workoutType == sessionType

    private fun distanceGap(session: SessionFacts, workout: WorkoutFacts): Double {
        val target = session.targetDistanceKm ?: return Double.MAX_VALUE
        val actual = workout.distanceKm ?: return Double.MAX_VALUE
        return abs(target - actual)
    }
}
