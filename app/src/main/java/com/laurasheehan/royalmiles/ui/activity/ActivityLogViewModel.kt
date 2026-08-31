package com.laurasheehan.royalmiles.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.core.progress.EffortReading
import com.laurasheehan.royalmiles.core.progress.EffortSignal
import com.laurasheehan.royalmiles.core.progress.WeekSummaries
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class ActivityLogUiState(
    val sessions: List<SessionEntity> = emptyList(),
    val totalSessions: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalMinutes: Int = 0,
    val recentEffort: List<EffortReading> = emptyList(),
    val effortSignal: EffortSignal = EffortSignal.NONE,
)

/**
 * A plain record of what actually got done, newest first — distinct from the Calendar, which is
 * the plan. No XP or streaks here; the Dashboard already carries that framing.
 */
class ActivityLogViewModel(private val repository: PlanRepository) : ViewModel() {

    val uiState: StateFlow<ActivityLogUiState> = combine(
        repository.observeSessions(),
        repository.observeStats(),
    ) { all, stats ->
        val done = all
            .filter { it.isCompleted }
            .sortedByDescending { it.completedAt ?: it.date }
        ActivityLogUiState(
            sessions = done,
            totalSessions = done.size,
            totalDistanceKm = done.sumOf { it.actualDistanceKm ?: it.targetDistanceKm ?: 0.0 },
            totalMinutes = done.sumOf { it.actualDurationMin ?: it.targetDurationMin ?: 0 },
            recentEffort = stats.recentEffort,
            effortSignal = stats.effortSignal,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActivityLogUiState())

    /** Dialling the current week back — see [PlanRepository.scaleDownWeek]. Costs nothing that scores. */
    fun scaleDownThisWeek() {
        val week = WeekSummaries.weekCommencing(LocalDate.now())
        viewModelScope.launch { repository.scaleDownWeek(week) }
    }
}
