package com.laura.royaltasks.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val priority: Priority = Priority.NORMAL,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val completedEpochDay: Long? = null,
    /** XP granted when it was ticked, so un-ticking takes back exactly that. */
    val xpAwarded: Int = 0
) {
    val isDone: Boolean get() = completedAt != null
}

fun List<Task>.toJsonString(): String {
    val array = JSONArray()
    forEach { task ->
        array.put(
            JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("priority", task.priority.name)
                put("createdAt", task.createdAt)
                task.completedAt?.let { put("completedAt", it) }
                task.completedEpochDay?.let { put("completedEpochDay", it) }
                put("xpAwarded", task.xpAwarded)
            }
        )
    }
    return array.toString()
}

/**
 * Every field except title is optional on read, so tasks saved by older
 * builds keep loading when new fields are added.
 */
fun String.toTasks(): List<Task> {
    val array = runCatching { JSONArray(this) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val obj = array.optJSONObject(i) ?: return@mapNotNull null
        val title = obj.optString("title", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Task(
            id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
            title = title,
            priority = runCatching { Priority.valueOf(obj.optString("priority")) }
                .getOrDefault(Priority.NORMAL),
            createdAt = obj.optLong("createdAt", 0L),
            completedAt = if (obj.has("completedAt")) obj.optLong("completedAt") else null,
            completedEpochDay = if (obj.has("completedEpochDay")) obj.optLong("completedEpochDay") else null,
            xpAwarded = obj.optInt("xpAwarded", 0)
        )
    }
}
