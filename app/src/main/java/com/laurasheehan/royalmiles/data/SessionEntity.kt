package com.laurasheehan.royalmiles.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import java.time.LocalDate

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
}

@Entity(tableName = "plan_meta")
data class PlanMetaEntity(
    @PrimaryKey val id: Int = 0,
    val raceDate: LocalDate,
    val startDate: LocalDate,
    val raceDistanceKm: Double,
    val peakLongRunKm: Double,
)
