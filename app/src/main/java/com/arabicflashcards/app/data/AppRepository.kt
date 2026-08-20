package com.arabicflashcards.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    val cards: Flow<List<UserCard>> = context.dataStore.data.map { prefs ->
        prefs[cardsKey]?.toUserCards() ?: emptyList()
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

    suspend fun addCard(english: String, egyptian: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[cardsKey]?.toUserCards() ?: emptyList()
            val updated = current + UserCard(english = english.trim(), egyptian = egyptian.trim())
            prefs[cardsKey] = updated.toJsonString()
        }
    }

    suspend fun updateCard(id: String, english: String, egyptian: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[cardsKey]?.toUserCards() ?: emptyList()
            val updated = current.map {
                if (it.id == id) it.copy(english = english.trim(), egyptian = egyptian.trim()) else it
            }
            prefs[cardsKey] = updated.toJsonString()
        }
    }

    suspend fun deleteCard(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[cardsKey]?.toUserCards() ?: emptyList()
            prefs[cardsKey] = current.filterNot { it.id == id }.toJsonString()
        }
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
