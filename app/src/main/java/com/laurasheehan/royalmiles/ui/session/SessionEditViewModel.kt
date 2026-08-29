package com.laurasheehan.royalmiles.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.UiWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val title: String = "",
    val type: SessionType = SessionType.EASY_RUN,
    val date: LocalDate = LocalDate.now(),
    val weekNumber: Int = 1,
    val phase: TrainingPhase = TrainingPhase.BASE,
    val distanceKm: String = "",
    val durationMin: String = "",
    val notes: String = "",
    val optional: Boolean = false,
    val isCompleted: Boolean = false,
    val isSkipped: Boolean = false,
    val actualDistanceKm: String = "",
    val actualDurationMin: String = "",
    val effortRating: Int? = null,
    val availableWeeks: List<UiWeek> = emptyList(),
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

class SessionEditViewModel(
    private val repository: PlanRepository,
    private val sessionId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionEditUiState(isNew = sessionId == null))
    val uiState: StateFlow<SessionEditUiState> = _uiState

    init {
        viewModelScope.launch {
            val weeks = repository.observeWeeks().first()
            val existing = sessionId?.let { repository.getSession(it) }
            _uiState.update { state ->
                if (existing != null) {
                    state.copy(
                        loading = false,
                        title = existing.title,
                        type = existing.type,
                        date = existing.date,
                        weekNumber = existing.weekNumber,
                        phase = existing.phase,
                        distanceKm = existing.targetDistanceKm?.let { formatNumber(it) } ?: "",
                        durationMin = existing.targetDurationMin?.toString() ?: "",
                        notes = existing.notes,
                        optional = existing.optional,
                        isCompleted = existing.isCompleted,
                        isSkipped = existing.isSkipped,
                        actualDistanceKm = existing.actualDistanceKm?.let { formatNumber(it) } ?: "",
                        actualDurationMin = existing.actualDurationMin?.toString() ?: "",
                        effortRating = existing.effortRating,
                        availableWeeks = weeks,
                    )
                } else {
                    val defaultWeek = weeks.firstOrNull { !it.startDate.isAfter(LocalDate.now()) && it.sessions.last().date >= LocalDate.now() }
                        ?: weeks.firstOrNull()
                    state.copy(
                        loading = false,
                        weekNumber = defaultWeek?.weekNumber ?: 1,
                        phase = defaultWeek?.phase ?: TrainingPhase.BUILD,
                        date = LocalDate.now(),
                        availableWeeks = weeks,
                    )
                }
            }
        }
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateType(value: SessionType) = _uiState.update { it.copy(type = value) }
    fun updateDate(value: LocalDate) = _uiState.update { it.copy(date = value) }
    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value) }
    fun updateDistance(value: String) = _uiState.update { it.copy(distanceKm = value) }
    fun updateDuration(value: String) = _uiState.update { it.copy(durationMin = value) }
    fun updateOptional(value: Boolean) = _uiState.update { it.copy(optional = value) }
    fun updateActualDistance(value: String) = _uiState.update { it.copy(actualDistanceKm = value) }
    fun updateActualDuration(value: String) = _uiState.update { it.copy(actualDurationMin = value) }
    fun updateCompleted(value: Boolean) = _uiState.update {
        it.copy(
            isCompleted = value,
            isSkipped = if (value) false else it.isSkipped,
            effortRating = if (value) it.effortRating else null,
        )
    }

    /** The three states are mutually exclusive: outstanding, done, or acknowledged as not done. */
    fun updateSkipped(value: Boolean) = _uiState.update {
        it.copy(
            isSkipped = value,
            isCompleted = if (value) false else it.isCompleted,
            effortRating = if (value) null else it.effortRating,
        )
    }
    fun updateEffortRating(value: Int) = _uiState.update { it.copy(effortRating = value) }

    fun updateWeek(week: UiWeek) = _uiState.update { it.copy(weekNumber = week.weekNumber, phase = week.phase) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val distance = state.distanceKm.toDoubleOrNull()
            val duration = state.durationMin.toIntOrNull()
            val actualDistance = state.actualDistanceKm.toDoubleOrNull()
            val actualDuration = state.actualDurationMin.toIntOrNull()
            val completedAt = if (state.isCompleted) LocalDate.now() else null

            if (state.isNew) {
                repository.addCustomSession(
                    SessionEntity(
                        date = state.date,
                        type = state.type,
                        title = state.title.ifBlank { state.type.name },
                        phase = state.phase,
                        weekNumber = state.weekNumber,
                        targetDistanceKm = distance,
                        targetDurationMin = duration,
                        optional = state.optional,
                        notes = state.notes,
                        isCompleted = state.isCompleted,
                        isSkipped = state.isSkipped,
                        actualDistanceKm = if (state.isCompleted) actualDistance ?: distance else null,
                        actualDurationMin = if (state.isCompleted) actualDuration ?: duration else null,
                        effortRating = if (state.isCompleted) state.effortRating else null,
                        completedAt = completedAt,
                    ),
                )
            } else {
                val existing = sessionId?.let { repository.getSession(it) } ?: return@launch
                repository.updateSession(
                    existing.copy(
                        title = state.title.ifBlank { state.type.name },
                        type = state.type,
                        date = state.date,
                        weekNumber = state.weekNumber,
                        phase = state.phase,
                        targetDistanceKm = distance,
                        targetDurationMin = duration,
                        optional = state.optional,
                        notes = state.notes,
                        isCompleted = state.isCompleted,
                        isSkipped = state.isSkipped,
                        actualDistanceKm = if (state.isCompleted) (actualDistance ?: distance) else null,
                        actualDurationMin = if (state.isCompleted) (actualDuration ?: duration) else null,
                        effortRating = if (state.isCompleted) state.effortRating else null,
                        completedAt = if (state.isCompleted) (existing.completedAt ?: LocalDate.now()) else null,
                    ),
                )
            }
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = sessionId ?: return
        viewModelScope.launch {
            val existing = repository.getSession(id) ?: return@launch
            repository.deleteSession(existing)
            _uiState.update { it.copy(deleted = true) }
        }
    }
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
