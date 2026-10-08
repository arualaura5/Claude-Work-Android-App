package com.laura.royaltasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskFormatterTest {

    private fun f(raw: String, learned: Map<String, String> = emptyMap()) = TaskFormatter.format(raw, learned)

    @Test
    fun `sentence case, single spaces, no trailing full stop`() {
        assertEquals("Buy milk", f("  buy   milk. "))
        assertEquals("Message Eno", f("message Eno ."))
        assertEquals("Call mum, then the GP", f("call mum ,then the gp"))
    }

    @Test
    fun `title case on everyday words is lowered but names stay`() {
        assertEquals("Book flights for Ireland", f("Book Flights for Ireland"))
        assertEquals("Message Eno", f("Message Eno"))
        assertEquals("Pay portion of service charge", f("Pay portion of service charge"))
    }

    @Test
    fun `known names and brands are capitalised`() {
        assertEquals("Buy Huel", f("buy huel"))
        assertEquals("Buy Twinings tea quiet mind", f("buy twinnings tea quiet mind"))
        assertEquals("Flights to Dublin on Friday", f("flights to dublin on friday"))
    }

    @Test
    fun `pronoun I and questions`() {
        assertEquals("Check if I'm free Saturday?", f("check if i'm free saturday?"))
        assertEquals("Do I need a visa", f("do i need a visa"))
    }

    @Test
    fun `shouting is calmed`() {
        assertEquals("Renew passport", f("RENEW PASSPORT"))
    }

    @Test
    fun `new sentence after a full stop gets a capital`() {
        assertEquals("Pack bag. Charge watch", f("pack bag. charge watch."))
    }

    @Test
    fun `learned fixes override and apply to later tasks`() {
        val learned = TaskFormatter.learn("Buy tea quiet mind", "Buy tea Quiet Mind")
        assertEquals(mapOf("quiet" to "Quiet", "mind" to "Mind"), learned)
        assertEquals("Buy Twinings Quiet Mind", f("buy twinnings quiet mind", learned))
    }

    @Test
    fun `small spelling fixes are learned, rewording is not`() {
        assertEquals("Physio", TaskFormatter.learn("Book phisio", "Book Physio")["phisio"])
        assertTrue(TaskFormatter.learn("Buy milk", "Buy bread").isEmpty())
        assertTrue(TaskFormatter.learn("Buy milk", "Buy oat milk").isEmpty())
        // Capitalising the first word is just sentence case.
        assertTrue(TaskFormatter.learn("buy milk", "Buy milk").isEmpty())
    }

    @Test
    fun `odd input survives`() {
        assertEquals("", f("   "))
        assertEquals("...", f("..."))
        assertEquals("iPhone screen repair", f("iphone screen repair"))
    }
}
