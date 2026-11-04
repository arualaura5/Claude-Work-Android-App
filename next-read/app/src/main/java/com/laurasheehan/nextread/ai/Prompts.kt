package com.laurasheehan.nextread.ai

import com.laurasheehan.nextread.data.Notebook
import com.laurasheehan.nextread.data.ShelfBook

/**
 * What Claude is told. Carried over from the Next Read artifact, which she shaped: her own words
 * outweigh star ratings, and Claude never rates a book itself (real Goodreads ratings are looked
 * up separately, after it answers).
 */
object Prompts {

    val CHIPS = listOf(
        "Icelandic & Nordic chill", "Twisty domestic thriller", "Big Tech exposé", "Body & gut science",
        "Egypt & the Arab world", "Historical true crime", "Books about books", "Translated literary fiction",
        "Survival & adventure", "Funny and warm", "Under 300 pages", "Audiobook-friendly",
    )

    private val BOOK_FIELDS = """"title": "", "author": "", "year": 2019, "pages": 320, "plot": "3 spoiler-free sentences: the premise, setting, main character and what's at stake (for nonfiction: what it covers and how)""""

    private val DISCOVER_FORMAT = """Reply with only JSON in this shape:
{"intro": "one warm sentence about the picks", "books": [{$BOOK_FIELDS, "why": "two sentences that tie it to specific books on their shelves", "tags": ["3 short style tags"], "similarTo": "a title from their shelves, or empty", "reception": "one line on how readers and critics received it"}]}
Exactly 6 books. Do not include ratings or star scores."""

    fun digest(n: Notebook, includeProfile: Boolean = true): String {
        fun line(b: ShelfBook): String = buildString {
            append("- ${b.title} — ${b.author}")
            if (b.myRating > 0) append(" [${b.myRating}★]")
            b.pages?.let { append(", ${it}p") }
            b.year?.let { append(", $it") }
            n.noteFor(b).takeIf { it.isNotBlank() }?.let { append("\n    Their own words: \"$it\"") }
        }
        val read = n.books.filter { it.isRead }
        val alsoRated = n.books.filter { it.myRating > 0 && it.isWantToRead }
        return listOfNotNull(
            n.taste.takeIf { it.isNotBlank() }?.let {
                "What the reader says they want from a book, in their own words (treat this as the most important guide):\n$it"
            },
            n.profile.takeIf { includeProfile && it.isNotBlank() }?.let { "Reader's style, as summarised earlier:\n$it" },
            "Books they have read (★ = their rating out of 5; no ★ = unrated):\n" +
                read.joinToString("\n", transform = ::line).ifEmpty { "(none)" },
            alsoRated.takeIf { it.isNotEmpty() }?.let { "Also rated:\n" + it.joinToString("\n", transform = ::line) },
            "Currently reading:\n" + n.books.filter { it.isCurrent }.joinToString("\n", transform = ::line).ifEmpty { "(none)" },
            "Already on their want-to-read list:\n" +
                n.books.filter { it.isWantToRead }.joinToString("\n", transform = ::line).ifEmpty { "(none)" },
            n.saved.takeIf { it.isNotEmpty() }?.let { saved ->
                "Already saved from earlier suggestions:\n" + saved.joinToString("\n") { "- ${it.title} — ${it.author}" }
            },
            n.notForMe.takeIf { it.isNotEmpty() }?.let { rejected ->
                "Suggestions they rejected (avoid books like these, and respect the reason):\n" +
                    rejected.joinToString("\n") { "- ${it.title} — ${it.author}" + if (it.why.isNotBlank()) " (${it.why})" else "" }
            },
        ).joinToString("\n\n")
    }

    fun discover(n: Notebook, mood: String, chips: Set<String>, moreLike: String?): String {
        val want = listOfNotNull(
            mood.trim().takeIf { it.isNotEmpty() }?.let { "In their words: $it" },
            chips.takeIf { it.isNotEmpty() }?.let { "Styles they picked: ${it.joinToString(", ")}" },
            moreLike?.takeIf { it.isNotBlank() }?.let { "They want more like: $it" },
        ).joinToString("\n").ifEmpty {
            "No specific request: surprise them with picks that fit their strongest tastes, plus one wildcard."
        }
        return """You are a widely read librarian helping one reader in London find their next book. They borrow from Idea Store (Tower Hamlets) — print, Libby and BorrowBox.

${digest(n)}

What they want now:
$want

Rules: their own words about a book outweigh its star rating — use them to judge pace, tone, prose style and length they enjoy or dislike. Never suggest a book listed above. Suggest only real, published books you are confident exist, with the exact title and the author's usual name. Prefer books a UK public library is likely to stock. Mix well-known and lesser-known titles. Make each "why" specific to their own books and ratings, not generic.

$DISCOVER_FORMAT"""
    }

    /** "Adjust these": the first request, Claude's last answer, and how she wants it changed. */
    fun refine(firstPrompt: String, lastAnswerJson: String, adjustment: String): String = """$firstPrompt

Your previous reply was:
$lastAnswerJson

They want you to adjust: $adjustment
Give 6 different books — none from your previous reply or from the lists above. Numbers like #2 refer to your previous list.

$DISCOVER_FORMAT"""

    fun check(n: Notebook, lines: List<String>, hasPhoto: Boolean): String {
        val asked = lines.joinToString("\n") { "- $it" }
        val what = if (hasPhoto) {
            "They have attached a photo. It may show a book cover, a spine, a page, or a shelf or table of several books. " +
                "Identify every book whose title you can read (with its author) and judge each one. If you can see a book " +
                "but can't make out its title, add an entry with verdict \"unsure\" and say what you can see." +
                (if (lines.isNotEmpty()) "\nThey also typed:\n$asked" else "")
        } else {
            "They are asking whether they should read these books:\n$asked"
        }
        return """You are a widely read librarian who knows this reader well.

${digest(n)}

$what

For each, judge honestly how well it fits THEIR taste, using their own words and notes first, then their ratings. Be direct: say "skip" when it is a poor fit, and explain why in terms of their own books. If a line is ambiguous or you don't recognise the book, set verdict "unsure" and say what you think they mean. If it is already on their shelves, say so in "onShelf".

Reply with only JSON in this shape:
{"books": [{"asked": "the line as they wrote it", $BOOK_FIELDS, "verdict": "read | maybe | skip | unsure", "fit": 7, "why": "2-3 sentences tied to their own books and notes", "watchOut": "one short line on what might not suit them, or empty", "tags": ["3 short style tags"], "onShelf": "read | want-to-read | none"}]}
One entry per book asked about or seen in the photo, in order (at most 12). For a book from the photo, "asked" says where it is, e.g. "photo, top shelf". "fit" is 1-10. Do not include ratings or star scores."""
    }

    fun profile(n: Notebook): String = """Here is one reader's Goodreads data.

${digest(n, includeProfile = false)}

Describe their reading taste in 4 to 6 short lines, each starting with "• ". Base it mostly on their own words and notes, then their ratings. Cover genres, tone, pacing, prose style, subjects, what they dislike, and one direction they might enjoy exploring. Address them as "you".

Reply with only JSON in this shape: {"profile": "• first line\n• second line"}"""
}
