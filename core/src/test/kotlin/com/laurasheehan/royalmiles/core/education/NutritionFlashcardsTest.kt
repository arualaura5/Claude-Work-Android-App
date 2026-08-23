package com.laurasheehan.royalmiles.core.education

import kotlin.test.Test
import kotlin.test.assertTrue

class NutritionFlashcardsTest {

    @Test
    fun `has a meaningful number of cards`() {
        assertTrue(NutritionFlashcards.cards.size >= 10)
    }

    @Test
    fun `every card has a non-blank question and answer`() {
        NutritionFlashcards.cards.forEach { card ->
            assertTrue(card.question.isNotBlank())
            assertTrue(card.answer.isNotBlank())
        }
    }

    @Test
    fun `questions are unique`() {
        val questions = NutritionFlashcards.cards.map { it.question }
        assertTrue(questions.size == questions.toSet().size)
    }
}
