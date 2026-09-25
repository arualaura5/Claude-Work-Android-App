package com.laurasheehan.royalmiles.data.health

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.sync.ImportDecision
import com.laurasheehan.royalmiles.core.sync.SessionFacts
import com.laurasheehan.royalmiles.core.sync.WorkoutFacts
import com.laurasheehan.royalmiles.core.sync.WorkoutImportPlanner
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Brings finished workouts in from Health Connect without anyone having to open the Sync screen.
 *
 * Runs whenever the app comes to the foreground. Before this, a run only reached the Activity log
 * if it was matched by hand within a week, and a missed week meant it was gone for good. Safe to
 * run repeatedly: a workout whose source id is already stored on a session is never imported again.
 */
class WorkoutAutoImporter(
    private val repository: PlanRepository,
    private val healthConnect: HealthConnectRepository,
) {
    // onStart can fire twice in quick succession (rotation, returning from Health Connect); two
    // overlapping runs would each see the workout as new and log it twice.
    private val mutex = Mutex()

    /** Returns how many workouts were logged, or null when Health Connect isn't set up. */
    suspend fun importNew(): Int? = mutex.withLock {
        if (!healthConnect.isAvailable() || !healthConnect.hasPermissions()) return@withLock null

        // Oldest first, so where two workouts compete for one planned session the earlier one wins.
        val workouts = healthConnect.recentWorkouts().sortedBy { it.start }
        val sessions = repository.observeSessions().first()
        val decisions = WorkoutImportPlanner.plan(
            workouts = workouts.map { it.toFacts() },
            sessions = sessions.map { it.toFacts() },
        )

        var logged = 0
        workouts.zip(decisions).forEach { (workout, decision) ->
            when (decision) {
                ImportDecision.Skip -> return@forEach
                is ImportDecision.Match -> log(decision.sessionId, workout)
                is ImportDecision.Fill -> log(decision.sessionId, workout)
                ImportDecision.AddExtra -> addExtra(workout, sessions)
            }
            logged++
        }
        logged
    }

    private suspend fun log(sessionId: Long, workout: ExternalWorkout) {
        repository.markComplete(
            id = sessionId,
            actualDistanceKm = workout.distanceKm,
            actualDurationMin = workout.durationMinutes,
            completedAt = workout.localDate,
            avgHeartRate = workout.avgHeartRate,
            maxHeartRate = workout.maxHeartRate,
            calories = workout.calories,
            elevationGainM = workout.elevationGainM,
            sourceApp = workout.sourceApp,
            sourceActivityId = workout.sourceActivityId,
        )
    }

    /** Filed under the week it happened in, borrowing phase and week number from the nearest planned day. */
    private suspend fun addExtra(workout: ExternalWorkout, sessions: List<SessionEntity>) {
        val type = workout.importableType ?: return
        val nearest = sessions.minByOrNull { abs(ChronoUnit.DAYS.between(it.date, workout.localDate)) } ?: return
        repository.addCustomSession(
            SessionEntity(
                eventId = nearest.eventId,
                date = workout.localDate,
                type = type,
                title = extraTitle(type),
                phase = nearest.phase,
                weekNumber = nearest.weekNumber,
                isCompleted = true,
                actualDistanceKm = workout.distanceKm,
                actualDurationMin = workout.durationMinutes,
                completedAt = workout.localDate,
                actualAvgHeartRate = workout.avgHeartRate,
                actualMaxHeartRate = workout.maxHeartRate,
                actualCalories = workout.calories,
                actualElevationGainM = workout.elevationGainM,
                sourceApp = workout.sourceApp,
                sourceActivityId = workout.sourceActivityId,
            ),
        )
    }

    private fun extraTitle(type: SessionType): String = when (type) {
        SessionType.CYCLE -> "Extra cycle"
        SessionType.SWIM -> "Extra swim"
        SessionType.YOGA -> "Extra yoga"
        SessionType.STRENGTH -> "Extra strength"
        else -> "Extra run"
    }
}

private fun ExternalWorkout.toFacts() = WorkoutFacts(
    sourceActivityId = sourceActivityId,
    date = localDate,
    type = importableType,
    distanceKm = distanceKm,
)

private fun SessionEntity.toFacts() = SessionFacts(
    id = id,
    date = date,
    type = type,
    targetDistanceKm = targetDistanceKm,
    isCompleted = isCompleted,
    isSkipped = isSkipped,
    completedAt = completedAt,
    sourceActivityId = sourceActivityId,
)
