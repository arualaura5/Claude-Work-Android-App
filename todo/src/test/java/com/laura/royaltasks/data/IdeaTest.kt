package com.laura.royaltasks.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IdeaTest {

    @Test
    fun `ideas round-trip through JSON, line breaks included`() {
        val ideas = listOf(
            Idea(id = "a", text = "Run club for new mums.\nSaturday 8am?", createdAt = 1L),
            Idea(id = "b", text = "Hill sprint ladder", createdAt = 2L)
        )
        assertEquals(ideas, ideas.ideasToJson().toIdeas())
    }

    @Test
    fun `missing fields still load, blank ideas are dropped`() {
        val loaded = """[{"text":"Old idea"},{"text":"  "}]""".toIdeas()
        assertEquals(1, loaded.size)
        assertEquals("Old idea", loaded.single().text)
        assertEquals(0L, loaded.single().createdAt)
    }

    @Test
    fun `tidy keeps the user's punctuation and capitalises the start`() {
        assertEquals("Podcast idea: recovery myths.", tidyIdea("  podcast  idea: recovery myths. "))
        assertEquals("Line one\n\nLine two", tidyIdea("line one\n\n\n\nLine two"))
    }
}
