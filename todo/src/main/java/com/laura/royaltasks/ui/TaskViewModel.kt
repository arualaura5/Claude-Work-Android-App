package com.laura.royaltasks.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laura.royaltasks.audio.SoundPlayer
import com.laura.royaltasks.data.Priority
import com.laura.royaltasks.data.Progress
import com.laura.royaltasks.data.Task
import com.laura.royaltasks.data.TaskRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class UiState(
    val open: List<Task> = emptyList(),
    val doneToday: List<Task> = emptyList(),
    val progress: Progress = Progress(),
    val today: Long = LocalDate.now().toEpochDay(),
    val soundEnabled: Boolean = true,
    val loaded: Boolean = false
)

sealed interface Celebration {
    data object CrownEarned : Celebration
    data class LevelUp(val level: Int) : Celebration
}

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TaskRepository(app)
    private val sounds = SoundPlayer(app)
    private val today = MutableStateFlow(LocalDate.now().toEpochDay())

    private val _events = MutableSharedFlow<Celebration>(extraBufferCapacity = 8)
    val events: SharedFlow<Celebration> = _events.asSharedFlow()

    val state: StateFlow<UiState> =
        combine(repo.tasks, repo.progress, repo.soundEnabled, today) { tasks, progress, sound, day ->
            UiState(
                open = tasks.filterNot { it.isDone }
                    .sortedWith(compareByDescending<Task> { it.priority.ordinal }.thenBy { it.createdAt }),
                doneToday = tasks.filter { it.completedEpochDay == day }
                    .sortedByDescending { it.completedAt },
                progress = progress,
                today = day,
                soundEnabled = sound,
                loaded = true
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch { repo.soundEnabled.collect { sounds.enabled = it } }
    }

    /** Called on resume, so "today" rolls over if the app sat open past midnight. */
    fun refreshDay() {
        today.value = LocalDate.now().toEpochDay()
    }

    fun add(title: String, priority: Priority) {
        viewModelScope.launch { repo.add(title, priority) }
    }

    fun update(id: String, title: String, priority: Priority) {
        viewModelScope.launch { repo.update(id, title, priority) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    /** Fresh id so the list treats it as a new row rather than the swiped-away one. */
    fun restore(task: Task) {
        viewModelScope.launch { repo.restore(task.copy(id = UUID.randomUUID().toString())) }
    }

    fun complete(id: String) {
        viewModelScope.launch {
            refreshDay()
            val result = repo.complete(id, today.value) ?: return@launch
            val levelledUp = result.levelAfter > result.levelBefore
            when {
                levelledUp -> sounds.playLevelUp()
                result.earnedCrown -> sounds.playCrown()
                else -> sounds.playComplete()
            }
            if (result.earnedCrown) _events.emit(Celebration.CrownEarned)
            if (levelledUp) _events.emit(Celebration.LevelUp(result.levelAfter))
        }
    }

    fun uncomplete(id: String) {
        viewModelScope.launch { repo.uncomplete(id) }
    }

    fun toggleSound() {
        viewModelScope.launch { repo.setSoundEnabled(!state.value.soundEnabled) }
    }

    override fun onCleared() {
        sounds.release()
    }
}
