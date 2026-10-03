package com.laurasheehan.royalmiles.data.garmin

import androidx.room.withTransaction
import com.laurasheehan.royalmiles.RaceConfig
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.AppDatabase
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.health.ExternalWorkout
import com.laurasheehan.royalmiles.data.health.GARMIN_PACKAGE
import kotlin.math.abs

/**
 * Every change a Garmin activity makes to her log, each in one transaction with a record of what
 * it replaced. The rules that keep a link honest are enforced here, not in the screen:
 * one activity holds at most one session, one session at most one activity, and only a session
 * still outstanding can take one. Each call returns false, changing nothing, if a rule would break.
 */
class GarminLinks(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val sessions = db.sessionDao()
    private val decisions = db.garminDecisionDao()

    /** Links it to a planned session: automatically, as she said, or as another day's done early or late. */
    suspend fun link(workout: ExternalWorkout, sessionId: Long, decision: GarminDecision): Boolean = db.withTransaction {
        val id = workout.sourceActivityId ?: return@withTransaction false
        if (!free(id)) return@withTransaction false
        val session = sessions.getById(sessionId) ?: return@withTransaction false
        if (!session.canTakeActivity) return@withTransaction false
        sessions.update(session.withGarmin(workout))
        decisions.insert(record(workout, decision, sessionId = session.id, previous = session))
        true
    }

    /** It replaced that day's planned session: the plan reads "Replaced", and what she did is logged. */
    suspend fun swap(workout: ExternalWorkout, plannedId: Long): Boolean = db.withTransaction {
        val id = workout.sourceActivityId ?: return@withTransaction false
        val kind = GarminMatcher.kindOf(workout) ?: return@withTransaction false
        if (!free(id)) return@withTransaction false
        val planned = sessions.getById(plannedId) ?: return@withTransaction false
        if (!planned.canTakeActivity) return@withTransaction false
        sessions.update(
            planned.copy(
                isCompleted = false,
                isSkipped = true,
                // Read back as "Replaced", like an agreed coach swap: a decision, not a miss.
                supersededByCoach = true,
                actualDistanceKm = null,
                actualDurationMin = null,
                effortRating = null,
                completedAt = null,
            ),
        )
        val createdId = sessions.insert(
            newSession(workout, kind, template = planned).copy(
                notes = "Was: ${planned.title}. Swapped by you for what Garmin recorded.",
            ),
        )
        decisions.insert(
            record(workout, GarminDecision.SWAPPED, sessionId = createdId, replaced = planned, createdSessionId = createdId),
        )
        true
    }

    /** Done, counted, but not part of the plan. */
    suspend fun extra(workout: ExternalWorkout): Boolean = db.withTransaction {
        val id = workout.sourceActivityId ?: return@withTransaction false
        val kind = GarminMatcher.kindOf(workout) ?: return@withTransaction false
        if (!free(id)) return@withTransaction false
        val template = sessions.getAll().minByOrNull { abs(it.date.toEpochDay() - workout.localDate.toEpochDay()) }
        val createdId = sessions.insert(newSession(workout, kind, template).copy(notes = "Extra: not part of the plan."))
        decisions.insert(record(workout, GarminDecision.EXTRA, sessionId = createdId, createdSessionId = createdId))
        true
    }

    suspend fun ignore(workout: ExternalWorkout): Boolean = db.withTransaction {
        val id = workout.sourceActivityId ?: return@withTransaction false
        if (!free(id)) return@withTransaction false
        decisions.insert(record(workout, GarminDecision.IGNORED, sessionId = null))
        true
    }

    /** Puts back exactly what the decision changed. The record stays, marked undone. */
    suspend fun undo(decisionId: Long): Boolean = db.withTransaction {
        val decision = decisions.getById(decisionId) ?: return@withTransaction false
        if (!decision.isActive) return@withTransaction false
        decision.createdSessionId?.let { id -> sessions.getById(id)?.let { sessions.delete(it) } }
        decision.previousSessionJson?.let { sessions.update(SessionCodec.decode(it)) }
        decision.replacedSessionJson?.let { sessions.update(SessionCodec.decode(it)) }
        decisions.update(decision.copy(undoneAtMillis = now()))
        true
    }

    suspend fun acknowledge(decisionId: Long) {
        val decision = decisions.getById(decisionId) ?: return
        if (decision.isActive && !decision.acknowledged) decisions.update(decision.copy(acknowledged = true))
    }

    /** Not decided already, and not already on a session (linked by hand on the Sync screen, say). */
    private suspend fun free(activityId: String): Boolean =
        decisions.activeFor(activityId) == null && sessions.getAll().none { it.sourceActivityId == activityId }

    private val SessionEntity.canTakeActivity: Boolean
        get() = isOutstanding && isLoggable && sourceActivityId.isNullOrBlank()

    private fun record(
        workout: ExternalWorkout,
        decision: GarminDecision,
        sessionId: Long?,
        previous: SessionEntity? = null,
        replaced: SessionEntity? = null,
        createdSessionId: Long? = null,
    ) = GarminDecisionEntity(
        activityId = workout.sourceActivityId!!,
        decision = decision,
        sessionId = sessionId,
        activityDate = workout.localDate,
        activityName = workout.title,
        activityKind = GarminMatcher.kindOf(workout)?.name,
        distanceKm = workout.distanceKm,
        durationMin = workout.durationMinutes,
        avgHeartRate = workout.avgHeartRate,
        maxHeartRate = workout.maxHeartRate,
        previousSessionJson = previous?.let(SessionCodec::encode),
        replacedSessionJson = replaced?.let(SessionCodec::encode),
        createdSessionId = createdSessionId,
        decidedAtMillis = now(),
    )

    private fun newSession(workout: ExternalWorkout, kind: ActivityKind, template: SessionEntity?) = SessionEntity(
        eventId = template?.eventId ?: RaceConfig.ACTIVE_EVENT_ID,
        date = workout.localDate,
        type = GarminMatcher.sessionTypeFor(kind),
        title = workout.title?.takeIf { it.isNotBlank() } ?: "Garmin ${kind.noun}",
        phase = template?.phase ?: TrainingPhase.BASE,
        weekNumber = template?.weekNumber ?: 1,
        isCustom = true,
    ).withGarmin(workout)
}

/** Completed with exactly what Garmin recorded: no planned figure ever stands in for a missing one. */
internal fun SessionEntity.withGarmin(workout: ExternalWorkout) = copy(
    isCompleted = true,
    isSkipped = false,
    completedAt = workout.localDate,
    actualDistanceKm = workout.distanceKm,
    actualDurationMin = workout.durationMinutes,
    actualAvgHeartRate = workout.avgHeartRate,
    actualMaxHeartRate = workout.maxHeartRate,
    actualCalories = workout.calories,
    actualElevationGainM = workout.elevationGainM,
    sourceApp = workout.sourceApp ?: GARMIN_PACKAGE,
    sourceActivityId = workout.sourceActivityId,
)
