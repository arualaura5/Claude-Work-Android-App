package com.laurasheehan.royalmiles.data.coach.chat

import android.content.Context
import android.content.SharedPreferences
import com.laurasheehan.royalmiles.data.SecretStore
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The conversation with the cloud coach. It lives on this phone only: the chat Worker keeps
 * usage counters, never the conversation, and each message carries the last few turns with it.
 * The address and key are entered on the phone, never built in, because the app repo is public.
 */
class ChatRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("coach_chat", Context.MODE_PRIVATE)

    /** The chat key; an older build's plain-text copy moves in on first use. */
    private val secrets by lazy {
        SecretStore(context.applicationContext).also { it.adopt(prefs, KEY_TOKEN, SecretStore.COACH_CHAT_KEY) }
    }

    private val _messages = MutableStateFlow(loadMessages())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun connection(): Pair<String, String>? {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        val key = secrets.get(SecretStore.COACH_CHAT_KEY) ?: return null
        return address to key
    }

    fun isConnected(): Boolean = connection() != null

    /** Saved only once the Worker has accepted the key, so a typo can't leave a broken connection. */
    suspend fun connect(address: String, key: String): Result<ChatUsage> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normaliseAddress(address)
            val token = key.trim()
            require(token.length >= 32) { "The chat key is at least 32 characters." }
            val usage = ChatProtocol.parseUsage(JSONObject(request(base, token, "GET", "/chat/v1/usage", null)))
            secrets.put(SecretStore.COACH_CHAT_KEY, token)
            prefs.edit().putString(KEY_ADDRESS, base).remove(KEY_TOKEN).apply()
            usage
        }
    }

    suspend fun usage(): Result<ChatUsage> = withContext(Dispatchers.IO) {
        runCatching {
            val (base, token) = connection() ?: error("Connect the coach chat first.")
            ChatProtocol.parseUsage(JSONObject(request(base, token, "GET", "/chat/v1/usage", null)))
        }
    }

    suspend fun ask(message: String, sessions: List<SessionEntity>, today: LocalDate): Result<CoachReply> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (base, token) = connection() ?: error("Connect the coach chat first.")
                val history = _messages.value
                append(newMessage(ChatMessage.Role.USER, message))
                val body = ChatProtocol.messageRequest(message, history, sessions, today, LocalDateTime.now().toString())
                val reply = ChatProtocol.parseCoachReply(request(base, token, "POST", "/chat/v1/messages", body))
                append(
                    newMessage(ChatMessage.Role.COACH, reply.text).copy(
                        proposal = reply.proposal,
                        proposalJson = reply.proposalJson,
                        proposalState = if (reply.proposal != null) ChatMessage.ProposalState.PENDING else ChatMessage.ProposalState.NONE,
                        basis = reply.basis,
                        memory = reply.memory,
                        memoryState = if (reply.memory != null) ChatMessage.MemoryState.PENDING else ChatMessage.MemoryState.NONE,
                    ),
                )
                reply
            }.onFailure { append(newMessage(ChatMessage.Role.NOTICE, it.message ?: "The coach couldn't answer.")) }
        }

    suspend fun research(question: String): Result<ResearchReply> = withContext(Dispatchers.IO) {
        runCatching {
            val (base, token) = connection() ?: error("Connect the coach chat first.")
            append(newMessage(ChatMessage.Role.USER, question))
            val reply = ChatProtocol.parseResearchReply(
                request(base, token, "POST", "/chat/v1/research", ChatProtocol.researchRequest(question)),
            )
            append(newMessage(ChatMessage.Role.RESEARCH, reply.text).copy(citations = reply.citations, basis = reply.basis))
            reply
        }.onFailure {
            append(newMessage(ChatMessage.Role.NOTICE, it.message ?: "The research didn't come back.").copy(failedResearch = true))
        }
    }

    fun setProposalState(messageId: String, state: ChatMessage.ProposalState) {
        _messages.value = _messages.value.map { if (it.id == messageId) it.copy(proposalState = state) else it }
        save()
    }

    fun addNotice(text: String) = append(newMessage(ChatMessage.Role.NOTICE, text))

    /** Saves a note she approved (possibly after editing it). Nothing is remembered without this. */
    suspend fun remember(proposal: MemoryProposal, messageId: String?): Result<List<MemoryNote>> = withContext(Dispatchers.IO) {
        runCatching {
            val (base, token) = connection() ?: error("Connect the coach chat first.")
            val notes = ChatProtocol.parseNotes(
                request(base, token, "POST", "/chat/v1/memory", ChatProtocol.memoryJson(proposal).put("source", "chat")),
            )
            if (messageId != null) setMemoryState(messageId, ChatMessage.MemoryState.SAVED, proposal)
            notes
        }
    }

    suspend fun memoryNotes(): Result<List<MemoryNote>> = withContext(Dispatchers.IO) {
        runCatching {
            val (base, token) = connection() ?: error("Connect the coach chat first.")
            ChatProtocol.parseNotes(request(base, token, "GET", "/chat/v1/memory", null))
        }
    }

    suspend fun forget(id: String): Result<List<MemoryNote>> = withContext(Dispatchers.IO) {
        runCatching {
            val (base, token) = connection() ?: error("Connect the coach chat first.")
            ChatProtocol.parseNotes(request(base, token, "POST", "/chat/v1/memory/delete", JSONObject().put("id", id)))
        }
    }

    fun setMemoryState(messageId: String, state: ChatMessage.MemoryState, memory: MemoryProposal? = null) {
        _messages.value = _messages.value.map {
            if (it.id == messageId) it.copy(memoryState = state, memory = memory ?: it.memory) else it
        }
        save()
    }

    /**
     * The question behind the last message, when that message says it failed: her question and
     * the notice are taken out of the thread so Send again doesn't leave a duplicate behind.
     */
    fun takeFailed(noticeId: String): FailedQuestion? {
        val failed = ChatStore.failedQuestion(_messages.value) ?: return null
        if (failed.noticeId != noticeId) return null
        _messages.value = _messages.value.dropLast(2)
        save()
        return failed
    }

    fun clearConversation() {
        _messages.value = emptyList()
        save()
    }

    private fun append(message: ChatMessage) {
        _messages.value = (_messages.value + message).takeLast(MAX_MESSAGES)
        save()
    }

    private fun newMessage(role: ChatMessage.Role, text: String) = ChatMessage(
        id = UUID.randomUUID().toString(),
        role = role,
        text = text,
        createdAtMillis = System.currentTimeMillis(),
    )

    private fun save() {
        prefs.edit().putString(KEY_MESSAGES, ChatStore.encode(_messages.value)).apply()
    }

    private fun loadMessages(): List<ChatMessage> =
        runCatching { ChatStore.decode(prefs.getString(KEY_MESSAGES, null)) }.getOrDefault(emptyList())

    private fun request(base: String, token: String, method: String, path: String, body: JSONObject?): String {
        val connection = try {
            URL(base + path).openConnection() as HttpURLConnection
        } catch (error: Exception) {
            throw IOException("That chat address isn't valid.", error)
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            // Her laptop gets up to ~110 s (Claude, then Codex) before the Worker falls back to
            // Gemini, so the phone waits a little longer than that.
            connection.readTimeout = 170_000
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = try {
                connection.responseCode
            } catch (error: IOException) {
                throw IOException("Couldn't reach the coach chat. Check your connection.", error)
            }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }
            if (status !in 200..299) throw IOException(ChatProtocol.errorMessage(text, status))
            return text ?: throw IOException("The coach chat sent an empty answer.")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val KEY_ADDRESS = "address"
        /** Where builds before the secret store kept the key in plain text; read once to move it. */
        private const val KEY_TOKEN = "token"
        private const val KEY_MESSAGES = "messages"
        private const val MAX_MESSAGES = 80

        fun normaliseAddress(input: String): String {
            val trimmed = input.trim().trimEnd('/').removeSuffix("/chat/v1/messages").removeSuffix("/chat/v1/usage")
            require(trimmed.startsWith("https://")) { "The chat address must start with https://." }
            return trimmed
        }

        /** The chat Worker sits beside the coach feed Worker, so its address can be guessed from it. */
        fun suggestedAddress(coachAddress: String?): String? {
            val host = coachAddress?.removePrefix("https://")?.substringBefore('/') ?: return null
            val rest = host.substringAfter('.', "")
            return if (rest.endsWith("workers.dev")) "https://royal-miles-chat.$rest" else null
        }
    }
}

