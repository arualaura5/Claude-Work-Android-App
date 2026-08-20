package com.arabicflashcards.app.data

import org.json.JSONArray
import org.json.JSONObject

data class NotebookBackup(
    val cards: List<UserCard>,
    val tags: Set<String>
)

fun NotebookBackup.toJsonString(): String {
    val root = JSONObject()
    val cardsArray = JSONArray()
    cards.forEach { cardsArray.put(it.toJson()) }
    root.put("cards", cardsArray)
    root.put("tags", JSONArray(tags.toList()))
    root.put("exportedAt", System.currentTimeMillis())
    return root.toString(2)
}

fun String.toNotebookBackup(): NotebookBackup {
    val root = JSONObject(this)
    val cardsArray = root.optJSONArray("cards") ?: JSONArray()
    val cards = (0 until cardsArray.length()).map { cardsArray.getJSONObject(it).toUserCard() }
    val tagsArray = root.optJSONArray("tags") ?: JSONArray()
    val tags = (0 until tagsArray.length()).map { tagsArray.getString(it) }.toSet()
    return NotebookBackup(cards, tags)
}
