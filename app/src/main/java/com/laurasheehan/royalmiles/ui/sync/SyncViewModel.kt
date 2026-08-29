package com.laurasheehan.royalmiles.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.health.ExternalWorkout
import com.laurasheehan.royalmiles.data.health.HealthConnectRepository
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val hasPermission: Boolean = false,
    val workouts: List<ExternalWorkout> = emptyList(),
    val candidatesByWorkout: Map<ExternalWorkout, List<SessionEntity>> = emptyMap(),
    val justMatched: Boolean = false,
)

class SyncViewModel(
    private val repository: PlanRepository,
    private val healthConnect: HealthConnectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val available = healthConnect.isAvailable()
            val hasPermission = available && healthConnect.hasPermissions()
            if (!available || !hasPermission) {
                _uiState.update { it.copy(loading = false, available = available, hasPermission = hasPermission, workouts = emptyList()) }
                return@launch
            }
            val workouts = healthConnect.recentWorkouts()
            val allSessions = repository.observeSessions().first()
            val candidates = workouts.associateWith { workout ->
                allSessions.filter { session ->
                    session.isOutstanding && session.isLoggable &&
                        ChronoUnit.DAYS.between(session.date, workout.localDate).let { it in -2..2 }
                }.sortedBy { kotlin.math.abs(ChronoUnit.DAYS.between(it.date, workout.localDate)) }
            }
            _uiState.update {
                it.copy(loading = false, available = true, hasPermission = true, workouts = workouts, candidatesByWorkout = candidates)
            }
        }
    }

    fun onPermissionGranted() = refresh()

    fun permissionContract() = healthConnect.requestPermissionsContract()

    val permissionsToRequest: Set<String> get() = healthConnect.permissions

    fun match(workout: ExternalWorkout, session: SessionEntity) {
        viewModelScope.launch {
            repository.markComplete(
                id = session.id,
                // Prefer what was actually run over what was planned; fall back only if Health
                // Connect has no distance for it.
                actualDistanceKm = workout.distanceKm ?: session.targetDistanceKm,
                actualDurationMin = workout.durationMinutes,
                completedAt = workout.localDate,
                avgHeartRate = workout.avgHeartRate,
                maxHeartRate = workout.maxHeartRate,
                calories = workout.calories,
                elevationGainM = workout.elevationGainM,
                sourceApp = workout.sourceApp,
                sourceActivityId = workout.sourceActivityId,
            )
            _uiState.update { it.copy(justMatched = true) }
            refresh()
        }
    }

    fun consumeMatchedFlag() = _uiState.update { it.copy(justMatched = false) }
}
