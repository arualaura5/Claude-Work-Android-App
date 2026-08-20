package com.arabicflashcards.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class UserCard(
    val id: String = UUID.randomUUID().toString(),
    val english: String,
    val egyptian: String,
    val createdAt: Long = System.currentTimeMillis(),
    val timesReviewed: Int = 0,
    val timesCorrect: Int = 0,
    val mastered: Boolean = false
)

fun UserCard.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("english", english)
    put("egyptian", egyptian)
    put("createdAt", createdAt)
    put("timesReviewed", timesReviewed)
    put("timesCorrect", timesCorrect)
    put("mastered", mastered)
}

fun JSONObject.toUserCard(): UserCard = UserCard(
    id = getString("id"),
    english = getString("english"),
    egyptian = getString("egyptian"),
    createdAt = optLong("createdAt", System.currentTimeMillis()),
    timesReviewed = optInt("timesReviewed", 0),
    timesCorrect = optInt("timesCorrect", 0),
    mastered = optBoolean("mastered", false)
)

fun List<UserCard>.toJsonString(): String {
    val array = JSONArray()
    forEach { array.put(it.toJson()) }
    return array.toString()
}

fun String.toUserCards(): List<UserCard> {
    if (isBlank()) return emptyList()
    val array = JSONArray(this)
    return (0 until array.length()).map { array.getJSONObject(it).toUserCard() }
}
