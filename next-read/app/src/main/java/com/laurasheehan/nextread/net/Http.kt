package com.laurasheehan.nextread.net

import com.laurasheehan.nextread.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A plain HTTPS request. Identifies itself honestly as this app. */
internal object Http {

    class Response(val code: Int, val body: String)

    fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        token: String? = null,
        timeoutMs: Int = 20_000,
    ): Response {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (error: Exception) {
            throw IOException("That address isn't valid.", error)
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "NextRead/${BuildConfig.VERSION_NAME} (Android)")
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = try {
                connection.responseCode
            } catch (error: IOException) {
                throw IOException("Couldn't connect. Check your internet connection.", error)
            }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return Response(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}
