package com.laurasheehan.royalmiles.ui.dashboard

import com.laurasheehan.royalmiles.RaceConfig
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import com.laurasheehan.royalmiles.data.coach.CoachSuggestionDecisions
import com.laurasheehan.royalmiles.notifications.CoachAlerts
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardCoachSuggestionTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val sunday = LocalDate.of(2026, 10, 18)

    private fun session(date: LocalDate, done: Boolean = false) = SessionEntity(
        eventId = RaceConfig.RICHMOND_EVENT_ID,
        date = date,
        type = SessionType.LONG_RUN,
        title = "Long run",
        phase = TrainingPhase.PEAK,
        weekNumber = 3,
        targetDistanceKm = 15.0,
        isCompleted = done,
    )

    private fun suggestion(date: LocalDate) = CoachPayload.suggestionFrom(
        org.json.JSONObject(
            """{"action":"replace","date":"$date","headline":"Make Sunday 13 km, not 15.","reason":"Resting HR has been 5 above normal for two days.",
               "replace_with":{"type":"LONG_RUN","title":"Long run","target_distance_km":13}}""",
        ),
    )!!

    private val noDecisions = object : CoachSuggestionDecisions {
        val dismissed = mutableSetOf<String>()
        override fun isDismissed(date: String) = date in dismissed
        override fun isAccepted(date: String) = false
        override fun markDismissed(date: String) { dismissed += date }
        override fun markAccepted(date: String) = Unit
    }

    @Test
    fun `no suggestion means no dashboard suggestion`() {
        assertNull(visibleCoachSuggestion(null, listOf(session(today)), today, null))
    }

    @Test
    fun `a change for Sunday shows from Thursday, so she can act before the run`() {
        val visible = visibleCoachSuggestion(suggestion(sunday), listOf(session(sunday)), today, noDecisions)
        assertNotNull(visible)
        assertEquals(sunday, visible.session.date)
    }

    @Test
    fun `past days, days more than a week ahead, and done or dismissed sessions don't show`() {
        assertNull(visibleCoachSuggestion(suggestion(today.minusDays(1)), listOf(session(today.minusDays(1))), today, noDecisions))
        assertNull(visibleCoachSuggestion(suggestion(today.plusDays(8)), listOf(session(today.plusDays(8))), today, noDecisions))
        assertNull(visibleCoachSuggestion(suggestion(sunday), listOf(session(sunday, done = true)), today, noDecisions))
        noDecisions.markDismissed(sunday.toString())
        assertNull(visibleCoachSuggestion(suggestion(sunday), listOf(session(sunday)), today, noDecisions))
    }

    @Test
    fun `the card and the notification say which day the change is for`() {
        assertEquals("YOUR COACH", suggestionDayLabel(today.toString(), today))
        assertEquals("YOUR COACH · FOR TOMORROW", suggestionDayLabel(today.plusDays(1).toString(), today))
        assertEquals("YOUR COACH · FOR SUNDAY 18 OCT", suggestionDayLabel(sunday.toString(), today))

        val s = suggestion(sunday)
        assertEquals("Your coach suggests a change for Sunday 18 Oct", CoachAlerts.title(s, today))
        assertEquals("Your coach suggests a change for tomorrow", CoachAlerts.title(s, sunday.minusDays(1)))
        assertTrue(CoachAlerts.body(s).startsWith("Make Sunday 13 km, not 15. Resting HR has been 5 above normal"))
        assertEquals("2026-10-18|Make Sunday 13 km, not 15.", CoachAlerts.key(s), "one notification per suggestion")
    }
}
