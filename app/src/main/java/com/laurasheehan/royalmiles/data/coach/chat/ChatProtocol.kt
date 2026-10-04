package com.laurasheehan.royalmiles.data.coach.chat

import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    /** An entry the coach offered for her athlete file; kept only if she taps Save. */
    val memory: MemoryProposal? = null,
    val memoryState: MemoryState = MemoryState.NONE,
    /** The saved entry, once she kept it; Undo removes it, or puts back the one it updated. */
    val memoryNoteId: String? = null,
    /** On a failure notice: the question was web research, so Send again searches again. */
    val failedResearch: Boolean = false,
) {
    enum class Role { USER, COACH, RESEARCH, NOTICE }
    enum class ProposalState { NONE, PENDING, ACCEPTED, DISMISSED }
    enum class MemoryState { NONE, PENDING, SAVED, DISMISSED }
}

/** An entry the coach offers for her athlete file, or one it updates ([replaces]). */
data class MemoryProposal(
    val section: AthleteSection,
    val text: String,
    val reason: String?,
    /** For temporary things, like a busy month. */
    val expires: String?,
    /** The entry this updates, so the file stays current instead of collecting near-duplicates. */
    val replaces: String? = null,
    /** That entry's words, shown on the card so she can see what changes. */
    val replacesText: String? = null,
)

/** The sections of her athlete file, as the chat Worker names them. */
enum class AthleteSection(val wire: String, val label: String) {
    ABOUT("about", "Who you are"),
    BLOCK("block", "This block and your goals"),
    WORKS("works", "What works for you"),
    HEALTH("health", "Health and body"),
    EXPECTATIONS("expectations", "What you agreed"),
    THREADS("threads", "Open threads");

    companion object {
        /** Entries saved before sections existed carry `about_me` or `philosophy`. */
        fun from(wire: String?, legacyKind: String? = null): AthleteSection? =
            entries.firstOrNull { it.wire == wire }
                ?: when (legacyKind) {
                    "about_me" -> ABOUT
                    "philosophy" -> WORKS
                    else -> null
                }
    }
}

/** An entry in her athlete file, as stored by the chat Worker. */
data class MemoryNote(
    val id: String,
    val section: AthleteSection,
    val text: String,
    val expires: String?,
    val createdAt: String?,
    val expired: Boolean,
    /** Where it came from: "seed" (her starting file), "chat", or "morning" (the morning coach). */
    val source: String? = null,
)

/** One line of her journal: something that happened, dated, timestamped and tagged. */
data class JournalEntry(
    val id: String,
    /** ISO instant it was written. */
    val at: String,
    val date: String,
    /** chat, session, check-in, plan change or morning. */
    val kind: String,
    val tags: List<String>,
    val text: String,
)

/** Her athlete file as the Worker returns it, and the id of an entry just saved. */
data class AthleteFile(val notes: List<MemoryNote>, val savedId: String?)

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

data class ResearchReply(
    val text: String,
    val citations: List<String>,
    val usage: ChatUsage?,
    /** Who searched, e.g. "Searched by Claude on your laptop". */
    val basis: String? = null,
)

/** The wire format shared with scripts/cloud/coach_chat_worker.js. */
object ChatProtocol {

    const val HISTORY_TURNS = 12

    /** Older turns go too, trimmed: the coach's memory of what it and Laura talked about and agreed. */
    private const val EARLIER_DAYS = 30L
    private const val EARLIER_TEXT_CHARS = 280
    private const val EARLIER_TOTAL_CHARS = 9_000
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
        .put("earlier", earlierJson(history, today))
        .put("plan", planJson(sessions, today, planGeneratedAt))

    fun researchRequest(question: String): JSONObject = JSONObject().put("question", question)

