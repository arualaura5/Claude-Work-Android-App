package com.laurasheehan.royalmiles.data.health

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.sync.GarminActivityTypes
import com.laurasheehan.royalmiles.core.sync.ImportDecision
import com.laurasheehan.royalmiles.core.sync.SessionFacts
import com.laurasheehan.royalmiles.core.sync.WorkoutFacts
import com.laurasheehan.royalmiles.core.sync.WorkoutImportPlanner
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.garmin.GarminActivity
import com.laurasheehan.royalmiles.data.garmin.GarminActivityFeed
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Logs finished workouts against the plan without anyone having to open the Sync screen.
 *
 * Two sources, in order of trust:
 *  1. The cloud refresh's Garmin activity feed — Garmin's own record, every activity with its real
 *     type and training effect, however long ago it was.
 *  2. Health Connect — a backup for when the feed isn't connected or hasn't caught up yet.
 *
 * Garmin writes its activity id into Health Connect too, so the same run arriving from both is
 * recognised as one. Safe to run repeatedly: a workout whose id is already stored on a session is
 * never logged again.
 *
 * @param fetchFeed the raw activity feed, null when no Worker is connected.
 */
class WorkoutAutoImporter(
    private val repository: PlanRepository,
    private val healthConnect: HealthConnectRepository,
    private val fetchFeed: suspend () -> String?,
) {
    // onStart can fire twice in quick succession (rotation, returning from Health Connect); two
    // overlapping runs would each see the workout as new and log it twice.
    private val mutex = Mutex()

    /** Returns how many workouts were logged, or null when neither source is set up. */
    suspend fun importNew(): Int? = mutex.withLock {
        val feed = runCatching { fetchFeed()?.let(GarminActivityFeed::parse) }.getOrNull()
        val healthConnectReady = runCatching { healthConnect.isAvailable() && healthConnect.hasPermissions() }
            .getOrDefault(false)
        if (feed == null && !healthConnectReady) return@withLock null

        val fromHealthConnect = if (healthConnectReady) {
            runCatching { healthConnect.recentWorkouts() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        // Feed first so it wins when both carry the same id; then oldest first (a stable sort keeps
        // that order within a day), so where two workouts compete for a planned session the earlier
        // one gets it.
        val incoming = (feed.orEmpty().map { it.toIncoming() } + fromHealthConnect.map { it.toIncoming() })
            .sortedBy { it.date }

        var sessions = repository.observeSessions().first()
        backfillTrainingEffect(feed.orEmpty(), sessions)
        sessions = repository.observeSessions().first()

        val decisions = WorkoutImportPlanner.plan(
            workouts = incoming.map { it.facts },
            sessions = sessions.map { it.toFacts() },
        )

        var logged = 0
        incoming.zip(decisions).forEach { (workout, decision) ->
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

    /** Runs logged from Health Connect before the feed existed get Garmin's training effect and load. */
    private suspend fun backfillTrainingEffect(feed: List<GarminActivity>, sessions: List<SessionEntity>) {
        if (feed.isEmpty()) return
        val byId = feed.associateBy { it.activityId }
        sessions
            .filter { it.isCompleted && it.aerobicTrainingEffect == null && it.trainingLoad == null }
            .forEach { session ->
                val activity = session.sourceActivityId?.let(byId::get) ?: return@forEach
                if (activity.aerobicTrainingEffect == null && activity.trainingLoad == null) return@forEach
                repository.updateSession(
                    session.copy(
                        aerobicTrainingEffect = activity.aerobicTrainingEffect,
                        trainingLoad = activity.trainingLoad,
                    ),
                )
            }
    }

    private suspend fun log(sessionId: Long, workout: Incoming) {
        repository.markComplete(
            id = sessionId,
            actualDistanceKm = workout.distanceKm,
            actualDurationMin = workout.durationMinutes,
            completedAt = workout.date,
            avgHeartRate = workout.avgHeartRate,
            maxHeartRate = workout.maxHeartRate,
            calories = workout.calories,
            elevationGainM = workout.elevationGainM,
            sourceApp = workout.sourceApp,
            sourceActivityId = workout.sourceActivityId,
            aerobicTrainingEffect = workout.aerobicTrainingEffect,
            trainingLoad = workout.trainingLoad,
        )
    }

    /** Filed under the week it happened in, borrowing phase and week number from the nearest planned day. */
    private suspend fun addExtra(workout: Incoming, sessions: List<SessionEntity>) {
        val type = workout.facts.type ?: return
        val nearest = sessions.minByOrNull { abs(ChronoUnit.DAYS.between(it.date, workout.date)) } ?: return
        repository.addCustomSession(
            SessionEntity(
                eventId = nearest.eventId,
                date = workout.date,
                type = type,
                title = extraTitle(type),
                phase = nearest.phase,
                weekNumber = nearest.weekNumber,
                isCompleted = true,
                actualDistanceKm = workout.distanceKm,
                actualDurationMin = workout.durationMinutes,
                completedAt = workout.date,
                actualAvgHeartRate = workout.avgHeartRate,
                actualMaxHeartRate = workout.maxHeartRate,
                actualCalories = workout.calories,
                actualElevationGainM = workout.elevationGainM,
                sourceApp = workout.sourceApp,
                sourceActivityId = workout.sourceActivityId,
                aerobicTrainingEffect = workout.aerobicTrainingEffect,
                trainingLoad = workout.trainingLoad,
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

/** A workout from either source, in the one shape the importer logs. */
private data class Incoming(
    val facts: WorkoutFacts,
    val sourceApp: String?,
    val durationMinutes: Int?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val calories: Int?,
    val elevationGainM: Int?,
    val aerobicTrainingEffect: Double?,
    val trainingLoad: Double?,
) {
    val date: LocalDate get() = facts.date
    val distanceKm: Double? get() = facts.distanceKm
    val sourceActivityId: String? get() = facts.sourceActivityId
}

private fun GarminActivity.toIncoming() = Incoming(
    facts = WorkoutFacts(
        sourceActivityId = activityId,
        date = date,
        type = GarminActivityTypes.sessionTypeFor(typeKey),
        distanceKm = distanceKm,
    ),
    // Recorded as Garmin's so the "View in Garmin Connect" link works, exactly as for a run that
    // came through Health Connect.
    sourceApp = GARMIN_PACKAGE,
    durationMinutes = durationMinutes,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
    calories = calories,
    elevationGainM = null,
    aerobicTrainingEffect = aerobicTrainingEffect,
    trainingLoad = trainingLoad,
)

private fun ExternalWorkout.toIncoming() = Incoming(
    facts = WorkoutFacts(
        sourceActivityId = sourceActivityId,
        date = localDate,
        type = importableType,
        distanceKm = distanceKm,
    ),
    sourceApp = sourceApp,
    durationMinutes = durationMinutes,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
    calories = calories,
    elevationGainM = elevationGainM,
    aerobicTrainingEffect = null,
    trainingLoad = null,
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
