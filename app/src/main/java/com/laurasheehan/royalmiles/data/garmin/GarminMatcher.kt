package com.laurasheehan.royalmiles.data.garmin

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.health.ExternalWorkout
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** The kind of training an activity or a planned session is, for matching one to the other. */
enum class ActivityKind(val noun: String) {
    RUN("run"),
    CYCLE("bike ride"),
    SWIM("swim"),
    STRENGTH("strength session"),
    YOGA("yoga session"),
}

sealed interface MatchResult {
    /** A dummy recording (a few seconds, a few metres), or not training at all, like a walk. */
    data object NotTraining : MatchResult

    /** One planned session fits: same day, same kind, about the planned size. Linked without asking. */
    data class Confident(val session: SessionEntity) : MatchResult

    /** Anything less certain: she decides. */
    data class Ask(
        val reason: AskReason,
        /** Everything still planned that day, any kind: what a swap would replace. */
        val plannedThatDay: List<SessionEntity>,
        /** Planned that day and of the same kind: what it might simply be. */
        val sameKindThatDay: List<SessionEntity>,
        /** The same kind planned a few days either side: done early or late. */
        val nearby: List<SessionEntity>,
    ) : MatchResult
}

enum class AskReason { DIFFERENT_SIZE, DIFFERENT_KIND, NOTHING_PLANNED, SEVERAL, SHE_SAID_NOT_THIS }

/**
 * Decides whether a Garmin activity can be linked to her plan without asking. Deliberately strict:
 * a wrong automatic link is worse than a question, so only one clear fit is ever taken for granted.
 */
object GarminMatcher {
    /** How far distance (or, without one, time) may differ from the plan and still be "the same". */
    const val TOLERANCE = 0.25
    const val MIN_MINUTES = 2
    const val MIN_KM = 0.5
    const val NEARBY_DAYS = 3L

    fun kindOf(workout: ExternalWorkout): ActivityKind? = when (workout.exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        -> ActivityKind.RUN
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        -> ActivityKind.CYCLE
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        -> ActivityKind.SWIM
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> ActivityKind.STRENGTH
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> ActivityKind.YOGA
        else -> null
    }

    fun kindOf(type: SessionType): ActivityKind? = when (type) {
        SessionType.EASY_RUN, SessionType.LONG_RUN, SessionType.RACE -> ActivityKind.RUN
        SessionType.CYCLE -> ActivityKind.CYCLE
        SessionType.SWIM -> ActivityKind.SWIM
        SessionType.STRENGTH -> ActivityKind.STRENGTH
        SessionType.YOGA -> ActivityKind.YOGA
        SessionType.REST -> null
    }

    /** The session type a swap or an extra is logged as. */
    fun sessionTypeFor(kind: ActivityKind): SessionType = when (kind) {
        ActivityKind.RUN -> SessionType.EASY_RUN
        ActivityKind.CYCLE -> SessionType.CYCLE
        ActivityKind.SWIM -> SessionType.SWIM
        ActivityKind.STRENGTH -> SessionType.STRENGTH
        ActivityKind.YOGA -> SessionType.YOGA
    }

    fun isDummy(workout: ExternalWorkout, kind: ActivityKind): Boolean {
        if (workout.durationMinutes < MIN_MINUTES) return true
        val distanceSport = kind == ActivityKind.RUN || kind == ActivityKind.CYCLE || kind == ActivityKind.SWIM
        return distanceSport && (workout.distanceKm ?: 0.0) < MIN_KM
    }

    /**
     * @param sessions her whole plan
     * @param sheSaidNotThis she undid an automatic link for this activity, so it's never assumed again
     */
    fun match(workout: ExternalWorkout, sessions: List<SessionEntity>, sheSaidNotThis: Boolean = false): MatchResult {
        val kind = kindOf(workout) ?: return MatchResult.NotTraining
        if (isDummy(workout, kind)) return MatchResult.NotTraining

        val day = workout.localDate
        val open = sessions.filter { it.isOutstanding && it.isLoggable && it.sourceActivityId.isNullOrBlank() }
        val thatDay = open.filter { it.date == day }
        val sameKind = thatDay.filter { kindOf(it.type) == kind }
        val nearby = open
            .filter { it.date != day && kindOf(it.type) == kind && abs(ChronoUnit.DAYS.between(day, it.date)) <= NEARBY_DAYS }
            .sortedWith(compareBy<SessionEntity>({ abs(ChronoUnit.DAYS.between(day, it.date)) }, { it.date }))

        val single = sameKind.singleOrNull()
        if (single != null && !sheSaidNotThis && similarSize(workout, single)) return MatchResult.Confident(single)

        val reason = when {
            sheSaidNotThis -> AskReason.SHE_SAID_NOT_THIS
            sameKind.size > 1 -> AskReason.SEVERAL
            single != null -> AskReason.DIFFERENT_SIZE
            thatDay.isNotEmpty() -> AskReason.DIFFERENT_KIND
            else -> AskReason.NOTHING_PLANNED
        }
        return MatchResult.Ask(reason, thatDay, sameKind, nearby)
    }

    /** Distance within the tolerance when both have one; otherwise time; otherwise nothing to compare. */
    fun similarSize(workout: ExternalWorkout, session: SessionEntity): Boolean {
        val km = workout.distanceKm
        val targetKm = session.targetDistanceKm
        if (km != null && targetKm != null && targetKm > 0) return abs(km / targetKm - 1) <= TOLERANCE
        val targetMin = session.targetDurationMin
        if (targetMin != null && targetMin > 0) return abs(workout.durationMinutes.toDouble() / targetMin - 1) <= TOLERANCE
        return true
    }

    /** Recent enough to ask about: from the day this started, and never more than a week back. */
    fun inWindow(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(SMART_MATCH_SINCE) && !date.isBefore(today.minusDays(7)) && !date.isAfter(today)

    /** Older activities stay on the Sync screen; they're never raised as questions. */
    val SMART_MATCH_SINCE: LocalDate = LocalDate.of(2026, 9, 27)
}