    /**
     * Everything older than the recent turns, from the last 30 days, each trimmed, newest kept first
     * if it runs long. A change she accepted travels with the message that proposed it, so the
     * coach can't forget what they agreed.
     */
    fun earlierJson(history: List<ChatMessage>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): JSONArray {
        val turns = history.filter { it.role == ChatMessage.Role.USER || it.role == ChatMessage.Role.COACH }
        val older = turns.dropLast(HISTORY_TURNS)
        val since = today.minusDays(EARLIER_DAYS)
        val kept = mutableListOf<JSONObject>()
        var used = 0
        for (message in older.asReversed()) {
            val date = Instant.ofEpochMilli(message.createdAtMillis).atZone(zone).toLocalDate()
            if (date.isBefore(since)) break
            val text = message.text.trim().let { if (it.length > EARLIER_TEXT_CHARS) it.take(EARLIER_TEXT_CHARS - 1).trimEnd() + "…" else it }
            val agreed = message.proposal
                ?.takeIf { message.proposalState == ChatMessage.ProposalState.ACCEPTED }
                ?.let { proposal -> listOfNotNull(proposal.headline, proposal.reason).joinToString(" ") }
            used += text.length + (agreed?.length ?: 0) + 30
            if (used > EARLIER_TOTAL_CHARS) break
            kept += JSONObject()
                .put("date", date.toString())
                .put("role", if (message.role == ChatMessage.Role.USER) "user" else "coach")
                .put("text", text)
                .putOpt("agreed_change", agreed)
        }
        return JSONArray(kept.asReversed())
    }

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
                            // A change she agreed with her coach, not a miss.
                            session.supersededByCoach -> "replaced"
                            session.isSkipped -> "skipped"
                            else -> "planned"
                        },
                    )
                    // Why a session was changed or added ("Was: Long run. Changed to ... because:
                    // just recovered from a virus"), so the coach reads the day as agreed.
                    .putOpt("note", session.notes.takeIf { (session.isCustom || session.supersededByCoach) && it.isNotBlank() }?.take(240))
                    .putOpt("target_km", session.targetDistanceKm)
                    .putOpt("target_min", session.targetDurationMin)
                    // Recorded or entered figures only: possibly-planned ones would read to the coach
                    // as what she ran.
                    .putOpt("actual_km", session.knownDistanceKm)
                    .putOpt("actual_min", session.knownDurationMin)
                    // Her own report after the session: effort out of 5, and any niggle.
                    .putOpt("effort", session.effortRating?.takeIf { session.isCompleted })
                    .putOpt("body", session.bodyNote?.takeIf { session.isCompleted && it.isNotBlank() }),
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
        val section = AthleteSection.from(json.optString("section"), json.optString("kind")) ?: return null
        val text = json.optString("text", "").trim().takeIf { it.isNotEmpty() } ?: return null
        return MemoryProposal(
            section = section,
            text = text,
            reason = json.optString("reason", "").trim().takeIf { it.isNotEmpty() && it != "null" },
            expires = json.optString("expires", "").takeIf { !json.isNull("expires") && it.isNotBlank() },
            replaces = json.optString("replaces", "").takeIf { !json.isNull("replaces") && it.isNotBlank() },
            replacesText = json.optString("replaces_text", "").takeIf { !json.isNull("replaces_text") && it.isNotBlank() },
        )
    }

    fun memoryJson(proposal: MemoryProposal): JSONObject = JSONObject()
        .put("section", proposal.section.wire)
        .put("text", proposal.text)
        .putOpt("reason", proposal.reason)
        .put("expires", proposal.expires ?: JSONObject.NULL)
        .put("replaces", proposal.replaces ?: JSONObject.NULL)
        .putOpt("replaces_text", proposal.replacesText)

    fun parseAthleteFile(json: String): AthleteFile {
        val root = JSONObject(json)
        return AthleteFile(
            notes = parseNotes(json),
            savedId = root.optString("saved_id", "").takeIf { it.isNotBlank() && !root.isNull("saved_id") },
        )
    }

    fun parseJournal(json: String): List<JournalEntry> {
        val array = JSONObject(json).optJSONArray("entries") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val tags = entry.optJSONArray("tags")
            JournalEntry(
                id = entry.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                at = entry.optString("at"),
                date = entry.optString("date").takeIf { it.length == 10 } ?: return@mapNotNull null,
                kind = entry.optString("kind", "note"),
                tags = tags?.let { list -> (0 until list.length()).mapNotNull { list.optString(it).takeIf(String::isNotBlank) } }.orEmpty(),
                text = entry.optString("text").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
            )
        }
    }

    fun parseNotes(json: String): List<MemoryNote> {
        val array = JSONObject(json).optJSONArray("notes") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val note = array.optJSONObject(index) ?: return@mapNotNull null
            MemoryNote(
                id = note.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                section = AthleteSection.from(note.optString("section"), note.optString("kind")) ?: return@mapNotNull null,
                text = note.optString("text"),
                expires = note.optString("expires", "").takeIf { !note.isNull("expires") && it.isNotBlank() },
                createdAt = note.optString("created_at", "").takeIf { it.isNotBlank() && !note.isNull("created_at") },
                expired = note.optBoolean("expired", false),
                source = note.optString("source", "").takeIf { it.isNotBlank() && !note.isNull("source") },
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
            basis = researcherLabel(research.optString("provider", "")),
        )
    }

    fun researcherLabel(provider: String): String? = when (provider) {
        "claude" -> "Searched by Claude on your laptop"
        "codex" -> "Searched by Codex on your laptop"
        "perplexity" -> "Searched by Perplexity"
        else -> null
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
