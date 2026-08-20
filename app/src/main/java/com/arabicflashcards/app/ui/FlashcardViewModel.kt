package com.arabicflashcards.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arabicflashcards.app.data.Category
import com.arabicflashcards.app.data.Flashcard
import com.arabicflashcards.app.data.PhraseBank
import com.arabicflashcards.app.data.ProgressStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FlashcardUiState(
    val deck: List<Flashcard> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val selectedCategory: Category? = null,
    val masteredIds: Set<Int> = emptySet()
) {
    val currentCard: Flashcard?
        get() = deck.getOrNull(currentIndex)

    val total: Int get() = deck.size
    val positionLabel: String get() = if (deck.isEmpty()) "0 / 0" else "${currentIndex + 1} / $total"
    val masteredInDeck: Int get() = deck.count { it.id in masteredIds }
}

class FlashcardViewModel(application: Application) : AndroidViewModel(application) {

    private val progressStore = ProgressStore(application)

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    private val _currentIndex = MutableStateFlow(0)
    private val _isFlipped = MutableStateFlow(false)
    private val _deckOrder = MutableStateFlow(PhraseBank.phrases.map { it.id })

    val uiState: StateFlow<FlashcardUiState> = combine(
        _selectedCategory,
        _deckOrder,
        _currentIndex,
        _isFlipped,
        progressStore.masteredIds
    ) { category, order, index, flipped, mastered ->
        val byId = PhraseBank.phrases.associateBy { it.id }
        val deck = order.mapNotNull { byId[it] }
            .filter { category == null || it.category == category }
        val clampedIndex = if (deck.isEmpty()) 0 else index.coerceIn(0, deck.size - 1)
        FlashcardUiState(
            deck = deck,
            currentIndex = clampedIndex,
            isFlipped = flipped,
            selectedCategory = category,
            masteredIds = mastered
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
        val size = uiState.value.deck.size
        if (size == 0) return
        _isFlipped.value = false
        _currentIndex.update { (it + 1) % size }
    }

    fun previous() {
        val size = uiState.value.deck.size
        if (size == 0) return
        _isFlipped.value = false
        _currentIndex.update { (it - 1 + size) % size }
    }

    fun shuffle() {
        _isFlipped.value = false
        _currentIndex.value = 0
        _deckOrder.value = PhraseBank.phrases.map { it.id }.shuffled()
    }

    fun selectCategory(category: Category?) {
        _selectedCategory.value = category
        _currentIndex.value = 0
        _isFlipped.value = false
    }

    fun toggleMastered() {
        val card = uiState.value.currentCard ?: return
        val currentlyMastered = card.id in uiState.value.masteredIds
        viewModelScope.launch {
            progressStore.setMastered(card.id, !currentlyMastered)
        }
    }

    fun resetProgress() {
        viewModelScope.launch {
            progressStore.clearAll()
        }
    }
}
