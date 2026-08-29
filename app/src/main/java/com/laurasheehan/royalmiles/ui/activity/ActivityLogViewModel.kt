package com.laurasheehan.royalmiles.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ActivityLogUiState(
    val sessions: List<SessionEntity> = emptyList(),
    val totalSessions: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalMinutes: Int = 0,
)

/**
 * A plain record of what actually got done, newest first — distinct from the Calendar, which is
 * the plan. No XP or streaks here; the Dashboard already carries that framing.
 */
class ActivityLogViewModel(repository: PlanRepository) : ViewModel() {

    val uiState: StateFlow<ActivityLogUiState> = repository.observeSessions()
        .map { all ->
            val done = all
                .filter { it.isCompleted }
                .sortedByDescending { it.completedAt ?: it.date }
            ActivityLogUiState(
                sessions = done,
                totalSessions = done.size,
                totalDistanceKm = done.sumOf { it.actualDistanceKm ?: it.targetDistanceKm ?: 0.0 },
                totalMinutes = done.sumOf { it.actualDurationMin ?: it.targetDurationMin ?: 0 },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActivityLogUiState())
}
