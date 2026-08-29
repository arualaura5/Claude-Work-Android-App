package com.laurasheehan.royalmiles.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.UiWeek
import com.laurasheehan.royalmiles.ui.components.Affirmations
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalendarViewModel(private val repository: PlanRepository) : ViewModel() {

    private val _affirmations = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val affirmations: SharedFlow<String> = _affirmations.asSharedFlow()

    val weeks: StateFlow<List<UiWeek>> = repository.observeWeeks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The week containing today, so the calendar can open on it rather than on week 1. */
    fun currentWeekIndex(weeks: List<UiWeek>): Int {
        val today = LocalDate.now()
        val index = weeks.indexOfFirst { !it.startDate.isAfter(today) && !it.startDate.plusDays(6).isBefore(today) }
        return if (index >= 0) index else 0
    }

    fun skip(session: SessionEntity) {
        viewModelScope.launch { repository.markSkipped(session.id) }
    }

    fun undoSkip(sessionId: Long) {
        viewModelScope.launch { repository.markIncomplete(sessionId) }
    }

    /** Dialling a week back — see [PlanRepository.scaleDownWeek]. Costs her nothing that scores. */
    fun scaleDownWeek(weekCommencing: LocalDate) {
        viewModelScope.launch { repository.scaleDownWeek(weekCommencing) }
    }

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
