package com.laurasheehan.royalmiles.data.coach

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Holds the coach payload, from one of two sources: the cloud coach (fetched from the Worker that
 * serves its latest live coach.json) or a file picked on the phone.
 *
 * SharedPreferences rather than Room, following [com.laurasheehan.royalmiles.data.CelebrationStore]:
 * this is a cached copy of a document the coach owns, not training data the user created. Losing it
 * costs one refresh — a far better failure than putting a schema migration in front of it. The raw
 * JSON is stored verbatim so a payload written by a newer exporter survives an app downgrade and
 * re-parses cleanly.
 *
 * Whichever source was used last is remembered so "Refresh" can re-read it. For a picked file that
 * relies on [android.content.ContentResolver.takePersistableUriPermission], which survives reboots
 * but only for as long as the source app allows, so a failed re-read asks for the file again.
 */
class CoachRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("coach", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<CoachState> = _state.asStateFlow()

    fun isRemoteConnected(): Boolean = remote() != null

    /** Re-reads the remembered source. Null return means there is nothing remembered to re-read. */
    suspend fun refreshFromRememberedSource(): Result<Unit>? {
        remote()?.let { (address, key) -> return fetchRemote(address, key) }
        val uri = rememberedUri() ?: return null
        return import(uri)
    }

    /** Saves the cloud coach's address and key, but only once they have fetched a valid payload. */
    suspend fun connectRemote(address: String, key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val normalised = CoachRemote.normaliseAddress(address)
            val trimmedKey = key.trim()
            require(trimmedKey.isNotEmpty()) { "Enter the coach key." }
            val json = CoachRemote.fetch(normalised, trimmedKey)
            val payload = parseRemotePayload(json)
            store(
                json = json,
                payload = payload,
                source = prefs.edit()
                    .putString(KEY_REMOTE_ADDRESS, normalised)
                    .putString(KEY_REMOTE_KEY, trimmedKey)
                    .remove(KEY_URI),
                sourceRemembered = true,
            )
        }
    }

    suspend fun import(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = readCoachFile(uri)

            // Parse before storing: a file that doesn't parse should leave the previous payload
            // intact rather than replacing a working one with something unreadable.
            val payload = parseCoachPayload(json)

            val sourceRemembered = runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess

            // Picking a file is a choice of source: the cloud coach would otherwise overwrite it
            // on the next refresh.
            val edit = prefs.edit().remove(KEY_REMOTE_ADDRESS).remove(KEY_REMOTE_KEY)
            if (sourceRemembered) edit.putString(KEY_URI, uri.toString()) else edit.remove(KEY_URI)

            store(json = json, payload = payload, source = edit, sourceRemembered = sourceRemembered)
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = CoachState.Empty
    }

    private suspend fun fetchRemote(address: String, key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = CoachRemote.fetch(address, key)
            store(
                json = json,
                payload = parseRemotePayload(json),
                source = prefs.edit(),
                sourceRemembered = true,
            )
        }
    }

    private fun store(
        json: String,
        payload: CoachPayload,
        source: android.content.SharedPreferences.Editor,
        sourceRemembered: Boolean,
    ) {
        val importedAtMillis = System.currentTimeMillis()
        source
            .putString(KEY_JSON, json)
            .putLong(KEY_IMPORTED_AT, importedAtMillis)
            .apply()
        _state.value = CoachState.Loaded(
            payload = payload,
            importedAtMillis = importedAtMillis,
            sourceRemembered = sourceRemembered,
        )
    }

    private fun remote(): Pair<String, String>? {
        val address = prefs.getString(KEY_REMOTE_ADDRESS, null) ?: return null
        val key = prefs.getString(KEY_REMOTE_KEY, null) ?: return null
        return address to key
    }

    private fun rememberedUri(): Uri? = prefs.getString(KEY_URI, null)?.let(Uri::parse)

    private fun readCoachFile(uri: Uri): String =
        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: error("Content resolver returned no stream.")
        }.getOrElse { error ->
            throw IllegalArgumentException(
                "Couldn't open or read that file. Pick it again, or export a fresh coach.json if " +
                    "it was moved or deleted. (${error.message})",
                error,
            )
        }

    private fun parseCoachPayload(json: String): CoachPayload =
        runCatching {
            CoachPayload.parse(json)
        }.getOrElse { error ->
            throw IllegalArgumentException(
                "That file isn't a coach export. Run export_coach_payload.py on the laptop and pick " +
                    "the coach.json it writes. (${error.message})",
                error,
            )
        }

    private fun parseRemotePayload(json: String): CoachPayload =
        runCatching {
            CoachPayload.parse(json)
        }.getOrElse { error ->
            throw IllegalArgumentException(
                "The coach server sent something that isn't a coach payload. (${error.message})",
                error,
            )
        }

    private fun load(): CoachState {
        val json = prefs.getString(KEY_JSON, null) ?: return CoachState.Empty
        return runCatching {
            CoachState.Loaded(
                payload = CoachPayload.parse(json),
                importedAtMillis = prefs.getLong(KEY_IMPORTED_AT, 0L),
                sourceRemembered = rememberedUri() != null || remote() != null,
            )
        }.getOrElse { CoachState.Empty }
    }

    private companion object {
        const val KEY_JSON = "payload_json"
        const val KEY_URI = "source_uri"
        const val KEY_IMPORTED_AT = "imported_at"
        const val KEY_REMOTE_ADDRESS = "remote_address"
        const val KEY_REMOTE_KEY = "remote_key"
    }
}

sealed interface CoachState {
    data object Empty : CoachState

    data class Loaded(
        val payload: CoachPayload,
        val importedAtMillis: Long,
        val sourceRemembered: Boolean,
    ) : CoachState
}
