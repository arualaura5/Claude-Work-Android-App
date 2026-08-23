package com.laurasheehan.royalmiles.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.UiWeek
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalendarViewModel(private val repository: PlanRepository) : ViewModel() {

    val weeks: StateFlow<List<UiWeek>> = repository.observeWeeks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
