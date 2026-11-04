package com.laurasheehan.nextread.data

import java.text.Normalizer
import org.json.JSONArray
import org.json.JSONObject

/** One book from her Goodreads export, plus its Goodreads rating when one has been looked up. */
data class ShelfBook(
    val id: String,
    val title: String,
    val author: String,
    /** Goodreads' exclusive shelf: read, to-read, currently-reading, maybe, did-not-finish… */
    val shelf: String,
    /** Her own stars, 0 when unrated. */
    val myRating: Int,
    val pages: Int?,
    val year: Int?,
    val dateRead: String,
    val added: String,
    val grRating: Double? = null,
    val grCount: Long? = null,
) {
    val isWantToRead: Boolean get() = shelf == "to-read" || shelf == "maybe"
    val isCurrent: Boolean get() = shelf == "currently-reading"
    val isRead: Boolean get() = !isWantToRead && !isCurrent

    /**
     * The key her notes are stored under. Matches the Next Read artifact, so notes written there
     * carry over: "b" + Goodreads id, or a title-author slug for a book added by hand.
     */
    val key: String get() = if (id.isNotBlank()) "b$id" else slug("$title-$author")
}

data class RejectedBook(val title: String, val author: String, val why: String)

data class SavedBook(
    val id: String,
    val title: String,
    val author: String,
    val plot: String,
    val why: String,
    val tags: List<String>,
    val grRating: Double?,
    val grCount: Long?,
    val grId: String?,
    val savedAt: String,
    val done: Boolean,
)

/** Everything the app knows about her reading. Kept in one JSON file on the phone. */
data class Notebook(
    val books: List<ShelfBook> = emptyList(),
    /** Her own words about a book, keyed by [ShelfBook.key]. */
    val notes: Map<String, String> = emptyMap(),
    /** "What you want from a book", in her words. The most important guide for Claude. */
    val taste: String = "",
    /** Claude's summary of her style, from "Describe my style". */
    val profile: String = "",
    val notForMe: List<RejectedBook> = emptyList(),
    val saved: List<SavedBook> = emptyList(),
    val importedAt: String = "",
    val ratingsAt: String = "",
) {
    fun noteFor(book: ShelfBook): String = notes[book.key].orEmpty()

    /** A shelf book with the same title, so a suggestion or a check can show its stored rating. */
    fun shelfBookTitled(title: String): ShelfBook? {
        val wanted = titleKey(title)
        return books.firstOrNull { titleKey(it.title) == wanted }
    }
}

fun slug(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(120)
        .ifEmpty { "book" }

/** "The Housemaid (The Housemaid, #1)" and "The Housemaid: A Novel" both become "the housemaid". */
fun titleKey(title: String): String =
    Normalizer.normalize(shortTitle(title).lowercase(), Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

fun shortTitle(title: String): String = title.split(':', '(').first().trim()

/**
 * The notebook's file format. The field names for books (t, a, r…) are the Next Read artifact's,
 * so the backup exported from the artifact imports here unchanged.
 */
object NotebookCodec {
    const val FORMAT = "next-read-backup"

    fun encode(n: Notebook): String = JSONObject()
        .put("format", FORMAT)
        .put("version", 1)
        .put("books", JSONArray().apply { n.books.forEach { put(encodeBook(it)) } })
        .put("notes", JSONObject().apply { n.notes.forEach { (k, v) -> put(k, v) } })
        .put("taste", n.taste)
        .put("profile", n.profile)
        .put("notForMe", JSONArray().apply {
            n.notForMe.forEach { put(JSONObject().put("title", it.title).put("author", it.author).put("why", it.why)) }
        })
        .put("saved", JSONArray().apply { n.saved.forEach { put(encodeSaved(it)) } })
        .put("importedAt", n.importedAt)
        .put("ratingsAt", n.ratingsAt)
        .toString(1)

    /** Throws if the text isn't a Next Read notebook, so a wrong file never replaces her data. */
    fun decode(text: String): Notebook {
        val o = JSONObject(text)
        require(o.optString("format") == FORMAT) { "That file isn't a Next Read backup." }
        val notes = o.optJSONObject("notes")?.let { obj -> obj.keys().asSequence().associateWith { obj.optString(it) } }
            .orEmpty()
            .filterValues { it.isNotBlank() }
        return Notebook(
            books = o.optJSONArray("books").objects().map(::decodeBook).filter { it.title.isNotBlank() },
            notes = notes,
            taste = o.optString("taste"),
            profile = o.optString("profile"),
            notForMe = o.optJSONArray("notForMe").objects().map {
                RejectedBook(it.optString("title"), it.optString("author"), it.optString("why"))
            },
            saved = o.optJSONArray("saved").objects().map(::decodeSaved).filter { it.title.isNotBlank() },
            importedAt = o.optString("importedAt"),
            ratingsAt = o.optString("ratingsAt"),
        )
    }

    private fun encodeBook(b: ShelfBook) = JSONObject()
        .put("id", b.id).put("t", b.title).put("a", b.author).put("shelf", b.shelf).put("r", b.myRating)
        .putOpt("pages", b.pages).putOpt("year", b.year).put("read", b.dateRead).put("added", b.added)
        .putOpt("gr", b.grRating).putOpt("grCount", b.grCount)

    private fun decodeBook(o: JSONObject) = ShelfBook(
        id = o.optString("id"),
        title = o.optString("t"),
        author = o.optString("a"),
        shelf = o.optString("shelf"),
        myRating = o.optInt("r"),
        pages = o.optPositiveInt("pages"),
        year = o.optPositiveInt("year"),
        dateRead = o.optString("read"),
        added = o.optString("added"),
        grRating = o.optPositiveDouble("gr"),
        grCount = o.optPositiveLong("grCount"),
    )

    private fun encodeSaved(s: SavedBook) = JSONObject()
        .put("id", s.id).put("title", s.title).put("author", s.author).put("plot", s.plot).put("why", s.why)
        .put("tags", JSONArray(s.tags)).putOpt("gr", s.grRating).putOpt("grCount", s.grCount).putOpt("grId", s.grId)
        .put("savedAt", s.savedAt).put("done", s.done)

    private fun decodeSaved(o: JSONObject) = SavedBook(
        id = o.optString("id").ifBlank { slug(o.optString("title") + "-" + o.optString("author")) },
        title = o.optString("title"),
        author = o.optString("author"),
        plot = o.optString("plot"),
        why = o.optString("why"),
        tags = o.optJSONArray("tags").strings(),
        grRating = o.optPositiveDouble("gr"),
        grCount = o.optPositiveLong("grCount"),
        grId = o.optString("grId").ifBlank { null },
        savedAt = o.optString("savedAt"),
        done = o.optBoolean("done"),
    )
}

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { opt(it)?.toString()?.takeIf(String::isNotBlank) }

internal fun JSONObject.optPositiveInt(name: String): Int? =
    if (isNull(name)) null else optDouble(name, Double.NaN).takeIf { it.isFinite() && it > 0 }?.toInt()

internal fun JSONObject.optPositiveLong(name: String): Long? =
    if (isNull(name)) null else optDouble(name, Double.NaN).takeIf { it.isFinite() && it > 0 }?.toLong()

internal fun JSONObject.optPositiveDouble(name: String): Double? =
    if (isNull(name)) null else optDouble(name, Double.NaN).takeIf { it.isFinite() && it > 0 }
