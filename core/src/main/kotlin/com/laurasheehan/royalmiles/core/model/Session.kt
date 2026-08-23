package com.laurasheehan.royalmiles.core.model

import java.time.LocalDate

data class Session(
    val date: LocalDate,
    val type: SessionType,
    val title: String,
    val phase: TrainingPhase,
    val targetDistanceKm: Double? = null,
    val targetDurationMin: Int? = null,
    val optional: Boolean = false,
    val notes: String = "",
) {
    val isLoggable: Boolean get() = type != SessionType.REST
}

data class TrainingWeek(
    val weekNumber: Int,
    val phase: TrainingPhase,
    val startDate: LocalDate,
    val sessions: List<Session>,
) {
    val longRunKm: Double?
        get() = sessions.firstOrNull { it.type == SessionType.LONG_RUN || it.type == SessionType.RACE }?.targetDistanceKm
}

data class TrainingPlan(
    val raceDate: LocalDate,
    val startDate: LocalDate,
    val raceDistanceKm: Double,
    val peakLongRunKm: Double,
    val weeks: List<TrainingWeek>,
) {
    val allSessions: List<Session> get() = weeks.flatMap { it.sessions }
}
