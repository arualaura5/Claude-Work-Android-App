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
) {
    val isLoggable: Boolean get() = type != SessionType.REST
}

@Entity(tableName = "plan_meta")
data class PlanMetaEntity(
    @PrimaryKey val id: Int = 0,
    val raceDate: LocalDate,
    val startDate: LocalDate,
    val raceDistanceKm: Double,
    val peakLongRunKm: Double,
)
