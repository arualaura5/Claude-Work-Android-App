package com.laura.royaltasks.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Idea(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Ideas keep the user's own punctuation and line breaks; only the edges are tidied. */
fun tidyIdea(raw: String): String =
    raw.trim().replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n")
        .replaceFirstChar { it.uppercaseChar() }

fun List<Idea>.ideasToJson(): String {
    val array = JSONArray()
    forEach { idea ->
        array.put(
            JSONObject().apply {
                put("id", idea.id)
                put("text", idea.text)
                put("createdAt", idea.createdAt)
            }
        )
    }
    return array.toString()
}

/** Optional-with-default on read, like tasks, so older saves keep loading. */
fun String.toIdeas(): List<Idea> {
    val array = runCatching { JSONArray(this) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val obj = array.optJSONObject(i) ?: return@mapNotNull null
        val text = obj.optString("text", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Idea(
            id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
            text = text,
            createdAt = obj.optLong("createdAt", 0L)
        )
    }
}
