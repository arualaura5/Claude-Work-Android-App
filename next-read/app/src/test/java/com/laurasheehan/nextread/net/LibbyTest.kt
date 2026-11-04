package com.laurasheehan.nextread.net

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shapes taken from Libby's real search responses for Idea Store (library key "tlc"). */
class LibbyTest {

    private fun item(title: String, author: String, type: String, available: Boolean, owned: Int, wait: Int?, holds: Int) =
        JSONObject().put("title", title).put("firstCreatorName", author)
            .put("type", JSONObject().put("id", type))
            .put("isAvailable", available).put("ownedCopies", owned)
            .put("estimatedWaitDays", wait ?: JSONObject.NULL).put("holdsCount", holds)

    @Test
    fun picksTheBestCopyOfEachFormatForTheRightBook() {
        val response = JSONObject().put("items", org.json.JSONArray()
            .put(item("Bookmarked: Mark Z. Danielewski's House of Leaves", "Michael Seidlinger", "ebook", false, 1, 42, 2))
            .put(item("The Untethered Soul", "Michael A. Singer", "ebook", false, 1, 6, 1))
            .put(item("The Untethered Soul", "Michael A. Singer", "audiobook", true, 2, 14, 0))
            .put(item("The Untethered Soul", "Michael A. Singer", "audiobook", false, 1, 30, 3)))
        val formats = Libby.match(response, "The Untethered Soul: The Journey Beyond Yourself", "Michael A. Singer")
        assertEquals(listOf("audiobook", "ebook"), formats.map { it.type })
        assertTrue(formats[0].available)
        assertEquals(6, formats[1].waitDays)
    }

    /** The bug the first version had: "Jonasson" never matched "Jónasson". */
    @Test
    fun matchesAuthorsWithAccents() {
        val response = JSONObject().put("items", org.json.JSONArray()
            .put(item("Snowblind", "Ragnar Jónasson", "ebook", true, 2, 14, 0)))
        assertEquals(1, Libby.match(response, "Snowblind", "Ragnar Jonasson").size)
    }

    @Test
    fun doesNotMatchADifferentBookWithASimilarTitle() {
        val response = JSONObject().put("items", org.json.JSONArray()
            .put(item("Bookmarked: Mark Z. Danielewski's House of Leaves", "Michael Seidlinger", "ebook", false, 1, 42, 2)))
        assertTrue(Libby.match(response, "House of Leaves", "Mark Z. Danielewski").isEmpty())
    }

    @Test
    fun keepsAWaitlistWithNoCopies() {
        val response = JSONObject().put("items", org.json.JSONArray()
            .put(item("Hello World", "Hannah Fry", "ebook", false, 0, null, 23)))
        val f = Libby.match(response, "Hello World: Being Human in the Age of Algorithms", "Hannah Fry").single()
        assertEquals(0, f.ownedCopies)
        assertEquals(null, f.waitDays)
    }
}
