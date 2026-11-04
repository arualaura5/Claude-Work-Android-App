package com.laurasheehan.nextread.net

import com.laurasheehan.nextread.data.objects
import com.laurasheehan.nextread.data.shortTitle
import com.laurasheehan.nextread.data.titleKey
import java.net.URLEncoder
import java.text.Normalizer
import org.json.JSONObject

/** One format of a book in Idea Store's Libby collection. */
data class LibbyFormat(
    /** "ebook" or "audiobook". */
    val type: String,
    val available: Boolean,
    /** Copies the library owns now; 0 with a waitlist means "no copies, notify me". */
    val ownedCopies: Int,
    val waitDays: Int?,
    val holds: Int,
)

/**
 * Live availability in Idea Store's Libby (library key "tlc"), from the same search Libby's own
 * website uses. Matches by title and author surname with accents stripped, so "Jonasson" finds
 * "Jónasson".
 */
object Libby {
    const val LIBRARY = "tlc"

    fun searchUrl(title: String, author: String): String =
        "https://libbyapp.com/search/$LIBRARY/search/query-" +
            URLEncoder.encode("${shortTitle(title)} $author", "UTF-8").replace("+", "%20") + "/page-1"

    fun lookup(title: String, author: String): List<LibbyFormat> {
        val query = URLEncoder.encode("${shortTitle(title)} $author", "UTF-8")
        val response = Http.request("https://thunder.api.overdrive.com/v2/libraries/$LIBRARY/media?query=$query&perPage=24")
        if (response.code != 200) throw java.io.IOException("Libby didn't answer (HTTP ${response.code}).")
        return match(JSONObject(response.body), title, author)
    }

    /** The best copy of each format, from a Libby search response. */
    fun match(response: JSONObject, title: String, author: String): List<LibbyFormat> {
        val wanted = titleKey(title)
        val surname = plain(author).split(" ").lastOrNull().orEmpty()
        val hits = response.optJSONArray("items").objects().filter { item ->
            val creator = plain(item.optString("firstCreatorName"))
            titleKey(item.optString("title")) == wanted && (surname.isEmpty() || creator.contains(surname))
        }
        return hits.map { item ->
            LibbyFormat(
                type = item.optJSONObject("type")?.optString("id").orEmpty(),
                available = item.optBoolean("isAvailable"),
                ownedCopies = item.optInt("ownedCopies"),
                waitDays = if (item.isNull("estimatedWaitDays")) null else item.optInt("estimatedWaitDays"),
                holds = item.optInt("holdsCount"),
            )
        }
            .filter { it.type == "ebook" || it.type == "audiobook" }
            .groupBy { it.type }
            .map { (_, copies) -> copies.maxWith(compareBy<LibbyFormat>({ it.available }, { -(it.waitDays ?: 9999) })) }
            .sortedBy { it.type }
    }

    private fun plain(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
