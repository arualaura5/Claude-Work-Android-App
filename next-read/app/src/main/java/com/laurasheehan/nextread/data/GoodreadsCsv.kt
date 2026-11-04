package com.laurasheehan.nextread.data

/** Reads goodreads_library_export.csv (My Books → Import and export → Export Library). */
object GoodreadsCsv {

    /** Throws if the file isn't a Goodreads export, so a wrong file never replaces her shelves. */
    fun parse(text: String): List<ShelfBook> {
        val rows = rows(text.removePrefix("﻿"))
        val head = rows.firstOrNull() ?: throw IllegalArgumentException("That file is empty.")
        val ix = head.withIndex().associate { (i, name) -> name.trim() to i }
        require(listOf("Title", "Author", "Exclusive Shelf").all { it in ix }) {
            "That file doesn't look like a Goodreads export. Choose goodreads_library_export.csv."
        }
        fun List<String>.col(name: String): String = ix[name]?.let { getOrNull(it) }.orEmpty().trim()
        fun number(v: String): Int = v.toDoubleOrNull()?.takeIf { it > 0 }?.let { Math.round(it).toInt() } ?: 0
        return rows.drop(1).mapNotNull { r ->
            val title = r.col("Title")
            if (title.isEmpty()) return@mapNotNull null
            ShelfBook(
                id = r.col("Book Id"),
                title = title,
                author = r.col("Author").replace(Regex("\\s+"), " "),
                shelf = r.col("Exclusive Shelf"),
                myRating = number(r.col("My Rating")),
                pages = number(r.col("Number of Pages")).takeIf { it > 0 },
                year = (number(r.col("Original Publication Year")).takeIf { it > 0 }
                    ?: number(r.col("Year Published")).takeIf { it > 0 }),
                dateRead = r.col("Date Read"),
                added = r.col("Date Added"),
            )
        }
    }

    /**
     * A new export replaces the shelves, but keeps what the app had learned about each book:
     * its Goodreads rating. Her notes are keyed by book, so they are never touched by an import.
     */
    fun mergeInto(old: Notebook, fresh: List<ShelfBook>, importedAt: String): Notebook {
        val ratings = old.books.filter { it.grRating != null }.associateBy { it.key }
        return old.copy(
            books = fresh.map { b -> ratings[b.key]?.let { b.copy(grRating = it.grRating, grCount = it.grCount) } ?: b },
            importedAt = importedAt,
        )
    }

    /** RFC 4180: quoted fields may hold commas, newlines and doubled quotes. */
    internal fun rows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r', '\n' -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field.clear()
                    if (row.size > 1) rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.size > 1) rows.add(row)
        }
        return rows
    }
}
