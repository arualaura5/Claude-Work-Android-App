package com.laurasheehan.nextread.ui

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.nextread.ai.Answer
import com.laurasheehan.nextread.ai.AnswerParser
import com.laurasheehan.nextread.ai.Prompts
import com.laurasheehan.nextread.ai.Suggestion
import com.laurasheehan.nextread.data.GoodreadsCsv
import com.laurasheehan.nextread.data.Notebook
import com.laurasheehan.nextread.data.NotebookCodec
import com.laurasheehan.nextread.data.NotebookStore
import com.laurasheehan.nextread.data.RejectedBook
import com.laurasheehan.nextread.data.SavedBook
import com.laurasheehan.nextread.data.SecretStore
import com.laurasheehan.nextread.data.ShelfBook
import com.laurasheehan.nextread.data.slug
import com.laurasheehan.nextread.net.BooksBackend
import com.laurasheehan.nextread.net.Libby
import com.laurasheehan.nextread.net.LibbyFormat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One of the app's questions to Claude: Discover, Check, or Describe my style. */
data class JobUi(
    val running: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val answer: Answer? = null,
)

sealed interface LibbyUi {
    data object Loading : LibbyUi
    data class Found(val formats: List<LibbyFormat>) : LibbyUi
    data object Missing : LibbyUi
    data object Unavailable : LibbyUi
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = NotebookStore(app)
    private val secrets = SecretStore(app)
    /** Questions still waiting, and the last answers, so both survive the app being closed. */
    private val jobs = app.getSharedPreferences("jobs", Context.MODE_PRIVATE)

    val notebook: StateFlow<Notebook> = store.notebook

    private val _discover = MutableStateFlow(JobUi(answer = savedAnswer(DISCOVER)))
    val discover: StateFlow<JobUi> = _discover.asStateFlow()
    private val _check = MutableStateFlow(JobUi(answer = savedAnswer(CHECK)))
    val check: StateFlow<JobUi> = _check.asStateFlow()
    private val _profile = MutableStateFlow(JobUi())
    val profile: StateFlow<JobUi> = _profile.asStateFlow()

    private val _libby = MutableStateFlow<Map<String, LibbyUi>>(emptyMap())
    val libby: StateFlow<Map<String, LibbyUi>> = _libby.asStateFlow()

    private val _connected = MutableStateFlow(backend() != null)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Short messages for the snackbar: "Saved", "Imported 55 books"… */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    private val running = mutableMapOf<String, Job>()

    init {
        for (kind in listOf(DISCOVER, CHECK, PROFILE)) {
            jobs.getString("$kind.pending", null)?.let { id -> follow(kind, id) }
        }
    }

    val savedAddress: String get() = secrets.address.orEmpty()

    private fun backend(): BooksBackend? {
        val address = secrets.address ?: return null
        val key = secrets.key ?: return null
        return BooksBackend(address, key)
    }

    private fun flowFor(kind: String) = when (kind) {
        DISCOVER -> _discover
        CHECK -> _check
        else -> _profile
    }

    // ---------- asking Claude ----------

    fun findBooks(mood: String, chips: Set<String>, moreLike: String?) {
        val prompt = Prompts.discover(notebook.value, mood, chips, moreLike)
        jobs.edit().putString("$DISCOVER.first", prompt).apply()
        start(DISCOVER, prompt, null)
    }

    fun refine(adjustment: String) {
        val first = jobs.getString("$DISCOVER.first", null) ?: return
        val last = jobs.getString("$DISCOVER.answer", null) ?: return
        start(DISCOVER, Prompts.refine(first, last, adjustment.trim()), null)
    }

    val canRefine: Boolean get() = jobs.contains("$DISCOVER.first") && jobs.contains("$DISCOVER.answer")

    fun checkBooks(lines: List<String>, photoJpeg: ByteArray?) {
        val prompt = Prompts.check(notebook.value, lines, photoJpeg != null)
        start(CHECK, prompt, photoJpeg?.let { Base64.encodeToString(it, Base64.NO_WRAP) })
    }

