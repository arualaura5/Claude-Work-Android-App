package com.laurasheehan.royalmiles.data.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CoachRemoteTest {

    @Test
    fun `bare worker address gets the coach path`() {
        assertEquals(
            "https://coach.example.workers.dev/coach.json",
            CoachRemote.normaliseAddress("  https://coach.example.workers.dev/ "),
        )
    }

    @Test
    fun `full coach address is kept as is`() {
        assertEquals(
            "https://coach.example.workers.dev/coach.json",
            CoachRemote.normaliseAddress("https://coach.example.workers.dev/coach.json"),
        )
    }

    @Test
    fun `plain http is refused so the key never travels unencrypted`() {
        assertFailsWith<IllegalArgumentException> {
            CoachRemote.normaliseAddress("http://coach.example.workers.dev")
        }
    }

    @Test
    fun `rejected key and missing coaching read differently`() {
        assertTrue(CoachRemote.failureMessage(401).contains("key"))
        assertTrue(CoachRemote.failureMessage(404).contains("No live coaching"))
        assertTrue(CoachRemote.failureMessage(503).contains("503"))
    }
}
