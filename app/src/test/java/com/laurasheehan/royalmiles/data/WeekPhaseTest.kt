package com.laurasheehan.royalmiles.data

import com.laurasheehan.royalmiles.RaceConfig
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekPhaseTest {

    private fun session(day: Int, event: String, phase: TrainingPhase) = SessionEntity(
        eventId = event, date = LocalDate.of(2026, 9, 27).plusDays(day.toLong()), type = SessionType.EASY_RUN, title = "Run",
        phase = phase, weekNumber = 1,
    )

    @Test
    fun `the week a new plan starts takes the new plan's phase, not the old plan's peak`() {
        val week = listOf(
            session(1, RaceConfig.ROYAL_PARKS_EVENT_ID, TrainingPhase.PEAK),
            session(4, RaceConfig.ROYAL_PARKS_EVENT_ID, TrainingPhase.PEAK),
            session(7, RaceConfig.ACTIVE_EVENT_ID, TrainingPhase.BUILD),
        )
        assertEquals(TrainingPhase.BUILD, weekPhase(week))
    }

    @Test
    fun `a week wholly from an earlier plan keeps its own phase`() {
        val week = listOf(session(1, RaceConfig.ROYAL_PARKS_EVENT_ID, TrainingPhase.PEAK))
        assertEquals(TrainingPhase.PEAK, weekPhase(week))
    }
}
