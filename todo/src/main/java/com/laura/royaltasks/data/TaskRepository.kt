package com.laura.royaltasks.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "royal_tasks")

/** Completed tasks older than this are dropped to keep storage small. */
private const val KEEP_DONE_DAYS = 30

data class CompletionResult(
    val earnedCrown: Boolean,
    val levelBefore: Int,
    val levelAfter: Int
)

class TaskRepository(private val context: Context) {

    // Never rename these keys without a migration — they hold the saved data.
    private val tasksKey = stringPreferencesKey("tasks_json")
    private val xpKey = intPreferencesKey("total_xp")
    private val currentStreakKey = intPreferencesKey("current_streak")
    private val longestStreakKey = intPreferencesKey("longest_streak")
    private val lastDoneDayKey = longPreferencesKey("last_done_epoch_day")
    private val crownsKey = intPreferencesKey("crowns")
    private val lastCrownDayKey = longPreferencesKey("last_crown_epoch_day")
    private val soundEnabledKey = booleanPreferencesKey("sound_enabled")
    private val learnedWordsKey = stringPreferencesKey("learned_words_json")
    private val titlesTidiedKey = booleanPreferencesKey("titles_tidied_v1")
    private val ideasKey = stringPreferencesKey("ideas_json")

    val tasks: Flow<List<Task>> = context.dataStore.data.map { it.readTasks() }
    val progress: Flow<Progress> = context.dataStore.data.map { it.readProgress() }
    val ideas: Flow<List<Idea>> = context.dataStore.data.map { it.readIdeas() }
    val soundEnabled: Flow<Boolean> = context.dataStore.data.map { it[soundEnabledKey] ?: true }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[soundEnabledKey] = enabled }
    }

    suspend fun add(title: String, priority: Priority) {
        if (title.isBlank()) return
        context.dataStore.edit { prefs ->
            val clean = TaskFormatter.format(title, prefs.readLearned())
            prefs[tasksKey] = (prefs.readTasks() + Task(title = clean, priority = priority)).toJsonString()
        }
    }

    /** Word fixes made here are remembered and applied to future tasks. */
    suspend fun update(id: String, title: String, priority: Priority) {
        if (title.isBlank()) return
        context.dataStore.edit { prefs ->
            val tasks = prefs.readTasks()
            val old = tasks.find { it.id == id } ?: return@edit
            val learned = prefs.readLearned() + TaskFormatter.learn(old.title, title)
            prefs[learnedWordsKey] = JSONObject(learned).toString()
            val clean = TaskFormatter.format(title, learned)
            prefs[tasksKey] = tasks
                .map { if (it.id == id) it.copy(title = clean, priority = priority) else it }
                .toJsonString()
        }
    }

    suspend fun addIdea(text: String) {
        val clean = tidyIdea(text)
        if (clean.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[ideasKey] = (prefs.readIdeas() + Idea(text = clean)).ideasToJson()
        }
    }

    suspend fun updateIdea(id: String, text: String) {
        val clean = tidyIdea(text)
        if (clean.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[ideasKey] = prefs.readIdeas()
                .map { if (it.id == id) it.copy(text = clean) else it }
                .ideasToJson()
        }
    }

    suspend fun deleteIdea(id: String) {
        context.dataStore.edit { prefs ->
            prefs[ideasKey] = prefs.readIdeas().filterNot { it.id == id }.ideasToJson()
        }
    }

    suspend fun restoreIdea(idea: Idea) {
        context.dataStore.edit { prefs ->
            val current = prefs.readIdeas()
            if (current.none { it.id == idea.id }) prefs[ideasKey] = (current + idea).ideasToJson()
        }
    }

    /**
     * Moves an idea onto the task list in one write, so it can never end up in
     * both or neither. Returns the new task's id for undo.
     */
    suspend fun ideaToTask(id: String): String? {
        var taskId: String? = null
        context.dataStore.edit { prefs ->
            val ideas = prefs.readIdeas()
            val idea = ideas.find { it.id == id } ?: return@edit
            val title = TaskFormatter.format(idea.text.replace('\n', ' '), prefs.readLearned())
            val task = Task(title = title)
            prefs[tasksKey] = (prefs.readTasks() + task).toJsonString()
            prefs[ideasKey] = ideas.filterNot { it.id == id }.ideasToJson()
            taskId = task.id
        }
        return taskId
    }

    /** One-off tidy of tasks typed before the formatter existed. */
    suspend fun tidyExistingOnce() {
        context.dataStore.edit { prefs ->
            if (prefs[titlesTidiedKey] == true) return@edit
            val learned = prefs.readLearned()
            prefs[tasksKey] = prefs.readTasks()
                .map { if (it.isDone) it else it.copy(title = TaskFormatter.format(it.title, learned)) }
                .toJsonString()
            prefs[titlesTidiedKey] = true
        }
    }

    suspend fun delete(id: String) {
        context.dataStore.edit { prefs ->
            prefs[tasksKey] = prefs.readTasks().filterNot { it.id == id }.toJsonString()
        }
    }

    suspend fun restore(task: Task) {
        context.dataStore.edit { prefs ->
            val current = prefs.readTasks()
            if (current.none { it.id == task.id }) {
                prefs[tasksKey] = (current + task).toJsonString()
            }
        }
    }

    suspend fun complete(id: String, today: Long): CompletionResult? {
        var result: CompletionResult? = null
        context.dataStore.edit { prefs ->
            val tasks = prefs.readTasks()
            val task = tasks.find { it.id == id && !it.isDone } ?: return@edit
            val xp = task.priority.xp
            val before = prefs.readProgress()
            val doneToday = tasks.count { it.completedEpochDay == today } + 1
            val after = before.afterCompleting(xp, today, doneToday)

            prefs[tasksKey] = tasks
                .map {
                    if (it.id == id) {
                        it.copy(
                            completedAt = System.currentTimeMillis(),
                            completedEpochDay = today,
                            xpAwarded = xp
                        )
                    } else it
                }
                .filter { t -> t.completedEpochDay.let { it == null || it >= today - KEEP_DONE_DAYS } }
                .toJsonString()
            prefs.writeProgress(after)

            result = CompletionResult(
                earnedCrown = after.crowns > before.crowns,
                levelBefore = before.level,
                levelAfter = after.level
            )
        }
        return result
    }

    suspend fun uncomplete(id: String) {
        context.dataStore.edit { prefs ->
            val tasks = prefs.readTasks()
            val task = tasks.find { it.id == id && it.isDone } ?: return@edit
            prefs[tasksKey] = tasks
                .map {
                    if (it.id == id) {
                        it.copy(completedAt = null, completedEpochDay = null, xpAwarded = 0)
                    } else it
                }
                .toJsonString()
            prefs.writeProgress(prefs.readProgress().afterUndoing(task.xpAwarded))
        }
    }

    private fun Preferences.readTasks(): List<Task> = this[tasksKey]?.toTasks() ?: emptyList()

    private fun Preferences.readIdeas(): List<Idea> = this[ideasKey]?.toIdeas() ?: emptyList()

    private fun Preferences.readLearned(): Map<String, String> {
        val json = this[learnedWordsKey] ?: return emptyMap()
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        return obj.keys().asSequence().associateWith { obj.optString(it) }.filterValues { it.isNotBlank() }
    }

    private fun Preferences.readProgress() = Progress(
        totalXp = this[xpKey] ?: 0,
        currentStreak = this[currentStreakKey] ?: 0,
        longestStreak = this[longestStreakKey] ?: 0,
        lastDoneEpochDay = this[lastDoneDayKey],
        crowns = this[crownsKey] ?: 0,
        lastCrownEpochDay = this[lastCrownDayKey]
    )

    private fun MutablePreferences.writeProgress(p: Progress) {
        this[xpKey] = p.totalXp
        this[currentStreakKey] = p.currentStreak
        this[longestStreakKey] = p.longestStreak
        p.lastDoneEpochDay?.let { this[lastDoneDayKey] = it }
        this[crownsKey] = p.crowns
        p.lastCrownEpochDay?.let { this[lastCrownDayKey] = it }
    }
}
