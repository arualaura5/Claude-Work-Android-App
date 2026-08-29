package com.laurasheehan.royalmiles.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.Stats
import com.laurasheehan.royalmiles.ui.components.Affirmations
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val stats: Stats? = null,
    val today: List<SessionEntity> = emptyList(),
    val upNext: List<SessionEntity> = emptyList(),
    val daysToRace: Long = 0,
    val phaseMessage: String = "",
)

class DashboardViewModel(
    private val repository: PlanRepository,
    private val raceDate: LocalDate,
) : ViewModel() {

    private val _affirmations = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val affirmations: SharedFlow<String> = _affirmations.asSharedFlow()

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeStats(),
        repository.observeWeeks(),
    ) { stats, weeks ->
        val allSessions = weeks.flatMap { it.sessions }
        val today = LocalDate.now()
        val currentWeek = weeks.firstOrNull { !it.startDate.isAfter(today) && it.startDate.plusDays(6) >= today }
        val planStarted = weeks.any { !it.startDate.isAfter(today) }
        DashboardUiState(
            stats = stats,
            today = allSessions.filter { it.date == today },
            upNext = allSessions.filter { it.date.isAfter(today) && it.isOutstanding }
                .sortedBy { it.date }
                .take(4),
            daysToRace = ChronoUnit.DAYS.between(today, raceDate),
            phaseMessage = phaseMessage(currentWeek?.phase, planStarted),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun toggleComplete(session: SessionEntity) {
        viewModelScope.launch {
            if (session.isCompleted) {
                repository.markIncomplete(session.id)
            } else {
                repository.markComplete(session.id, session.targetDistanceKm, session.targetDurationMin)
                _affirmations.tryEmit(Affirmations.random())
            }
        }
    }
}

private fun phaseMessage(phase: TrainingPhase?, planStarted: Boolean): String = when {
    !planStarted -> "Your plan starts Monday. This week is just about resting up."
    phase == TrainingPhase.BASE -> "Laying the foundation — this part isn't about speed."
    phase == TrainingPhase.BUILD -> "Building back, one weekend at a time."
    phase == TrainingPhase.PEAK -> "Biggest week of the block. Trust what you've built."
    phase == TrainingPhase.TAPER -> "Almost there. Let your legs feel it."
    else -> ""
}
