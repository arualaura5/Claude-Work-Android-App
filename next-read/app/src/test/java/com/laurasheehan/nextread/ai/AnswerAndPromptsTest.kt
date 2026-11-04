package com.laurasheehan.nextread.ai

import com.laurasheehan.nextread.data.Notebook
import com.laurasheehan.nextread.data.RejectedBook
import com.laurasheehan.nextread.data.ShelfBook
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerAndPromptsTest {

    @Test
    fun readsACheckAnswerWithAGoodreadsLookup() {
        val result = JSONObject("""{"books":[
          {"asked":"Fahrenheit 451","title":"Fahrenheit 451","author":"Ray Bradbury","year":1953,"pages":249,
           "plot":"A fireman burns books.","verdict":"Maybe","fit":6,"why":"Short, with a point.","watchOut":"Lyrical prose.",
           "tags":["classic","dystopia","short","extra","more"],"onShelf":"none",
           "goodreads":{"rating":3.97,"count":3015714,"id":"13079982"}},
          {"asked":"photo, top shelf","title":"","author":"","verdict":"unsure","why":"A blue spine I can't read."},
          {"title":"Made Up Rating","author":"Someone","goodreads":{"rating":7.5}}
        ]}""")
        val answer = AnswerParser.parse(result)
        assertEquals(3, answer.books.size)
        val f = answer.books[0]
        assertEquals("maybe", f.verdict)
        assertEquals(6, f.fit)
        assertEquals(3.97, f.grRating!!, 0.0)
        assertEquals(3015714L, f.grCount)
        assertEquals("13079982", f.grId)
        assertEquals(4, f.tags.size)
        // A book whose title couldn't be read keeps what was asked, so the card can say so.
        assertEquals("photo, top shelf", answer.books[1].title)
        // Anything that isn't a real 0–5 rating is dropped, never shown.
        assertNull(answer.books[2].grRating)
    }

    private val notebook = Notebook(
        books = listOf(
            ShelfBook("1232", "The Shadow of the Wind", "Carlos Ruiz Zafón", "read", 0, 487, 2001, "", ""),
            ShelfBook("15852019", "I Remember You", "Yrsa Sigurdardottir", "read", 5, 391, 2022, "", ""),
            ShelfBook("24800", "House of Leaves", "Mark Z. Danielewski", "to-read", 0, 709, 2000, "", ""),
        ),
        notes = mapOf("b1232" to "Long and dragged out. Didn't enjoy the flowery language."),
        taste = "I really like thrillers and suspense.",
        notForMe = listOf(RejectedBook("Audition", "Katie Kitamura", "Not my style")),
    )

    @Test
    fun promptsCarryHerWordsShelvesAndRejections() {
        val prompt = Prompts.discover(notebook, "something creepy", setOf("Twisty domestic thriller"), null)
        assertTrue(prompt.contains("I really like thrillers and suspense."))
        assertTrue(prompt.contains("Their own words: \"Long and dragged out."))
        assertTrue(prompt.contains("I Remember You — Yrsa Sigurdardottir [5★]"))
        assertTrue(prompt.contains("House of Leaves"))
        assertTrue(prompt.contains("Audition — Katie Kitamura (Not my style)"))
        assertTrue(prompt.contains("In their words: something creepy"))
        assertTrue(prompt.contains("Do not include ratings or star scores."))
    }

    @Test
    fun photoChecksAskClaudeToReadEveryTitle() {
        val withPhoto = Prompts.check(notebook, emptyList(), hasPhoto = true)
        assertTrue(withPhoto.contains("They have attached a photo"))
        assertFalse(withPhoto.contains("They also typed"))
        val typed = Prompts.check(notebook, listOf("Fahrenheit 451"), hasPhoto = false)
        assertTrue(typed.contains("- Fahrenheit 451"))
    }

    @Test
    fun theStyleSummaryLeavesOutTheOldSummary() {
        val n = notebook.copy(profile = "OLD SUMMARY")
        assertFalse(Prompts.profile(n).contains("OLD SUMMARY"))
        assertTrue(Prompts.discover(n, "", emptySet(), null).contains("OLD SUMMARY"))
    }
}
