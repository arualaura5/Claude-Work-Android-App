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
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val direction: StudyDirection = StudyDirection.EGYPTIAN_FIRST,
    val stats: GameStats = GameStats(),
    val justScoredPoints: Boolean = false
) {
    val currentCard: UserCard? get() = cards.getOrNull(currentIndex)
    val total: Int get() = cards.size
    val positionLabel: String get() = if (cards.isEmpty()) "0 / 0" else "${currentIndex + 1} / $total"
    val masteredCount: Int get() = cards.count { it.mastered }
}

private data class LoadedData(
    val cards: List<UserCard>,
    val direction: StudyDirection,
    val stats: GameStats
)

class FlashcardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _currentIndex = MutableStateFlow(0)
    private val _isFlipped = MutableStateFlow(false)
    private val _celebrate = MutableStateFlow(false)

    private val loadedData: Flow<LoadedData> = combine(
        repository.cards,
        repository.direction,
        repository.stats
    ) { cards, direction, stats -> LoadedData(cards, direction, stats) }

    val uiState: StateFlow<FlashcardUiState> = combine(
        loadedData,
        _currentIndex,
        _isFlipped,
        _celebrate
    ) { loaded, index, flipped, celebrate ->
        val clampedIndex = if (loaded.cards.isEmpty()) 0 else index.coerceIn(0, loaded.cards.size - 1)
        FlashcardUiState(
            cards = loaded.cards,
            currentIndex = clampedIndex,
            isFlipped = flipped,
            direction = loaded.direction,
            stats = loaded.stats,
            justScoredPoints = celebrate
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
        val size = uiState.value.cards.size
        if (size == 0) return
        _isFlipped.value = false
        _currentIndex.update { (it + 1) % size }
    }

    fun previous() {
        val size = uiState.value.cards.size
        if (size == 0) return
        _isFlipped.value = false
        _currentIndex.update { (it - 1 + size) % size }
    }

    fun setDirection(direction: StudyDirection) {
        _isFlipped.value = false
        viewModelScope.launch { repository.setDirection(direction) }
    }

    fun addCard(english: String, egyptian: String) {
        if (english.isBlank() || egyptian.isBlank()) return
        viewModelScope.launch { repository.addCard(english, egyptian) }
    }

    fun updateCard(id: String, english: String, egyptian: String) {
        if (english.isBlank() || egyptian.isBlank()) return
        viewModelScope.launch { repository.updateCard(id, english, egyptian) }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch { repository.deleteCard(id) }
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
