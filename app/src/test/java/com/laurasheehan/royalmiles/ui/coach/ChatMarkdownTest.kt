package com.laurasheehan.royalmiles.ui.coach

import com.laurasheehan.royalmiles.ui.coach.ChatMarkdown.Block
import com.laurasheehan.royalmiles.ui.coach.ChatMarkdown.Run
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMarkdownTest {

    private fun text(runs: List<Run>) = runs.joinToString("") { it.text }

    @Test
    fun `bold and italic are styled and their markers removed`() {
        val runs = ChatMarkdown.inline("**Yes, modestly.** Keep it *easy* today.")
        assertEquals(
            listOf(
                Run("Yes, modestly.", bold = true),
                Run(" Keep it "),
                Run("easy", italic = true),
                Run(" today."),
            ),
            runs,
        )
    }

    @Test
    fun `a lone asterisk or underscore is left alone`() {
        assertEquals("5 * 3 sets and a heart_rate field", text(ChatMarkdown.inline("5 * 3 sets and a heart_rate field")))
        assertEquals("**not closed", text(ChatMarkdown.inline("**not closed")))
    }

    @Test
    fun `citations become numbered runs, including grouped ones`() {
        val runs = ChatMarkdown.inline("Economy improves [1][2] and more [3, 4].")
        assertEquals(listOf(1, 2, 3, 4), runs.mapNotNull { it.citation })
        assertEquals("Economy improves [1][2] and more [3][4].", text(runs))
    }

    @Test
    fun `links keep their text and only web addresses become links`() {
        val runs = ChatMarkdown.inline("See [the review](https://bjsm.bmj.com/x) or [this](file:///etc).")
        assertEquals("https://bjsm.bmj.com/x", runs.first { it.text == "the review" }.url)
        assertEquals("See the review or [this](file:///etc).", text(runs))
    }

    @Test
    fun `code and escapes`() {
        val runs = ChatMarkdown.inline("Use `Zone 2` not \\*this\\*")
        assertEquals(Run("Zone 2", code = true), runs[1])
        assertEquals("Use Zone 2 not *this*", text(runs))
    }

    @Test
    fun `blocks are split into paragraphs, headings and lists`() {
        val blocks = ChatMarkdown.parse(
            "**Yes.** Reviews find:\n\n- Twice a week [1]\n  - nested\n* Kept apart\n\n### In practice\n1. Lift heavy\n2) Rest 48 h\n\n---\nLine one\nline two",
        )
        assertEquals(
            listOf("Paragraph", "Bullet", "Bullet", "Bullet", "Heading", "Numbered", "Numbered", "Paragraph"),
            blocks.map { it::class.simpleName },
        )
        assertEquals(1, (blocks[2] as Block.Bullet).depth)
        assertEquals("2", (blocks[6] as Block.Numbered).number)
        assertEquals("In practice", text(blocks[4].runs))
        assertEquals("Line one\nline two", text(blocks.last().runs))
    }

    @Test
    fun `plain text comes through unchanged`() {
        val plain = "Heavy legs after 16 km is normal. Resting HR is up 2 bpm."
        assertEquals(listOf(Block.Paragraph(listOf(Run(plain)))), ChatMarkdown.parse(plain))
    }
}
