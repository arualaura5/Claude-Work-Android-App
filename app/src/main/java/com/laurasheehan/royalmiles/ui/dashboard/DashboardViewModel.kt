package com.laurasheehan.royalmiles.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.Stats
import com.laurasheehan.royalmiles.data.UiWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val stats: Stats? = null,
    val today: List<SessionEntity> = emptyList(),
    val upNext: List<SessionEntity> = emptyList(),
    val daysToRace: Long = 0,
)

class DashboardViewModel(private val repository: PlanRepository, private val raceDate: LocalDate) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeStats(),
        repository.observeWeeks(),
    ) { stats, weeks ->
        val allSessions = weeks.flatMap { it.sessions }
        val today = LocalDate.now()
        DashboardUiState(
            stats = stats,
            today = allSessions.filter { it.date == today },
            upNext = allSessions.filter { it.date.isAfter(today) && !it.isCompleted }
                .sortedBy { it.date }
                .take(4),
            daysToRace = java.time.temporal.ChronoUnit.DAYS.between(today, raceDate),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun toggleComplete(session: SessionEntity) {
        viewModelScope.launch {
            if (session.isCompleted) {
                repository.markIncomplete(session.id)
            } else {
                repository.markComplete(session.id, session.targetDistanceKm, session.targetDurationMin)
            }
        }
    }
}
