package com.arabicflashcards.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "flashcard_progress")

class ProgressStore(private val context: Context) {

    private val masteredKey = stringSetPreferencesKey("mastered_ids")

    val masteredIds: Flow<Set<Int>> = context.dataStore.data.map { prefs ->
        prefs[masteredKey]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    suspend fun setMastered(id: Int, mastered: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[masteredKey]?.toMutableSet() ?: mutableSetOf()
            if (mastered) current.add(id.toString()) else current.remove(id.toString())
            prefs[masteredKey] = current
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs[masteredKey] = emptySet()
        }
    }
}