    fun describeStyle() = start(PROFILE, Prompts.profile(notebook.value), null)

    fun stop(kind: String) {
        running.remove(kind)?.cancel()
        jobs.edit().remove("$kind.pending").apply()
        flowFor(kind).update { it.copy(running = false, status = "") }
    }

    private fun start(kind: String, prompt: String, image: String?) {
        val api = backend()
        val flow = flowFor(kind)
        if (api == null) {
            flow.update { it.copy(error = "Connect the books service first: tap Settings (top right).") }
            return
        }
        running[kind]?.cancel()
        flow.update { it.copy(running = true, status = "Sending to Claude…", error = null) }
        running[kind] = viewModelScope.launch {
            val id = runCatching { withContext(Dispatchers.IO) { api.submit(kind, prompt, image) } }.getOrElse { error ->
                flow.update { it.copy(running = false, status = "", error = error.message ?: "Couldn't reach the books service.") }
                return@launch
            }
            jobs.edit().putString("$kind.pending", id).apply()
            await(kind, api, id)
        }
    }

    /** Picks a question back up after the app was closed while Claude was answering it. */
    private fun follow(kind: String, id: String) {
        val api = backend() ?: return
        flowFor(kind).update { it.copy(running = true, status = "Waiting for Claude…") }
        running[kind] = viewModelScope.launch { await(kind, api, id) }
    }

    private suspend fun await(kind: String, api: BooksBackend, id: String) {
        val flow = flowFor(kind)
        val started = System.currentTimeMillis()
        var failures = 0
        while (true) {
            val seconds = (System.currentTimeMillis() - started) / 1000
            flow.update {
                it.copy(status = when {
                    seconds < 25 -> "Starting up… this usually takes a minute or two."
                    seconds < 120 -> "Claude is thinking… (${seconds}s)"
                    else -> "Still working… (${seconds / 60} min). You can leave the app and come back."
                })
            }
            delay(if (seconds < 30) 6_000 else 4_000)
            val state = try {
                withContext(Dispatchers.IO) { api.poll(id) }
            } catch (error: java.io.IOException) {
                // A dropped connection is not a failed answer: keep waiting a while.
                if (++failures >= 8) {
                    finish(kind, error = error.message ?: "Lost the connection to the books service.")
                    return
                }
                continue
            } catch (error: org.json.JSONException) {
                finish(kind, error = "The books service sent an answer the app couldn't read.")
                return
            }
            failures = 0
            when (state) {
                is BooksBackend.JobState.Waiting -> if (seconds > 600) {
                    finish(kind, error = "That took too long. Try again.")
                    return
                }
                is BooksBackend.JobState.Failed -> { finish(kind, error = state.message); return }
                is BooksBackend.JobState.Done -> { finish(kind, result = state.result); return }
            }
        }
    }

    private fun finish(kind: String, result: JSONObject? = null, error: String? = null) {
        jobs.edit().remove("$kind.pending").apply()
        running.remove(kind)
        val flow = flowFor(kind)
        if (result == null) {
            flow.update { it.copy(running = false, status = "", error = error) }
            return
        }
        if (kind == PROFILE) {
            val text = result.optString("profile").trim()
            if (text.isEmpty()) {
                flow.update { it.copy(running = false, status = "", error = "Claude's answer came back empty. Try again.") }
            } else {
                store.update { it.copy(profile = text) }
                flow.update { it.copy(running = false, status = "", error = null) }
                _message.value = "Saved. New suggestions will use your style."
            }
            return
        }
        val answer = AnswerParser.parse(result)
        if (answer.books.isEmpty()) {
            flow.update { it.copy(running = false, status = "", error = "Claude's answer came back garbled. Try again.") }
            return
        }
        jobs.edit().putString("$kind.answer", result.toString()).apply()
        flow.update { JobUi(answer = answer) }
    }

