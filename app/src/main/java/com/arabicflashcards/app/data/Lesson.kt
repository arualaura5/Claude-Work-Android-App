package com.arabicflashcards.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Lesson(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val cardIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

fun Lesson.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("cardIds", JSONArray(cardIds))
    put("createdAt", createdAt)
}

fun JSONObject.toLesson(): Lesson = Lesson(
    id = getString("id"),
    name = getString("name"),
    cardIds = optJSONArray("cardIds")?.let { array ->
        (0 until array.length()).map { array.getString(it) }
    } ?: emptyList(),
    createdAt = optLong("createdAt", System.currentTimeMillis())
)

fun List<Lesson>.toJsonString(): String {
    val array = JSONArray()
    forEach { array.put(it.toJson()) }
    return array.toString()
}

fun String.toLessons(): List<Lesson> {
    if (isBlank()) return emptyList()
    val array = JSONArray(this)
    return (0 until array.length()).map { array.getJSONObject(it).toLesson() }
}