/** A question that got a failure notice instead of an answer. */
data class FailedQuestion(val noticeId: String, val text: String, val research: Boolean)

/** Plain JSON so the stored conversation needs no schema migration. */
internal object ChatStore {
    /** Only the latest exchange: her question, then a notice in place of the answer. */
    fun failedQuestion(messages: List<ChatMessage>): FailedQuestion? {
        val notice = messages.lastOrNull()?.takeIf { it.role == ChatMessage.Role.NOTICE } ?: return null
        val question = messages.getOrNull(messages.size - 2)?.takeIf { it.role == ChatMessage.Role.USER } ?: return null
        return FailedQuestion(notice.id, question.text, notice.failedResearch)
    }

    fun encode(messages: List<ChatMessage>): String = JSONArray().apply {
        messages.forEach { message ->
            put(
                JSONObject()
                    .put("id", message.id)
                    .put("role", message.role.name)
                    .put("text", message.text)
                    .put("created_at", message.createdAtMillis)
                    .putOpt("proposal", message.proposalJson)
                    .put("proposal_state", message.proposalState.name)
                    .put("citations", JSONArray(message.citations))
                    .putOpt("basis", message.basis)
                    .putOpt("memory", message.memory?.let(ChatProtocol::memoryJson))
                    .put("memory_state", message.memoryState.name)
                    .put("failed_research", message.failedResearch),
            )
        }
    }.toString()

    fun decode(json: String?): List<ChatMessage> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val role = runCatching { ChatMessage.Role.valueOf(item.optString("role")) }.getOrNull() ?: return@mapNotNull null
            val proposalJson = item.optString("proposal", "").takeIf { it.isNotBlank() && !item.isNull("proposal") }
            val citations = item.optJSONArray("citations")
            ChatMessage(
                id = item.optString("id"),
                role = role,
                text = item.optString("text"),
                createdAtMillis = item.optLong("created_at"),
                proposal = proposalJson?.let { runCatching { CoachPayload.suggestionFrom(JSONObject(it)) }.getOrNull() },
                proposalJson = proposalJson,
                proposalState = runCatching { ChatMessage.ProposalState.valueOf(item.optString("proposal_state")) }
                    .getOrDefault(ChatMessage.ProposalState.NONE),
                citations = citations?.let { list -> (0 until list.length()).map { list.optString(it) } }.orEmpty(),
                basis = item.optString("basis", "").takeIf { it.isNotBlank() && !item.isNull("basis") },
                memory = item.optJSONObject("memory")?.let(ChatProtocol::parseMemoryProposal),
                memoryState = runCatching { ChatMessage.MemoryState.valueOf(item.optString("memory_state")) }
                    .getOrDefault(ChatMessage.MemoryState.NONE),
                failedResearch = item.optBoolean("failed_research", false),
            )
        }
    }
}
