package com.laurasheehan.royalmiles.data.coach

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoachSuggestionDecisionStoreTest {

    @Test
    fun `dismissal persists by suggestion date`() {
        val prefs = FakePreferenceAccess()
        val store = CoachSuggestionDecisionStore(prefs)

        assertFalse(store.isDismissed("2026-09-01"))

        store.markDismissed("2026-09-01")

        val reloaded = CoachSuggestionDecisionStore(prefs)
        assertTrue(reloaded.isDismissed("2026-09-01"))
        assertFalse(reloaded.isDismissed("2026-09-02"))
    }
}

private class FakePreferenceAccess : CoachSuggestionDecisionStore.PreferenceAccess {
    private val sets = mutableMapOf<String, Set<String>>()

    override fun getStringSet(key: String): Set<String>? = sets[key]

    override fun putStringSet(key: String, value: Set<String>) {
        sets[key] = value
    }
}
