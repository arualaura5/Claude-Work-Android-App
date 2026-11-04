package com.laurasheehan.nextread.ai

import com.laurasheehan.nextread.data.objects
import com.laurasheehan.nextread.data.optPositiveDouble
import com.laurasheehan.nextread.data.optPositiveInt
import com.laurasheehan.nextread.data.optPositiveLong
import com.laurasheehan.nextread.data.strings
import org.json.JSONObject

/** A book Claude suggested, or one she asked it to check. */
data class Suggestion(
    val title: String,
    val author: String,
    val year: Int? = null,
    val pages: Int? = null,
    val plot: String = "",
    val why: String = "",
    val watchOut: String = "",
    val tags: List<String> = emptyList(),
    val similarTo: String = "",
    val reception: String = "",
    /** Check tab only: read, maybe, skip or unsure. */
    val verdict: String = "",
    val fit: Int? = null,
    val onShelf: String = "",
    /** The real Goodreads rating, looked up after Claude answered. Never Claude's own guess. */
    val grRating: Double? = null,
    val grCount: Long? = null,
    val grId: String? = null,
)

data class Answer(val intro: String, val books: List<Suggestion>)

/**
 * Reads the Worker's result: Claude's JSON, with a "goodreads" object the Action added to each
 * book it could find on Goodreads.
 */
object AnswerParser {

    fun parse(result: JSONObject): Answer {
        val books = result.optJSONArray("books").objects().mapNotNull { o ->
            val title = o.optString("title").ifBlank { o.optString("asked") }.trim()
            if (title.isEmpty()) return@mapNotNull null
            val gr = o.optJSONObject("goodreads")
            Suggestion(
                title = title,
                author = o.optString("author").trim(),
                year = o.optPositiveInt("year"),
                pages = o.optPositiveInt("pages"),
                plot = o.optString("plot").trim(),
                why = o.optString("why").trim(),
                watchOut = o.optString("watchOut").trim(),
                tags = o.optJSONArray("tags").strings().take(4),
                similarTo = o.optString("similarTo").trim(),
                reception = o.optString("reception").trim(),
                verdict = o.optString("verdict").trim().lowercase(),
                fit = o.optPositiveInt("fit")?.coerceIn(1, 10),
                onShelf = o.optString("onShelf").trim(),
                grRating = gr?.optPositiveDouble("rating")?.takeIf { it <= 5.0 },
                grCount = gr?.optPositiveLong("count"),
                grId = gr?.optString("id")?.takeIf { it.isNotBlank() },
            )
        }
        return Answer(result.optString("intro").trim(), books)
    }
}
