package com.laurasheehan.royalmiles.core.progress

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class EffortTrendTest {

    private val today = LocalDate.of(2026, 9, 20)

    /** Ratings ending today, one per day, oldest first. */
    private fun readings(vararg ratings: Int): List<EffortReading> =
        ratings.mapIndexed { index, rating ->
            EffortReading(today.minusDays((ratings.size - 1 - index).toLong()), rating)
        }

    @Test
    fun `says nothing on too little data`() {
        assertEquals(EffortSignal.NONE, EffortTrends.assess(emptyList(), today))
        assertEquals(EffortSignal.NONE, EffortTrends.assess(readings(1, 1), today))
    }

    @Test
    fun `three rough sessions in a row asks to dial back`() {
        assertEquals(EffortSignal.DIAL_BACK, EffortTrends.assess(readings(4, 2, 2, 1), today))
    }

    @Test
    fun `one rough session among good ones is not a trend`() {
        assertEquals(EffortSignal.NONE, EffortTrends.assess(readings(4, 1, 4, 5), today))
    }

    @Test
    fun `a moderate dip is a watch, not a dial back`() {
        assertEquals(EffortSignal.WATCH, EffortTrends.assess(readings(4, 3, 2, 2, 3), today))
    }

    @Test
    fun `a clear drop against the previous three is a watch`() {
        assertEquals(EffortSignal.WATCH, EffortTrends.assess(readings(5, 5, 5, 3, 3, 3), today))
    }

    @Test
    fun `strong sessions say nothing`() {
        assertEquals(EffortSignal.NONE, EffortTrends.assess(readings(4, 5, 4, 5), today))
    }

    @Test
    fun `old rough sessions do not trigger anything`() {
        val stale = listOf(1, 1, 1).mapIndexed { index, rating ->
            EffortReading(today.minusDays(60L + index), rating)
        }
        assertEquals(EffortSignal.NONE, EffortTrends.assess(stale, today))
    }

    @Test
    fun `future-dated readings are ignored rather than counted`() {
        val future = listOf(1, 1, 1).mapIndexed { index, rating ->
            EffortReading(today.plusDays(index + 1L), rating)
        }
        assertEquals(EffortSignal.NONE, EffortTrends.assess(future, today))
    }
}