    private fun savedAnswer(kind: String): Answer? =
        jobs.getString("$kind.answer", null)?.let { runCatching { AnswerParser.parse(JSONObject(it)) }.getOrNull() }

    // ---------- Libby ----------

    fun libbyFor(title: String, author: String) {
        val key = libbyKey(title, author)
        if (_libby.value[key] != null) return
        _libby.update { it + (key to LibbyUi.Loading) }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { Libby.lookup(title, author) } }
                .fold({ if (it.isEmpty()) LibbyUi.Missing else LibbyUi.Found(it) }, { LibbyUi.Unavailable })
            _libby.update { it + (key to result) }
        }
    }

    fun libbyKey(title: String, author: String) = slug("$title-$author")

    // ---------- the notebook ----------

    fun save(s: Suggestion) = store.update { n ->
        val id = slug("${s.title}-${s.author}")
        if (n.saved.any { it.id == id }) n
        else n.copy(saved = listOf(
            SavedBook(id, s.title, s.author, s.plot, s.why, s.tags, s.grRating, s.grCount, s.grId, Instant.now().toString(), false),
        ) + n.saved)
    }

    fun isSaved(s: Suggestion): Boolean = notebook.value.saved.any { it.id == slug("${s.title}-${s.author}") }

    fun removeSaved(id: String) = store.update { n -> n.copy(saved = n.saved.filterNot { it.id == id }) }

    fun toggleDone(id: String) = store.update { n -> n.copy(saved = n.saved.map { if (it.id == id) it.copy(done = !it.done) else it }) }

    fun reject(s: Suggestion, why: String) = store.update { n ->
        n.copy(notForMe = (listOf(RejectedBook(s.title, s.author, why)) + n.notForMe).take(80))
    }

    fun undoReject(index: Int) = store.update { n -> n.copy(notForMe = n.notForMe.filterIndexed { i, _ -> i != index }) }

    fun saveNote(book: ShelfBook, text: String) = store.update { n ->
        val notes = n.notes.toMutableMap()
        if (text.isBlank()) notes.remove(book.key) else notes[book.key] = text.trim()
        n.copy(notes = notes)
    }

    fun saveTaste(text: String) {
        store.update { it.copy(taste = text.trim()) }
        _message.value = "Saved."
    }

    /** Called with the text of a file she picked: a Goodreads export or a Next Read backup. */
    fun importFile(text: String) {
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r')
        runCatching {
            if (trimmed.startsWith("{")) {
                val incoming = NotebookCodec.decode(trimmed)
                store.update { incoming }
                "Restored ${incoming.books.size} books, ${incoming.notes.size} notes and ${incoming.saved.size} saved books."
            } else {
                val books = GoodreadsCsv.parse(trimmed)
                store.update { GoodreadsCsv.mergeInto(it, books, Instant.now().toString()) }
                "Imported ${books.size} books from Goodreads. Your notes and saved books are unchanged."
            }
        }.fold({ _message.value = it }, { _message.value = it.message ?: "That file couldn't be read." })
    }

    fun exportText(): String = NotebookCodec.encode(notebook.value)

    fun exported() { _message.value = "Backup saved." }

    // ---------- settings ----------

    suspend fun connect(address: String, key: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val clean = BooksBackend.normaliseAddress(address)
            val token = key.trim().ifEmpty { secrets.key.orEmpty() }
            require(token.length >= 32) { "The books key is at least 32 characters." }
            val enabled = BooksBackend(clean, token).ping()
            secrets.address = clean
            secrets.key = token
            _connected.value = true
            if (enabled) null else "Connected, but answering is switched off on the Worker (NEXT_READ_ENABLED)."
        }.getOrElse { it.message ?: "Couldn't connect." }
    }

    fun disconnect() {
        secrets.key = null
        _connected.value = false
    }

    companion object {
        const val DISCOVER = "discover"
        const val CHECK = "check"
        const val PROFILE = "profile"
    }
}
