package com.laurasheehan.nextread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookTest {

    // ISBN cells are plain here: the export's ="..." form would end this raw string early.
    private val csv = """Book Id,Title,Author,Author l-f,Additional Authors,ISBN,ISBN13,My Rating,Publisher,Binding,Number of Pages,Year Published,Original Publication Year,Date Read,Date Added,Bookshelves,Bookshelves with positions,Exclusive Shelf,My Review,Spoiler,Private Notes,Read Count,Owned Copies
1232,"The Shadow of the Wind (The Cemetery of Forgotten Books, #1)",Carlos Ruiz Zafón,"Zafón, Carlos Ruiz",Lucia Graves,,,0,Penguin Books,Paperback,487,2005,2001,2026/10/24,2026/06/21,,,read,,,,1,0
15852019,I Remember You,Yrsa Sigurdardottir,"Sigurdardottir, Yrsa",Philip Roughton,1444738496,9781444738490,5.0,Hodder & Stoughton,Paperback,391,2012,2022,,2026/10/22,,,read,,,,1,0
201422457,"Eton Rogue: 'A delicious tale in which class, politics, and a toxic press all jostle'",William   Coles,"Coles, William",,1915643325,9781915643322,0,Legend Press,Kindle Edition,386,2024,,,2026/03/26,to-read,to-read (#19),to-read,,,,0,0
"""

    @Test
    fun readsGoodreadsExport() {
        val books = GoodreadsCsv.parse("﻿" + csv)
        assertEquals(3, books.size)
        val shadow = books[0]
        assertEquals("The Shadow of the Wind (The Cemetery of Forgotten Books, #1)", shadow.title)
        assertEquals("Carlos Ruiz Zafón", shadow.author)
        assertEquals(2001, shadow.year)
        assertEquals(487, shadow.pages)
        assertTrue(shadow.isRead)
        assertEquals(5, books[1].myRating)
        // Quoted commas stay in the title; runs of spaces in names collapse.
        assertTrue(books[2].title.startsWith("Eton Rogue: 'A delicious tale in which class, politics"))
        assertEquals("William Coles", books[2].author)
        assertTrue(books[2].isWantToRead)
        assertEquals(2024, books[2].year)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesAFileThatIsNotAGoodreadsExport() {
        GoodreadsCsv.parse("name,price\nbread,1\n")
    }

    @Test
    fun reimportKeepsRatingsAndNotes() {
        val first = GoodreadsCsv.parse(csv)
        val before = Notebook(
            books = first.map { if (it.id == "1232") it.copy(grRating = 4.31, grCount = 759340) else it },
            notes = mapOf("b1232" to "Long and flowery."),
        )
        val after = GoodreadsCsv.mergeInto(before, GoodreadsCsv.parse(csv), "2026-11-04T10:00:00Z")
        assertEquals(4.31, after.books.first { it.id == "1232" }.grRating!!, 0.0)
        assertEquals("Long and flowery.", after.noteFor(after.books.first { it.id == "1232" }))
        assertEquals("2026-11-04T10:00:00Z", after.importedAt)
    }

    /** The backup the Next Read artifact exported must import unchanged, notes included. */
    @Test
    fun readsTheArtifactBackup() {
        val json = """{"format":"next-read-backup","version":1,
          "books":[{"id":"1232","t":"The Shadow of the Wind","a":"Carlos Ruiz Zafón","shelf":"read","r":0,"pages":487,"year":2001,"read":"2026/10/24","added":"2026/06/21","gr":4.31,"grCount":759340},
                   {"id":"","t":"Lock Every Door","a":"Riley Sager","shelf":"read","r":0,"pages":null,"year":2019,"read":"","added":"2026-10-30","gr":3.87,"grCount":335560}],
          "notes":{"b1232":"It was OK.","lock-every-door-riley-sager":"Really enjoyed it."},
          "taste":"Thrillers and suspense.","profile":"","notForMe":[{"title":"Audition","author":"Katie Kitamura","why":"Not my style"}],
          "saved":[],"importedAt":"2026-10-30T16:30:00.000Z","ratingsAt":"2026-11-03"}"""
        val n = NotebookCodec.decode(json)
        assertEquals(2, n.books.size)
        assertEquals("It was OK.", n.noteFor(n.books[0]))
        assertEquals("Really enjoyed it.", n.noteFor(n.books[1]))
        assertNull(n.books[1].pages)
        assertEquals("Thrillers and suspense.", n.taste)
        assertEquals("Audition", n.notForMe.single().title)
        // And survives a round trip through the app's own format.
        assertEquals(n, NotebookCodec.decode(NotebookCodec.encode(n)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesSomeOtherJson() {
        NotebookCodec.decode("""{"books":[]}""")
    }

    @Test
    fun titlesMatchAcrossSeriesTagsAndSubtitles() {
        assertEquals("the housemaid", titleKey("The Housemaid (The Housemaid, #1)"))
        assertEquals(titleKey("Stolen Focus"), titleKey("Stolen Focus: Why You Can't Pay Attention"))
        val n = Notebook(books = GoodreadsCsv.parse(csv))
        assertEquals("1232", n.shelfBookTitled("The Shadow of the Wind")?.id)
    }
}
