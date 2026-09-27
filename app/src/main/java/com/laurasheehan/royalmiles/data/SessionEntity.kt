package com.laurasheehan.royalmiles.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import java.time.LocalDate

@Entity(tableName = "sessions", indices = [Index(value = ["eventId", "date"])])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val date: LocalDate,
    val type: SessionType,
    val title: String,
    val phase: TrainingPhase,
    val weekNumber: Int,
    val targetDistanceKm: Double? = null,
    val targetDurationMin: Int? = null,
    val optional: Boolean = false,
    val notes: String = "",
    val isCompleted: Boolean = false,
    val actualDistanceKm: Double? = null,
    val actualDurationMin: Int? = null,
    val completedAt: LocalDate? = null,
    val isCustom: Boolean = false,
    /** How it felt, 1 (rough/sore) to 5 (great) — captured only on completed sessions. */
    val effortRating: Int? = null,
    /**
     * Explicitly acknowledged as not done, as distinct from simply not done *yet*. Purely so a
     * session can be closed off and stop asking; nothing scores or penalises it.
     */
    val isSkipped: Boolean = false,
    /**
     * Skipped because she accepted a coach suggestion to replace it, not because it failed to
     * happen. Both are skipped as far as the plan is concerned, but they read back as very
     * different things: one is a decision she made with her coach, the other is a session she
     * missed. Labelling an agreed change "Didn't do it" tells her she failed at something she
     * chose to do. The replacement session's notes carry what it was and why it changed.
     */
    val supersededByCoach: Boolean = false,
    /**
     * Metrics captured from Health Connect at match time. Stored rather than re-read on demand so
     * the training history accumulates into something a coaching layer can actually reason over
     * later — Health Connect only retains a rolling window, and these are the numbers that make a
     * session interpretable after the fact.
     */
    val actualAvgHeartRate: Int? = null,
    val actualMaxHeartRate: Int? = null,
    val actualCalories: Int? = null,
    val actualElevationGainM: Int? = null,
    /** Source app package and its own activity id, kept so the original activity stays reachable. */
    val sourceApp: String? = null,
    val sourceActivityId: String? = null,
) {
    /** Garmin puts its activity id in Health Connect's clientRecordId, so this link is buildable. */
    val garminUrl: String?
        get() = sourceActivityId
            ?.takeIf { it.isNotBlank() && sourceApp == "com.garmin.android.apps.connectmobile" }
            ?.let { "https://connect.garmin.com/modern/activity/$it" }

    val isLoggable: Boolean get() = type != SessionType.REST

    /** Neither done nor written off — the only state that still wants something from you. */
    val isOutstanding: Boolean get() = !isCompleted && !isSkipped

    /**
     * Where the distance and duration came from. Ticking a session done used to copy the planned
     * figures into these fields, so for anything logged before that stopped, the honest answer is
     * often "unknown". Planned figures never become actuals any more.
     */
    val actualsSource: ActualsSource
        get() = when {
            actualDistanceKm == null && actualDurationMin == null -> ActualsSource.NONE
            sourceApp == MANUAL_SOURCE -> ActualsSource.YOU
            !sourceActivityId.isNullOrBlank() ->
                // A link made before the fix kept whatever the session already held, which may
                // have been the plan. Figures identical to the plan are flagged, not trusted.
                if (loggedBeforeTruthfulActuals && actualsMatchPlan) ActualsSource.UNCERTAIN else ActualsSource.RECORDED
            else -> ActualsSource.UNCERTAIN
        }

    private val loggedBeforeTruthfulActuals: Boolean
        get() = completedAt == null || completedAt.isBefore(TRUTHFUL_ACTUALS_SINCE)

    private val actualsMatchPlan: Boolean
        get() = actualDistanceKm == targetDistanceKm && actualDurationMin == targetDurationMin

    /** A distance she can be credited with: recorded or entered, never the plan's. */
    val knownDistanceKm: Double? get() = actualDistanceKm.takeIf { actualsSource != ActualsSource.UNCERTAIN }

    val knownDurationMin: Int? get() = actualDurationMin.takeIf { actualsSource != ActualsSource.UNCERTAIN }

    companion object {
        /** In `sourceApp`: the figures were typed in by Laura in Royal Miles. */
        const val MANUAL_SOURCE = "royal-miles:manual"

        /** The first day a completion can no longer copy planned figures into actuals. */
        val TRUTHFUL_ACTUALS_SINCE: LocalDate = LocalDate.of(2026, 9, 28)
    }
}

enum class ActualsSource(val label: String?) {
    NONE(null),
    RECORDED("Recorded by Garmin"),
    YOU("Entered by you"),
    UNCERTAIN("Source uncertain: may be the planned figures"),
}

@Entity(tableName = "plan_meta")
data class PlanMetaEntity(
    @PrimaryKey val id: Int = 0,
    val raceDate: LocalDate,
    val startDate: LocalDate,
    val raceDistanceKm: Double,
    val peakLongRunKm: Double,
    val planVersion: Int = 1,
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val raceDate: LocalDate,
    val raceDistanceKm: Double,
    val peakLongRunKm: Double,
    val planStartDate: LocalDate?,
    val planVersion: Int = 0,
)
