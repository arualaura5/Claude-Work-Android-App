package com.laurasheehan.royalmiles.core.sync

import com.laurasheehan.royalmiles.core.model.SessionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GarminActivityTypesTest {

    @Test
    fun `every kind of run logs as a run`() {
        listOf("running", "treadmill_running", "track_running", "Trail_Running").forEach {
            assertEquals(SessionType.EASY_RUN, GarminActivityTypes.sessionTypeFor(it))
        }
    }

    @Test
    fun `other training maps to its own session type`() {
        assertEquals(SessionType.CYCLE, GarminActivityTypes.sessionTypeFor("indoor_cycling"))
        assertEquals(SessionType.SWIM, GarminActivityTypes.sessionTypeFor("lap_swimming"))
        assertEquals(SessionType.YOGA, GarminActivityTypes.sessionTypeFor("yoga"))
        assertEquals(SessionType.STRENGTH, GarminActivityTypes.sessionTypeFor("strength_training"))
    }

    @Test
    fun `walks and unknown activities are not logged automatically`() {
        assertNull(GarminActivityTypes.sessionTypeFor("walking"))
        assertNull(GarminActivityTypes.sessionTypeFor("hiking"))
        assertNull(GarminActivityTypes.sessionTypeFor(null))
    }
}
