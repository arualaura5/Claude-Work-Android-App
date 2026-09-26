package com.laurasheehan.royalmiles.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.coach.CoachRepository
import com.laurasheehan.royalmiles.data.garmin.GarminActivityFeed
import com.laurasheehan.royalmiles.data.health.ExternalWorkout
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SyncUiState(
    val loading: Boolean = true,
    /** The cloud coach is connected, so its key can open the Garmin feed. */
    val connected: Boolean = false,
    val error: String? = null,
    val workouts: List<ExternalWorkout> = emptyList(),
    val candidatesByWorkout: Map<ExternalWorkout, List<SessionEntity>> = emptyMap(),
    val justMatched: Boolean = false,
)

class SyncViewModel(
    private val repository: PlanRepository,
    private val coachRepository: CoachRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val credentials = coachRepository.cloudCredentials()
            if (credentials == null) {
                _uiState.update { it.copy(loading = false, connected = false, workouts = emptyList()) }
                return@launch
            }
            val (address, key) = credentials
            val fetched = withContext(Dispatchers.IO) {
                runCatching { GarminActivityFeed.fetch(address, key) }
            }
            val feed = fetched.getOrElse { error ->
                _uiState.update {
                    it.copy(loading = false, connected = true, error = error.message ?: "Couldn't load Garmin workouts.")
                }
                return@launch
            }

            val allSessions = repository.observeSessions().first()
            // An activity already linked to a session is logged; offering it again invites a double log.
            val linkedIds = allSessions.mapNotNull { it.sourceActivityId }.toSet()
            val workouts = feed.filter { it.sourceActivityId !in linkedIds }
            val candidates = workouts.associateWith { workout ->
                allSessions.filter { session ->
                    session.sourceActivityId == null &&
                        (session.isOutstanding || session.isCompleted) &&
                        session.isLoggable &&
                        ChronoUnit.DAYS.between(session.date, workout.localDate).let { it in -2..2 }
                }.sortedWith(
                    compareBy<SessionEntity>(
                        { if (it.isOutstanding) 0 else 1 },
                        { kotlin.math.abs(ChronoUnit.DAYS.between(it.date, workout.localDate)) },
                    ),
                )
            }
            _uiState.update {
                it.copy(loading = false, connected = true, workouts = workouts, candidatesByWorkout = candidates)
            }
        }
    }

    fun match(workout: ExternalWorkout, session: SessionEntity) {
        viewModelScope.launch {
            repository.markComplete(
                id = session.id,
                // Prefer what was actually run over what was planned; fall back only if Garmin
                // has no distance for it.
                actualDistanceKm = if (session.isCompleted) {
                    workout.distanceKm.takeIf { session.actualDistanceKm == null }
                } else {
                    workout.distanceKm ?: session.targetDistanceKm
                },
                actualDurationMin = workout.durationMinutes.takeIf { !session.isCompleted || session.actualDurationMin == null },
                completedAt = if (session.isCompleted) session.completedAt ?: workout.localDate else workout.localDate,
                avgHeartRate = workout.avgHeartRate.takeIf { session.actualAvgHeartRate == null },
                maxHeartRate = workout.maxHeartRate.takeIf { session.actualMaxHeartRate == null },
                calories = workout.calories.takeIf { session.actualCalories == null },
                elevationGainM = workout.elevationGainM.takeIf { session.actualElevationGainM == null },
                sourceApp = workout.sourceApp,
                sourceActivityId = workout.sourceActivityId,
            )
            _uiState.update { it.copy(justMatched = true) }
            refresh()
        }
    }

    fun consumeMatchedFlag() = _uiState.update { it.copy(justMatched = false) }
}
