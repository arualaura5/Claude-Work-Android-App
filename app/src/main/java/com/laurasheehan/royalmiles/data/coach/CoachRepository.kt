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
 * Holds the coach payload imported from the laptop.
 *
 * SharedPreferences rather than Room, following [com.laurasheehan.royalmiles.data.CelebrationStore]:
 * this is a cached copy of a document the laptop owns, not training data the user created. Losing it
 * costs one re-import from a file that still exists on the laptop — a far better failure than putting
 * a schema migration in front of it. The raw JSON is stored verbatim so a payload written by a newer
 * exporter survives an app downgrade and re-parses cleanly.
 *
 * The picked file's URI is persisted too, so "Refresh" can re-read it without asking for the file
 * again. That works because [android.content.ContentResolver.takePersistableUriPermission] survives
 * reboots — but only for as long as the source app allows, so a failed re-read falls back to asking
 * for the file rather than treating it as an error.
 */
class CoachRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("coach", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<CoachState> = _state.asStateFlow()

    /** Re-reads the remembered file. Null return means there is nothing remembered to re-read. */
    suspend fun refreshFromRememberedSource(): Result<Unit>? {
        val uri = rememberedUri() ?: return null
        return import(uri)
    }

    suspend fun import(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: error("Could not open that file.")

            // Parse before storing: a file that doesn't parse should leave the previous payload
            // intact rather than replacing a working one with something unreadable.
            val payload = CoachPayload.parse(json)

            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            prefs.edit()
                .putString(KEY_JSON, json)
                .putString(KEY_URI, uri.toString())
                .putLong(KEY_IMPORTED_AT, System.currentTimeMillis())
                .apply()

            _state.value = CoachState.Loaded(
                payload = payload,
                importedAtMillis = System.currentTimeMillis(),
                sourceRemembered = true,
            )
        }.recoverCatching { error ->
            throw IllegalArgumentException(
                "That file isn't a coach export. Run export_coach_payload.py on the laptop and pick " +
                    "the coach.json it writes. (${error.message})",
                error,
            )
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = CoachState.Empty
    }

    private fun rememberedUri(): Uri? = prefs.getString(KEY_URI, null)?.let(Uri::parse)

    private fun load(): CoachState {
        val json = prefs.getString(KEY_JSON, null) ?: return CoachState.Empty
        return runCatching {
            CoachState.Loaded(
                payload = CoachPayload.parse(json),
                importedAtMillis = prefs.getLong(KEY_IMPORTED_AT, 0L),
                sourceRemembered = rememberedUri() != null,
            )
        }.getOrElse { CoachState.Empty }
    }

    private companion object {
        const val KEY_JSON = "payload_json"
        const val KEY_URI = "source_uri"
        const val KEY_IMPORTED_AT = "imported_at"
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
