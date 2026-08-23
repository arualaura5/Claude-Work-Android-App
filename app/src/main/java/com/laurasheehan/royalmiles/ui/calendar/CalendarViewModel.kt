package com.laurasheehan.royalmiles.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.UiWeek
import com.laurasheehan.royalmiles.ui.components.Affirmations
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
