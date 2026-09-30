package com.laura.royaltasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    @Test
    fun `each level costs 100 XP more than the last`() {
        assertEquals(1, levelFor(0))
        assertEquals(1, levelFor(99))
        assertEquals(2, levelFor(100))
        assertEquals(2, levelFor(299))
        assertEquals(3, levelFor(300))
        assertEquals(4, levelFor(600))
        assertEquals(300, Progress(totalXp = 350).xpForThisLevel)
        assertEquals(50, Progress(totalXp = 350).xpIntoLevel)
    }

    @Test
    fun `titles cap at Legend`() {
        assertEquals("Page", titleFor(1))
        assertEquals("Knight", titleFor(3))
        assertEquals("Legend", titleFor(10))
        assertEquals("Legend", titleFor(42))
    }

    @Test
    fun `streak grows on consecutive days and resets after a gap`() {
        val day1 = Progress().afterCompleting(10, today = 100, doneToday = 1)
        assertEquals(1, day1.currentStreak)
        val sameDay = day1.afterCompleting(10, today = 100, doneToday = 2)
        assertEquals(1, sameDay.currentStreak)
        val day2 = sameDay.afterCompleting(10, today = 101, doneToday = 1)
        assertEquals(2, day2.currentStreak)
        val afterGap = day2.afterCompleting(10, today = 104, doneToday = 1)
        assertEquals(1, afterGap.currentStreak)
        assertEquals(2, afterGap.longestStreak)
    }

    @Test
    fun `streak shows as zero once a day is missed`() {
        val p = Progress(currentStreak = 5, lastDoneEpochDay = 100)
        assertEquals(5, p.streakAsOf(100))
        assertEquals(5, p.streakAsOf(101))
        assertEquals(0, p.streakAsOf(102))
    }

    @Test
    fun `crown is earned once per day at the daily goal`() {
        var p = Progress()
        p = p.afterCompleting(10, today = 100, doneToday = 1)
        p = p.afterCompleting(10, today = 100, doneToday = 2)
        assertEquals(0, p.crowns)
        p = p.afterCompleting(10, today = 100, doneToday = DAILY_GOAL)
        assertEquals(1, p.crowns)
        assertEquals(30 + CROWN_BONUS_XP, p.totalXp)
        assertTrue(p.crownEarnedOn(100))
        p = p.afterCompleting(10, today = 100, doneToday = DAILY_GOAL + 1)
        assertEquals(1, p.crowns)
        assertFalse(p.crownEarnedOn(101))
    }

    @Test
    fun `undo returns XP but never goes negative`() {
        assertEquals(15, Progress(totalXp = 25).afterUndoing(10).totalXp)
        assertEquals(0, Progress(totalXp = 5).afterUndoing(10).totalXp)
    }

    @Test
    fun `tasks round-trip through JSON`() {
        val tasks = listOf(
            Task(id = "a", title = "Book physio", priority = Priority.HIGH, createdAt = 1L),
            Task(
                id = "b", title = "Buy milk", priority = Priority.LOW, createdAt = 2L,
                completedAt = 3L, completedEpochDay = 100L, xpAwarded = 5
            )
        )
        assertEquals(tasks, tasks.toJsonString().toTasks())
    }

    @Test
    fun `older saved tasks with missing fields still load`() {
        val loaded = """[{"id":"x","title":"Old task"}]""".toTasks().single()
        assertEquals("Old task", loaded.title)
        assertEquals(Priority.NORMAL, loaded.priority)
        assertNull(loaded.completedAt)
        assertEquals(0, loaded.xpAwarded)
    }

    @Test
    fun `corrupt JSON loads as empty rather than crashing`() {
        assertEquals(emptyList<Task>(), "not json".toTasks())
    }
}
