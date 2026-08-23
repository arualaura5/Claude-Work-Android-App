package com.laurasheehan.royalmiles.ui.components

/** Shown when a session is marked complete — the point is to notice the act of showing up, not the score. */
object Affirmations {
    private val messages = listOf(
        "Logged. That's rebuilding.",
        "Small session, real progress.",
        "You showed up. That's the whole game.",
        "This is what consistency looks like.",
        "One more brick in the foundation.",
        "Future you says thank you.",
        "Nobody sees this one but you. It still counts.",
    )

    fun random(): String = messages.random()
}
