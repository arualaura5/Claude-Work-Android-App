package com.arabicflashcards.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GameStatsTest {

    @Test
    fun `level increases every 100 points`() {
        assertEquals(1, GameStats(totalPoints = 0).level)
        assertEquals(1, GameStats(totalPoints = 99).level)
        assertEquals(2, GameStats(totalPoints = 100).level)
        assertEquals(3, GameStats(totalPoints = 250).level)
    }

    @Test
    fun `pointsIntoLevel wraps within the current level`() {
        assertEquals(0, GameStats(totalPoints = 100).pointsIntoLevel)
        assertEquals(50, GameStats(totalPoints = 250).pointsIntoLevel)
        assertEquals(99, GameStats(totalPoints = 199).pointsIntoLevel)
    }
}
