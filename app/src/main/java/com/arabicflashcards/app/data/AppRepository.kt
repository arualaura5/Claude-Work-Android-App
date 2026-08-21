package com.arabicflashcards.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "flashcard_notebook")

class AppRepository(private val context: Context) {

    private val cardsKey = stringPreferencesKey("cards_json")
    private val directionKey = stringPreferencesKey("study_direction")
    private val pointsKey = intPreferencesKey("total_points")
    private val currentStreakKey = intPreferencesKey("current_streak")
    private val longestStreakKey = intPreferencesKey("longest_streak")
    private val lastStudyDayKey = longPreferencesKey("last_study_epoch_day")
    private val knownTagsKey = stringSetPreferencesKey("known_tags")
    private val lessonsKey = stringPreferencesKey("lessons_json")
    private val soundEnabledKey = booleanPreferencesKey("sound_enabled")

    val cards: Flow<List<UserCard>> = context.dataStore.data.map { prefs ->
        prefs[cardsKey]?.toUserCards() ?: emptyList()
    }

    val soundEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[soundEnabledKey] ?: true
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[soundEnabledKey] = enabled
        }
    }

    val lessons: Flow<List<Lesson>> = context.dataStore.data.map { prefs ->
        prefs[lessonsKey]?.toLessons() ?: emptyList()
    }

    val knownTags: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[knownTagsKey] ?: emptySet()
    }

    val direction: Flow<StudyDirection> = context.dataStore.data.map { prefs ->
        prefs[directionKey]?.let { runCatching { StudyDirection.valueOf(it) }.getOrNull() }
            ?: StudyDirection.EGYPTIAN_FIRST
    }

    val stats: Flow<GameStats> = context.dataStore.data.map { prefs ->
        GameStats(
            totalPoints = prefs[pointsKey] ?: 0,
            currentStreak = prefs[currentStreakKey] ?: 0,
            longestStreak = prefs[longestStreakKey] ?: 0,
            lastStudyEpochDay = prefs[lastStudyDayKey]
        )
    }

    suspend fun addCard(english: String, egyptian: String, tags: List<String> = emptyList()) {
        val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        context.dataStore.edit { prefs ->
            val current = prefs[cardsKey]?.toUserCards() ?: emptyList()
            val updated = current + UserCard(
                english = english.trim(),
                egyptian = egyptian.trim(),
                tags = cleanTags
            )
            prefs[cardsKey] = updated.toJsonString()
            if (cleanTags.isNotEmpty()) {
                prefs[knownTagsKey] = (prefs[knownTagsKey] ?: emptySet()) + cleanTags
            }
        }
    }

    suspend fun updateCard(id: String, english: String, egyptian: String, tags: List<String> = emptyList()) {
        val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        context.dataStore.edit { prefs ->
            val current = prefs[cardsKey]?.toUserCards() ?: emptyList()
            val updated = current.map {
                if (it.id == id) it.copy(english = english.trim(), egyptian = egyptian.trim(), tags = cleanTags) else it
            }
            prefs[cardsKey] = updated.toJsonString()
            if (cleanTags.isNotEmpty()) {
                prefs[knownTagsKey] = (prefs[knownTagsKey] ?: emptySet()) + cleanTags
            }
        }
    }

    suspend fun deleteCard(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[cardsKey]?.toUserCards() ?: emptyList()
            prefs[cardsKey] = current.filterNot { it.id == id }.toJsonString()
            val currentLessons = prefs[lessonsKey]?.toLessons() ?: emptyList()
            if (currentLessons.isNotEmpty()) {
                prefs[lessonsKey] = currentLessons.map { it.copy(cardIds = it.cardIds - id) }.toJsonString()
            }
        }
    }

    suspend fun createLesson(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[lessonsKey]?.toLessons() ?: emptyList()
            prefs[lessonsKey] = (current + Lesson(name = clean)).toJsonString()
        }
    }

    suspend fun renameLesson(id: String, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[lessonsKey]?.toLessons() ?: emptyList()
            prefs[lessonsKey] = current.map { if (it.id == id) it.copy(name = clean) else it }.toJsonString()
        }
    }

    suspend fun deleteLesson(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[lessonsKey]?.toLessons() ?: emptyList()
            prefs[lessonsKey] = current.filterNot { it.id == id }.toJsonString()
        }
    }

    suspend fun setLessonCards(id: String, cardIds: List<String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[lessonsKey]?.toLessons() ?: emptyList()
            prefs[lessonsKey] = current.map { if (it.id == id) it.copy(cardIds = cardIds) else it }.toJsonString()
        }
    }

    suspend fun reorderLessons(orderedIds: List<String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[lessonsKey]?.toLessons() ?: emptyList()
            val byId = current.associateBy { it.id }
            prefs[lessonsKey] = orderedIds.mapNotNull { byId[it] }.toJsonString()
        }
    }

    suspend fun addTag(tag: String) {
        val clean = tag.trim()
        if (clean.isBlank()) return
        context.dataStore.edit { prefs ->
            prefs[knownTagsKey] = (prefs[knownTagsKey] ?: emptySet()) + clean
        }
    }

    suspend fun deleteTag(tag: String) {
        context.dataStore.edit { prefs ->
            prefs[knownTagsKey] = (prefs[knownTagsKey] ?: emptySet()) - tag
            val current = prefs[cardsKey]?.toUserCards() ?: emptyList()
            val updated = current.map { it.copy(tags = it.tags - tag) }
            prefs[cardsKey] = updated.toJsonString()
        }
    }

    /**
     * Merge-only import: never overwrites or removes an existing card.
     * Returns how many cards were actually added (imported cards whose id
     * already exists in the notebook are skipped, not overwritten).
     */
    suspend fun importBackup(cards: List<UserCard>, tags: Set<String>, lessons: List<Lesson> = emptyList()): Int {
        var addedCount = 0
        context.dataStore.edit { prefs ->
            val current = prefs[cardsKey]?.toUserCards() ?: emptyList()
            val existingIds = current.map { it.id }.toSet()
            val newCards = cards.filterNot { it.id in existingIds }
            addedCount = newCards.size
            prefs[cardsKey] = (current + newCards).toJsonString()
            val importedTags = tags + cards.flatMap { it.tags }
            if (importedTags.isNotEmpty()) {
                prefs[knownTagsKey] = (prefs[knownTagsKey] ?: emptySet()) + importedTags
            }
            if (lessons.isNotEmpty()) {
                val currentLessons = prefs[lessonsKey]?.toLessons() ?: emptyList()
                val existingLessonIds = currentLessons.map { it.id }.toSet()
                val newLessons = lessons.filterNot { it.id in existingLessonIds }
                prefs[lessonsKey] = (currentLessons + newLessons).toJsonString()
            }
        }
        return addedCount
    }

    suspend fun setDirection(direction: StudyDirection) {
        context.dataStore.edit { prefs ->
            prefs[directionKey] = direction.name
        }
    }

    suspend fun recordReview(id: String, knewIt: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[cardsKey]?.toUserCards() ?: emptyList()
            val updated = current.map { card ->
                if (card.id == id) {
                    val newTimesCorrect = card.timesCorrect + if (knewIt) 1 else 0
                    card.copy(
                        timesReviewed = card.timesReviewed + 1,
                        timesCorrect = newTimesCorrect,
                        mastered = if (knewIt) newTimesCorrect >= 3 else card.mastered
                    )
                } else card
            }
            prefs[cardsKey] = updated.toJsonString()

            if (knewIt) {
                val today = LocalDate.now().toEpochDay()
                val lastDay = prefs[lastStudyDayKey]
                val newStreak = when (lastDay) {
                    today -> prefs[currentStreakKey] ?: 1
                    today - 1 -> (prefs[currentStreakKey] ?: 0) + 1
                    else -> 1
                }
                prefs[currentStreakKey] = newStreak
                prefs[longestStreakKey] = maxOf(prefs[longestStreakKey] ?: 0, newStreak)
                prefs[lastStudyDayKey] = today
                prefs[pointsKey] = (prefs[pointsKey] ?: 0) + 10
            }
        }
    }
}
