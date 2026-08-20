package com.arabicflashcards.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseBankTest {

    @Test
    fun `ids are unique`() {
        val ids = PhraseBank.phrases.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `no phrase has blank fields`() {
        PhraseBank.phrases.forEach { card ->
            assertFalse("arabic blank for id ${card.id}", card.arabic.isBlank())
            assertFalse("transliteration blank for id ${card.id}", card.transliteration.isBlank())
            assertFalse("english blank for id ${card.id}", card.english.isBlank())
        }
    }

    @Test
    fun `every category has at least one card`() {
        val categoriesUsed = PhraseBank.phrases.map { it.category }.toSet()
        assertTrue(categoriesUsed.containsAll(Category.entries))
    }
}
