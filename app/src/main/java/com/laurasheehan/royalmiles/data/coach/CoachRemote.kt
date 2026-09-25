package com.laurasheehan.royalmiles.data.coach

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches coach.json from the Cloudflare Worker that serves the cloud coach's latest live output.
 *
 * The Worker can read one object and nothing else, so the key held here opens coaching only —
 * never the database or the Garmin token. The key is entered on the phone, never built into the
 * app, because this repository and its APKs are public.
 */
internal object CoachRemote {

    private const val TIMEOUT_MS = 15_000

    fun normaliseAddress(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        require(trimmed.startsWith("https://")) { "The coach address must start with https://." }
        return if (trimmed.endsWith("/coach.json")) trimmed else "$trimmed/coach.json"
    }

    /** The activity feed sits next to coach.json on the same Worker, behind the same key. */
    fun activitiesAddress(coachAddress: String): String =
        coachAddress.removeSuffix("/coach.json") + "/activities.json"

    fun failureMessage(code: Int): String = when (code) {
        401, 403 -> "The coach key was rejected. Check it matches the one set on the Worker."
        404 -> "No live coaching has been published yet."
        in 500..599 -> "The coach server had a problem (HTTP $code). Try again later."
        else -> "Unexpected response from the coach server (HTTP $code)."
    }

    fun fetch(address: String, key: String): String {
        val connection = try {
            URL(address).openConnection() as HttpURLConnection
        } catch (error: Exception) {
            throw IOException("That coach address isn't valid.", error)
        }
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Accept", "application/json")

            val code = try {
                connection.responseCode
            } catch (error: IOException) {
                throw IOException("Couldn't reach the coach server. Check your connection.", error)
            }
            if (code != HttpURLConnection.HTTP_OK) throw IOException(failureMessage(code))
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
