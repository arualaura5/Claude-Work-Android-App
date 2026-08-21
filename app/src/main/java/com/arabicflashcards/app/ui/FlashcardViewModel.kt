package com.arabicflashcards.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arabicflashcards.app.data.AppRepository
import com.arabicflashcards.app.data.GameStats
import com.arabicflashcards.app.data.Lesson
import com.arabicflashcards.app.data.NotebookBackup
import com.arabicflashcards.app.data.StudyDirection
import com.arabicflashcards.app.data.UserCard
import com.arabicflashcards.app.data.toJsonString
import com.arabicflashcards.app.data.toNotebookBackup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FlashcardUiState(
    val cards: List<UserCard> = emptyList(),
    val studyDeck: List<UserCard> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val direction: StudyDirection = StudyDirection.EGYPTIAN_FIRST,
    val stats: GameStats = GameStats(),
    val justScoredPoints: Boolean = false,
    val allTags: Set<String> = emptySet(),
    val selectedTagFilter: Set<String> = emptySet(),
    val lessons: List<Lesson> = emptyList(),
    val soundEnabled: Boolean = true,
    val selectedLessonFilter: String? = null
) {
    val currentCard: UserCard? get() = studyDeck.getOrNull(currentIndex)
    val total: Int get() = studyDeck.size
    val positionLabel: String get() = if (studyDeck.isEmpty()) "0 / 0" else "${currentIndex + 1} / $total"
    val masteredCount: Int get() = studyDeck.count { it.mastered }
}

private data class LoadedData(
    val cards: List<UserCard>,
    val direction: StudyDirection,
    val stats: GameStats,
    val knownTags: Set<String>,
    val lessons: List<Lesson>,
    val soundEnabled: Boolean = true
)

private data class PreFilterState(
    val loaded: LoadedData,
    val index: Int,
    val flipped: Boolean,
    val celebrate: Boolean,
    val tagFilter: Set<String>
)

class FlashcardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _currentIndex = MutableStateFlow(0)
    private val _isFlipped = MutableStateFlow(false)
    private val _celebrate = MutableStateFlow(false)
    private val _selectedTagFilter = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedLessonFilter = MutableStateFlow<String?>(null)
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val loadedData: Flow<LoadedData> = combine(
        repository.cards,
        repository.direction,
        repository.stats,
        repository.knownTags,
        repository.lessons
    ) { cards, direction, stats, knownTags, lessons -> LoadedData(cards, direction, stats, knownTags, lessons) }
        .combine(repository.soundEnabled) { loaded, soundEnabled -> loaded.copy(soundEnabled = soundEnabled) }

    val uiState: StateFlow<FlashcardUiState> = combine(
        loadedData,
        _currentIndex,
        _isFlipped,
        _celebrate,
        _selectedTagFilter
    ) { loaded, index, flipped, celebrate, tagFilter ->
        PreFilterState(loaded, index, flipped, celebrate, tagFilter)
    }.combine(_selectedLessonFilter) { pre, lessonFilterId ->
        val loaded = pre.loaded
        val lesson = lessonFilterId?.let { id -> loaded.lessons.find { it.id == id } }
        val studyDeck = when {
            lesson != null -> lesson.cardIds.mapNotNull { id -> loaded.cards.find { it.id == id } }
            pre.tagFilter.isEmpty() -> loaded.cards
            else -> loaded.cards.filter { card -> card.tags.any { it in pre.tagFilter } }
        }
        val clampedIndex = if (studyDeck.isEmpty()) 0 else pre.index.coerceIn(0, studyDeck.size - 1)
        FlashcardUiState(
            cards = loaded.cards,
            studyDeck = studyDeck,
            currentIndex = clampedIndex,
            isFlipped = pre.flipped,
            direction = loaded.direction,
            stats = loaded.stats,
            justScoredPoints = pre.celebrate,
            allTags = loaded.knownTags,
            selectedTagFilter = pre.tagFilter,
            lessons = loaded.lessons,
            soundEnabled = loaded.soundEnabled,
            selectedLessonFilter = lesson?.id
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FlashcardUiState()
    )

    fun flip() {
        _isFlipped.update { !it }
    }

    fun next() {
        val size = uiState.value.studyDeck.size
        if (size == 0) return
        _isFlipped.value = false
        _currentIndex.update { (it + 1) % size }
    }

    fun previous() {
        val size = uiState.value.studyDeck.size
        if (size == 0) return
        _isFlipped.value = false
        _currentIndex.update { (it - 1 + size) % size }
    }

    fun setDirection(direction: StudyDirection) {
        _isFlipped.value = false
        viewModelScope.launch { repository.setDirection(direction) }
    }

    fun addCard(english: String, egyptian: String, tags: List<String> = emptyList()) {
        if (english.isBlank() || egyptian.isBlank()) return
        viewModelScope.launch { repository.addCard(english, egyptian, tags) }
    }

    fun updateCard(id: String, english: String, egyptian: String, tags: List<String> = emptyList()) {
        if (english.isBlank() || egyptian.isBlank()) return
        viewModelScope.launch { repository.updateCard(id, english, egyptian, tags) }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch { repository.deleteCard(id) }
    }

    fun addTag(tag: String) {
        viewModelScope.launch { repository.addTag(tag) }
    }

    fun deleteTag(tag: String) {
        viewModelScope.launch { repository.deleteTag(tag) }
        _selectedTagFilter.update { it - tag }
    }

    fun toggleTagFilter(tag: String) {
        _isFlipped.value = false
        _currentIndex.value = 0
        _selectedLessonFilter.value = null
        _selectedTagFilter.update { current ->
            if (tag in current) current - tag else current + tag
        }
    }

    fun clearTagFilter() {
        _isFlipped.value = false
        _currentIndex.value = 0
        _selectedTagFilter.value = emptySet()
        _selectedLessonFilter.value = null
    }

    fun selectLessonFilter(lessonId: String?) {
        _isFlipped.value = false
        _currentIndex.value = 0
        _selectedTagFilter.value = emptySet()
        _selectedLessonFilter.value = lessonId
    }

    fun gradeCard(cardId: String, knewIt: Boolean) {
        viewModelScope.launch { repository.recordReview(cardId, knewIt) }
        if (knewIt) {
            _celebrate.value = true
        }
    }

    fun gradeCurrent(knewIt: Boolean) {
        val card = uiState.value.currentCard ?: return
        gradeCard(card.id, knewIt)
        next()
    }

    fun consumeCelebration() {
        _celebrate.value = false
    }

    fun createLesson(name: String) {
        viewModelScope.launch { repository.createLesson(name) }
    }

    fun renameLesson(id: String, name: String) {
        viewModelScope.launch { repository.renameLesson(id, name) }
    }

    fun deleteLesson(id: String) {
        viewModelScope.launch { repository.deleteLesson(id) }
    }

    fun setLessonCards(id: String, cardIds: List<String>) {
        viewModelScope.launch { repository.setLessonCards(id, cardIds) }
    }

    fun reorderLessons(orderedIds: List<String>) {
        viewModelScope.launch { repository.reorderLessons(orderedIds) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSoundEnabled(enabled) }
    }

    fun exportBackupJson(): String {
        val state = uiState.value
        return NotebookBackup(state.cards, state.allTags, state.lessons).toJsonString()
    }

    fun importBackup(json: String) {
        viewModelScope.launch {
            val backup = runCatching { json.toNotebookBackup() }.getOrNull()
            if (backup == null) {
                _statusMessage.value = "Import failed — that doesn't look like a valid backup file."
                return@launch
            }
            val added = repository.importBackup(backup.cards, backup.tags, backup.lessons)
            _statusMessage.value = when {
                added == 0 -> "No new cards to import — everything in that file is already in your notebook."
                added == 1 -> "Imported 1 new card."
                else -> "Imported $added new cards."
            }
        }
    }

    fun notifyBackupSaved() {
        _statusMessage.value = "Backup saved."
    }

    fun notifyBackupFailed() {
        _statusMessage.value = "Couldn't save the backup file."
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }
}
