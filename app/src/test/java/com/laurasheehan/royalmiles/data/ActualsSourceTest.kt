package com.laurasheehan.royalmiles.data

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.ui.components.sessionSubtitle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActualsSourceTest {

    private val before = LocalDate.of(2026, 9, 20)
    private val after = LocalDate.of(2026, 9, 29)

    private fun session(
        completedAt: LocalDate? = after,
        actualKm: Double? = null,
        actualMin: Int? = null,
        sourceApp: String? = null,
        activityId: String? = null,
    ) = SessionEntity(
        eventId = "e",
        date = completedAt ?: after,
        type = SessionType.EASY_RUN,
        title = "Easy run",
        phase = TrainingPhase.BASE,
        weekNumber = 1,
        targetDistanceKm = 8.0,
        targetDurationMin = 50,
        isCompleted = completedAt != null,
        completedAt = completedAt,
        actualDistanceKm = actualKm,
        actualDurationMin = actualMin,
        sourceApp = sourceApp,
        sourceActivityId = activityId,
    )

    @Test
    fun `done with nothing recorded has no actuals and credits no distance`() {
        val done = session()
        assertEquals(ActualsSource.NONE, done.actualsSource)
        assertNull(done.knownDistanceKm)
    }

    @Test
    fun `figures from before the fix with no Garmin link are uncertain and not credited`() {
        val legacy = session(completedAt = before, actualKm = 8.0, actualMin = 50)
        assertEquals(ActualsSource.UNCERTAIN, legacy.actualsSource)
        assertNull(legacy.knownDistanceKm)
        assertNull(legacy.knownDurationMin)
    }

    @Test
    fun `Garmin-linked figures are recorded, unless an old link simply kept the plan`() {
        assertEquals(
            ActualsSource.RECORDED,
            session(completedAt = before, actualKm = 7.62, actualMin = 47, sourceApp = "com.garmin.android.apps.connectmobile", activityId = "123").actualsSource,
        )
        assertEquals(
            ActualsSource.UNCERTAIN,
            session(completedAt = before, actualKm = 8.0, actualMin = 50, activityId = "123").actualsSource,
        )
        // After the fix a link always writes Garmin's own figures, so even plan-equal ones are recorded.
        assertEquals(ActualsSource.RECORDED, session(actualKm = 8.0, actualMin = 50, activityId = "123").actualsSource)
    }

    @Test
    fun `figures she typed in are hers, whenever the session was done`() {
        val typed = session(completedAt = before, actualKm = 8.0, actualMin = 50, sourceApp = SessionEntity.MANUAL_SOURCE)
        assertEquals(ActualsSource.YOU, typed.actualsSource)
        assertEquals(8.0, typed.knownDistanceKm)
    }

    @Test
    fun `a card never shows planned figures as if they were done`() {
        val done = sessionSubtitle(session())
        assertTrue("planned 8km" in done, done)
        val legacy = sessionSubtitle(session(completedAt = before, actualKm = 8.0, actualMin = 50))
        assertTrue("source uncertain" in legacy, legacy)
        val recorded = sessionSubtitle(session(actualKm = 7.6, actualMin = 47, activityId = "1"))
        assertTrue("7.6km" in recorded && "planned" !in recorded, recorded)
        val upcoming = sessionSubtitle(session(completedAt = null))
        assertFalse("planned" in upcoming, upcoming)
    }
}
