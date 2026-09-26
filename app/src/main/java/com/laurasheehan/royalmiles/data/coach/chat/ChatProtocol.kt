package com.laurasheehan.royalmiles.data.coach.chat

import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** One line of the conversation, as kept on the phone. */
data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val createdAtMillis: Long,
    val proposal: CoachPayload.Coaching.Suggestion? = null,
    /** Kept verbatim so a proposal survives app updates and re-parses with the same rules. */
    val proposalJson: String? = null,
    val proposalState: ProposalState = ProposalState.NONE,
    val citations: List<String> = emptyList(),
    /** Which data an answer was based on, so an old answer can be judged by its date. */
    val basis: String? = null,
    /** Something the coach offered to remember; saved only if she taps Save. */
    val memory: MemoryProposal? = null,
    val memoryState: MemoryState = MemoryState.NONE,
) {
    enum class Role { USER, COACH, RESEARCH, NOTICE }
    enum class ProposalState { NONE, PENDING, ACCEPTED, DISMISSED }
    enum class MemoryState { NONE, PENDING, SAVED, DISMISSED }
}

data class MemoryProposal(
    val kind: MemoryKind,
    val text: String,
    val reason: String?,
    /** For temporary things, like a busy month. */
    val expires: String?,
)

enum class MemoryKind(val wire: String, val label: String) {
    ABOUT_ME("about_me", "About you"),
    PHILOSOPHY("philosophy", "Your philosophy");

    companion object {
        fun from(wire: String?): MemoryKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** A note she approved, as stored by the chat Worker. */
data class MemoryNote(
    val id: String,
    val kind: MemoryKind,
    val text: String,
    val expires: String?,
    val createdAt: String?,
    val expired: Boolean,
)

data class ChatUsage(
    val chatEnabled: Boolean,
    val researchEnabled: Boolean,
    val callsToday: Int,
    val callsMonth: Int,
    val costMonthUsd: Double,
    val dailyCap: Int,
    val monthlyCap: Int,
    val monthlyBudgetUsd: Double,
    val researchCallsMonth: Int,
    val researchMonthlyCap: Int,
    /** Her laptop is online, so Claude or Codex answer on her subscriptions before Gemini is used. */
    val laptopConnected: Boolean = false,
)

data class CoachReply(
    val text: String,
    val proposal: CoachPayload.Coaching.Suggestion?,
    val proposalJson: String?,
    val basis: String?,
    val usage: ChatUsage?,
    val memory: MemoryProposal? = null,
)

data class ResearchReply(val text: String, val citations: List<String>, val usage: ChatUsage?)

/** The wire format shared with scripts/cloud/coach_chat_worker.js. */
object ChatProtocol {

    const val HISTORY_TURNS = 6
    private const val PLAN_DAYS_BACK = 28L
    private const val PLAN_DAYS_AHEAD = 42L
    private const val PLAN_MAX_SESSIONS = 80

    fun messageRequest(
        message: String,
        history: List<ChatMessage>,
        sessions: List<SessionEntity>,
        today: LocalDate,
        planGeneratedAt: String,
    ): JSONObject = JSONObject()
        .put("message", message)
        .put("today", today.toString())
        .put("history", historyJson(history))
        .put("plan", planJson(sessions, today, planGeneratedAt))

    fun researchRequest(question: String): JSONObject = JSONObject().put("question", question)

    /** The last few real turns only: notices and research cards are not part of the conversation. */
    fun historyJson(history: List<ChatMessage>): JSONArray {
        val turns = history
            .filter { it.role == ChatMessage.Role.USER || it.role == ChatMessage.Role.COACH }
            .takeLast(HISTORY_TURNS)
        return JSONArray().apply {
            turns.forEach { message ->
                put(
                    JSONObject()
                        .put("role", if (message.role == ChatMessage.Role.USER) "user" else "coach")
                        .put("text", message.text),
                )
            }
        }
    }

    /** Four weeks back and six ahead: enough for the coach to see the rhythm, never the whole log. */
    fun planJson(sessions: List<SessionEntity>, today: LocalDate, generatedAt: String): JSONObject {
        val window = sessions
            .filter { !it.date.isBefore(today.minusDays(PLAN_DAYS_BACK)) && !it.date.isAfter(today.plusDays(PLAN_DAYS_AHEAD)) }
            .sortedBy { it.date }
            .let { inWindow ->
                if (inWindow.size <= PLAN_MAX_SESSIONS) inWindow
                else inWindow.sortedBy { kotlin.math.abs(it.date.toEpochDay() - today.toEpochDay()) }
                    .take(PLAN_MAX_SESSIONS)
                    .sortedBy { it.date }
            }
        val array = JSONArray()
        window.forEach { session ->
            array.put(
                JSONObject()
                    .put("date", session.date.toString())
                    .put("type", session.type.name)
                    .put("title", session.title)
                    .put(
                        "status",
                        when {
                            session.isCompleted -> "done"
                            session.isSkipped -> "skipped"
                            else -> "planned"
                        },
                    )
                    .putOpt("target_km", session.targetDistanceKm)
                    .putOpt("target_min", session.targetDurationMin)
                    .putOpt("actual_km", session.actualDistanceKm)
                    .putOpt("actual_min", session.actualDurationMin),
            )
        }
        return JSONObject().put("generated_at", generatedAt).put("sessions", array)
    }

    fun parseCoachReply(json: String): CoachReply {
        val root = JSONObject(json)
        val reply = root.getJSONObject("reply")
        val proposalObject = reply.optJSONObject("proposal")
        val proposal = proposalObject?.let(CoachPayload::suggestionFrom)
        val context = root.optJSONObject("context")
        return CoachReply(
            text = reply.optString("text", "").trim(),
            proposal = proposal,
            proposalJson = proposalObject?.takeIf { proposal != null }?.toString(),
            basis = context?.let(::basisLine),
            usage = root.optJSONObject("usage")?.let(::parseUsage),
            memory = reply.optJSONObject("memory")?.let(::parseMemoryProposal),
        )
    }

    fun parseMemoryProposal(json: JSONObject): MemoryProposal? {
        val kind = MemoryKind.from(json.optString("kind")) ?: return null
        val text = json.optString("text", "").trim().takeIf { it.isNotEmpty() } ?: return null
        return MemoryProposal(
            kind = kind,
            text = text,
            reason = json.optString("reason", "").trim().takeIf { it.isNotEmpty() && it != "null" },
            expires = json.optString("expires", "").takeIf { !json.isNull("expires") && it.isNotBlank() },
        )
    }

    fun memoryJson(proposal: MemoryProposal): JSONObject = JSONObject()
        .put("kind", proposal.kind.wire)
        .put("text", proposal.text)
        .putOpt("reason", proposal.reason)
        .put("expires", proposal.expires ?: JSONObject.NULL)

    fun parseNotes(json: String): List<MemoryNote> {
        val array = JSONObject(json).optJSONArray("notes") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val note = array.optJSONObject(index) ?: return@mapNotNull null
            MemoryNote(
                id = note.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                kind = MemoryKind.from(note.optString("kind")) ?: return@mapNotNull null,
                text = note.optString("text"),
                expires = note.optString("expires", "").takeIf { !note.isNull("expires") && it.isNotBlank() },
                createdAt = note.optString("created_at", "").takeIf { it.isNotBlank() },
                expired = note.optBoolean("expired", false),
            )
        }
    }

    fun parseResearchReply(json: String): ResearchReply {
        val root = JSONObject(json)
        val research = root.getJSONObject("research")
        val citations = research.optJSONArray("citations")
        return ResearchReply(
            text = research.optString("text", "").trim(),
            citations = citations?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }
                .orEmpty(),
            usage = root.optJSONObject("usage")?.let(::parseUsage),
        )
    }

