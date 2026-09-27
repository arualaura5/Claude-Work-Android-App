package com.laurasheehan.royalmiles.data.garmin

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.SessionEntity
import org.json.JSONObject
import java.lang.reflect.Modifier
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionCodecTest {

    /** Every field set to something other than its default. */
    private val everything = SessionEntity(
        id = 42,
        eventId = "richmond-2026",
        date = LocalDate.of(2026, 9, 27),
        type = SessionType.LONG_RUN,
        title = "Long run",
        phase = TrainingPhase.PEAK,
        weekNumber = 9,
        targetDistanceKm = 16.0,
        targetDurationMin = 100,
        optional = true,
        notes = "Was: Easy run.",
        isCompleted = true,
        actualDistanceKm = 15.62,
        actualDurationMin = 98,
        completedAt = LocalDate.of(2026, 9, 28),
        isCustom = true,
        effortRating = 4,
        isSkipped = true,
        supersededByCoach = true,
        actualAvgHeartRate = 146,
        actualMaxHeartRate = 171,
        actualCalories = 980,
        actualElevationGainM = 64,
        sourceApp = "com.garmin.android.apps.connectmobile",
        sourceActivityId = "21001",
    )

    @Test
    fun `undo restores a session exactly, every field`() {
        assertEquals(everything, SessionCodec.decode(SessionCodec.encode(everything)))
        val sparse = SessionEntity(eventId = "e", date = LocalDate.of(2026, 1, 1), type = SessionType.REST, title = "Rest", phase = TrainingPhase.BASE, weekNumber = 1)
        assertEquals(sparse, SessionCodec.decode(SessionCodec.encode(sparse)))
    }

    @Test
    fun `a field added to sessions must be added to the codec too`() {
        val fields = SessionEntity::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertEquals(fields, JSONObject(SessionCodec.encode(everything)).keys().asSequence().toSet())
    }
}
