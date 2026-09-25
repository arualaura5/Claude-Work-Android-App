package com.laurasheehan.royalmiles.ui.dashboard

import com.laurasheehan.royalmiles.RaceConfig
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.SessionEntity
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertNull

class DashboardCoachSuggestionTest {

    @Test
    fun `no suggestion means no dashboard suggestion`() {
        val session = SessionEntity(
            eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
            date = LocalDate.of(2026, 9, 1),
            type = SessionType.EASY_RUN,
            title = "Easy run",
            phase = TrainingPhase.BASE,
            weekNumber = 1,
        )

        val visible = visibleCoachSuggestion(
            suggestion = null,
            sessions = listOf(session),
            today = LocalDate.of(2026, 9, 1),
            suggestionDecisions = null,
        )

        assertNull(visible)
    }
}