    fun parseUsage(json: JSONObject): ChatUsage {
        val caps = json.optJSONObject("caps") ?: JSONObject()
        return ChatUsage(
            chatEnabled = json.optBoolean("chat_enabled", false),
            researchEnabled = json.optBoolean("research_enabled", false),
            callsToday = json.optInt("calls_today", 0),
            callsMonth = json.optInt("calls_month", 0),
            costMonthUsd = json.optDouble("cost_month_usd", 0.0),
            dailyCap = caps.optInt("daily_calls", 0),
            monthlyCap = caps.optInt("monthly_calls", 0),
            monthlyBudgetUsd = caps.optDouble("monthly_budget_usd", 0.0),
            researchCallsMonth = json.optInt("research_calls_month", 0),
            researchMonthlyCap = caps.optInt("research_monthly_calls", 0),
            laptopConnected = json.optBoolean("laptop_connected", false),
        )
    }

    /** The Worker's own sentence for a refusal or failure, or a plain fallback. */
    fun errorMessage(json: String?, status: Int): String {
        val message = runCatching { JSONObject(json ?: "").optString("message", "") }.getOrDefault("")
        if (message.isNotBlank()) return message
        return when (status) {
            401, 403 -> "The chat key was rejected. Check it matches the one set for the chat Worker."
            404 -> "That address doesn't look like the coach chat."
            429 -> "The coach's spending limit has been reached. No model was called."
            else -> "The coach couldn't answer that time (HTTP $status)."
        }
    }

    fun providerLabel(provider: String): String? = when (provider) {
        "claude" -> "Claude on your laptop"
        "codex" -> "Codex on your laptop"
        "gemini" -> "Gemini"
        else -> null
    }

    private fun basisLine(context: JSONObject): String? {
        val dataDate = context.optString("data_date", "").takeIf { it.isNotBlank() && it != "null" }
        val planAt = context.optString("plan_generated_at", "").takeIf { it.isNotBlank() && it != "null" }
        val knowledge = context.optJSONArray("knowledge")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        val parts = listOfNotNull(
            providerLabel(context.optString("provider", "")),
            dataDate?.let { "Garmin data to $it" },
            planAt?.let { "plan as of ${it.take(16).replace('T', ' ')}" },
            knowledge.takeIf { it.isNotEmpty() }?.let { "using ${it.joinToString()}" },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
