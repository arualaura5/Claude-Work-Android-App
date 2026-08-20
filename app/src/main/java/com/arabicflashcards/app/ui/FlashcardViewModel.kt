package com.arabicflashcards.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arabicflashcards.app.data.AppRepository
import com.arabicflashcards.app.data.GameStats
import com.arabicflashcards.app.data.StudyDirection
import com.arabicflashcards.app.data.UserCard
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
    val selectedTagFilter: Set<String> = emptySet()
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
    val knownTags: Set<String>
)

class FlashcardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _currentIndex = MutableStateFlow(0)
    private val _isFlipped = MutableStateFlow(false)
    private val _celebrate = MutableStateFlow(false)
    private val _selectedTagFilter = MutableStateFlow<Set<String>>(emptySet())

    private val loadedData: Flow<LoadedData> = combine(
        repository.cards,
        repository.direction,
        repository.stats,
        repository.knownTags
    ) { cards, direction, stats, knownTags -> LoadedData(cards, direction, stats, knownTags) }

    val uiState: StateFlow<FlashcardUiState> = combine(
        loadedData,
        _currentIndex,
        _isFlipped,
        _celebrate,
        _selectedTagFilter
    ) { loaded, index, flipped, celebrate, tagFilter ->
        val studyDeck = if (tagFilter.isEmpty()) {
            loaded.cards
        } else {
            loaded.cards.filter { card -> card.tags.any { it in tagFilter } }
        }
        val clampedIndex = if (studyDeck.isEmpty()) 0 else index.coerceIn(0, studyDeck.size - 1)
        FlashcardUiState(
            cards = loaded.cards,
            studyDeck = studyDeck,
            currentIndex = clampedIndex,
            isFlipped = flipped,
            direction = loaded.direction,
            stats = loaded.stats,
            justScoredPoints = celebrate,
            allTags = loaded.knownTags,
            selectedTagFilter = tagFilter
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
        _selectedTagFilter.update { current ->
            if (tag in current) current - tag else current + tag
        }
    }

    fun clearTagFilter() {
        _isFlipped.value = false
        _currentIndex.value = 0
        _selectedTagFilter.value = emptySet()
    }

    fun gradeCurrent(knewIt: Boolean) {
        val card = uiState.value.currentCard ?: return
        viewModelScope.launch { repository.recordReview(card.id, knewIt) }
        if (knewIt) {
            _celebrate.value = true
        }
        next()
    }

    fun consumeCelebration() {
        _celebrate.value = false
    }
}
