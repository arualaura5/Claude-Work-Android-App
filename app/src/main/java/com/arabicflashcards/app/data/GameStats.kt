package com.arabicflashcards.app.data

data class GameStats(
    val totalPoints: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastStudyEpochDay: Long? = null
) {
    val level: Int get() = totalPoints / 100 + 1
    val pointsIntoLevel: Int get() = totalPoints % 100
}
