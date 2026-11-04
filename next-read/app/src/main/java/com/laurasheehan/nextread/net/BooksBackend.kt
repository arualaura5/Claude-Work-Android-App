package com.laurasheehan.nextread.net

import java.io.IOException
import org.json.JSONObject

/**
 * The Next Read Worker. The phone hands it a question; the Worker starts a GitHub Action that
 * runs Claude on her own subscription and then looks up Goodreads ratings; the phone checks
 * back until the answer is in. That takes a minute or two, mostly GitHub starting a machine.
 *
 * Routes (projects/next-read/worker.js in ai-workspace):
 *   GET  /books/v1/ping          the key works, and whether answering is switched on
 *   POST /books/v1/jobs          {kind, prompt, image?} → {id}
 *   GET  /books/v1/jobs/{id}     {state: queued|running|done|failed, result?, error?}
 */
class BooksBackend(private val address: String, private val key: String) {

    sealed interface JobState {
        data object Waiting : JobState
        data class Done(val result: JSONObject) : JobState
        data class Failed(val message: String) : JobState
    }

    fun ping(): Boolean {
        val json = call("GET", "/books/v1/ping", null, 15_000)
        return json.optBoolean("enabled")
    }

    fun submit(kind: String, prompt: String, imageBase64: String?): String {
        val body = JSONObject().put("kind", kind).put("prompt", prompt)
        if (imageBase64 != null) body.put("image", imageBase64)
        return call("POST", "/books/v1/jobs", body.toString(), 60_000).getString("id")
    }

    fun poll(id: String): JobState {
        val json = call("GET", "/books/v1/jobs/$id", null, 20_000)
        return when (json.optString("state")) {
            "done" -> json.optJSONObject("result")?.let { JobState.Done(it) }
                ?: JobState.Failed("The answer came back empty. Try again.")
            "failed" -> JobState.Failed(json.optString("error").ifBlank { "Claude couldn't answer that. Try again." })
            else -> JobState.Waiting
        }
    }

    private fun call(method: String, path: String, body: String?, timeoutMs: Int): JSONObject {
        val response = Http.request(address.trimEnd('/') + path, method, body, key, timeoutMs)
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        if (response.code !in 200..299) {
            val message = json?.optString("error")?.takeIf { it.isNotBlank() }
            throw IOException(
                message ?: when (response.code) {
                    401, 403 -> "The books key was rejected. Check it in Settings."
                    404 -> "That isn't the Next Read Worker's address. Check it in Settings."
                    429 -> "Today's limit of questions is used up. Try again tomorrow."
                    else -> "The books service had a problem (HTTP ${response.code}). Try again."
                },
            )
        }
        return json ?: throw IOException("The books service sent an answer the app couldn't read.")
    }

    companion object {
        /** Accepts the Worker's address with or without https:// and a trailing slash. */
        fun normaliseAddress(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            val withScheme = if (trimmed.startsWith("https://")) trimmed else "https://" + trimmed.removePrefix("http://")
            require(withScheme.length > "https://".length && !withScheme.contains(' ')) { "That address isn't valid." }
            return withScheme
        }
    }
}
