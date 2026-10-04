package com.laurasheehan.royalmiles.data.coach.chat

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatProtocolTest {

    private val today = LocalDate.of(2026, 9, 26)

    private fun session(
        daysFromToday: Long,
        type: SessionType = SessionType.EASY_RUN,
        completed: Boolean = false,
        skipped: Boolean = false,
    ) = SessionEntity(
        id = daysFromToday + 100,
        eventId = "royal-parks-2026",
        date = today.plusDays(daysFromToday),
        type = type,
        title = "Session $daysFromToday",
        phase = TrainingPhase.BUILD,
        weekNumber = 1,
        targetDistanceKm = 6.0,
        isCompleted = completed,
        isSkipped = skipped,
        actualDistanceKm = if (completed) 6.3 else null,
        sourceActivityId = if (completed) "garmin-1" else null,
    )

    private fun message(role: ChatMessage.Role, text: String) =
        ChatMessage(id = text, role = role, text = text, createdAtMillis = 0)

    @Test
    fun `plan sent to the coach covers four weeks back and six ahead, with status`() {
        val plan = ChatProtocol.planJson(
            listOf(session(-40), session(-2, completed = true), session(-1, skipped = true), session(0), session(43)),
            today,
            "2026-09-26T08:30",
        )
        val sessions = plan.getJSONArray("sessions")
        assertEquals(3, sessions.length())
        assertEquals("done", sessions.getJSONObject(0).getString("status"))
        assertEquals(6.3, sessions.getJSONObject(0).getDouble("actual_km"))
        assertEquals("skipped", sessions.getJSONObject(1).getString("status"))
        assertEquals("planned", sessions.getJSONObject(2).getString("status"))
        assertEquals("EASY_RUN", sessions.getJSONObject(2).getString("type"))
        assertFalse(sessions.getJSONObject(2).has("actual_km"))
    }

    @Test
    fun `figures that may be the plan's are not sent to the coach as what she ran`() {
        val legacy = session(-2, completed = true).copy(sourceActivityId = null, completedAt = LocalDate.of(2026, 9, 20))
        val sent = ChatProtocol.planJson(listOf(legacy), today, "2026-09-26T08:30").getJSONArray("sessions").getJSONObject(0)
        assertEquals("done", sent.getString("status"))
        assertFalse(sent.has("actual_km"))
    }

    @Test
    fun `history carries only the last real turns, not notices or research`() {
        val history = (1..14).map { message(if (it % 2 == 0) ChatMessage.Role.COACH else ChatMessage.Role.USER, "turn $it") } +
            message(ChatMessage.Role.NOTICE, "limit reached") +
            message(ChatMessage.Role.RESEARCH, "web result")
        val json = ChatProtocol.historyJson(history)
        assertEquals(ChatProtocol.HISTORY_TURNS, json.length())
        assertEquals("user", json.getJSONObject(0).getString("role"))
        assertEquals("turn 3", json.getJSONObject(0).getString("text"))
        assertEquals("coach", json.getJSONObject(5).getString("role"))
    }

    @Test
    fun `a coach reply with a valid proposal parses into the same suggestion shape as daily coaching`() {
        val reply = ChatProtocol.parseCoachReply(
            """
            {"reply":{"text":"Take Monday easy.","proposal":{"action":"replace","date":"2026-09-28",
              "headline":"Swap Monday for an easy spin.","reason":"Legs are heavy.",
              "replace_with":{"type":"CYCLE","title":"Easy spin","target_duration_min":30,"target_distance_km":null,"notes":null}},
              "proposal_rejected_reason":null},
             "context":{"provider":"claude","data_date":"2026-09-26","plan_generated_at":"2026-09-26T08:30:00","knowledge":["endurance-nutrition"]},
             "usage":{"chat_enabled":true,"calls_today":2,"calls_month":14,"cost_month_usd":0.24,
              "laptop_connected":true,"caps":{"daily_calls":15,"monthly_calls":150,"monthly_budget_usd":3}}}
            """.trimIndent(),
        )
        assertEquals("Take Monday easy.", reply.text)
        val proposal = reply.proposal!!
        assertEquals(CoachPayload.Coaching.SuggestionAction.REPLACE, proposal.action)
        assertEquals(SessionType.CYCLE, proposal.replaceWith!!.type)
        assertEquals(30, proposal.replaceWith!!.targetDurationMin)
        assertEquals(
            "Claude on your laptop · Garmin data to 2026-09-26 · plan as of 2026-09-26 08:30 · using endurance-nutrition",
            reply.basis,
        )
        assertEquals(2, reply.usage!!.callsToday)
        assertEquals(3.0, reply.usage!!.monthlyBudgetUsd)
        assertTrue(reply.usage!!.laptopConnected)
    }

    @Test
    fun `a proposal the app cannot honour is dropped rather than shown`() {
        val reply = ChatProtocol.parseCoachReply(
            """{"reply":{"text":"ok","proposal":{"action":"replace","date":"2026-09-28","headline":"Race it","reason":"r",
               "replace_with":{"type":"RACE","title":"Race"}}}}""",
        )
        assertNull(reply.proposal)
        assertNull(reply.proposalJson)
    }

    @Test
    fun `research replies keep their citations`() {
        val reply = ChatProtocol.parseResearchReply(
            """{"research":{"text":"Two sessions a week.","citations":["https://a.example","https://b.example"]}}""",
        )
        assertEquals(listOf("https://a.example", "https://b.example"), reply.citations)
        assertNull(reply.basis)
    }

    @Test
    fun `research replies say who searched`() {
        val laptop = ChatProtocol.parseResearchReply("""{"research":{"text":"t","citations":[],"provider":"codex"}}""")
        assertEquals("Searched by Codex on your laptop", laptop.basis)
        val api = ChatProtocol.parseResearchReply("""{"research":{"text":"t","citations":[],"provider":"perplexity"}}""")
        assertEquals("Searched by Perplexity", api.basis)
    }

    @Test
    fun `refusals show the worker's own sentence`() {
        assertEquals(
            "Today's coach limit is reached (15 of 15). No model was called.",
            ChatProtocol.errorMessage("""{"error":"cap_reached","message":"Today's coach limit is reached (15 of 15). No model was called."}""", 429),
        )
        assertTrue(ChatProtocol.errorMessage(null, 401).contains("key"))
    }

    @Test
    fun `the conversation round-trips through storage, proposals included`() {
        val proposalJson = """{"action":"skip","date":"2026-09-30","headline":"Rest instead.","reason":"Tired."}"""
        val messages = listOf(
            message(ChatMessage.Role.USER, "hi"),
            ChatMessage(
                id = "c1",
                role = ChatMessage.Role.COACH,
                text = "Rest Wednesday.",
                createdAtMillis = 5,
                proposal = CoachPayload.suggestionFrom(org.json.JSONObject(proposalJson)),
                proposalJson = proposalJson,
                proposalState = ChatMessage.ProposalState.ACCEPTED,
                basis = "Garmin data to 2026-09-26",
            ),
            ChatMessage(id = "r1", role = ChatMessage.Role.RESEARCH, text = "web", createdAtMillis = 6, citations = listOf("https://x.example")),
        )
        val decoded = ChatStore.decode(ChatStore.encode(messages))
        assertEquals(messages.map { it.id }, decoded.map { it.id })
        assertEquals(ChatMessage.ProposalState.ACCEPTED, decoded[1].proposalState)
        assertEquals(CoachPayload.Coaching.SuggestionAction.SKIP, decoded[1].proposal!!.action)
        assertEquals("Garmin data to 2026-09-26", decoded[1].basis)
        assertNull(decoded[0].basis)
        assertEquals(listOf("https://x.example"), decoded[2].citations)
    }

    @Test
    fun `the chat address is guessed from the coach feed's address`() {
        assertEquals(
            "https://royal-miles-chat.example.workers.dev",
            ChatRepository.suggestedAddress("https://royal-miles-coach.example.workers.dev/coach.json"),
        )
        assertNull(ChatRepository.suggestedAddress("https://coach.example.com/coach.json"))
        assertEquals("https://royal-miles-chat.example.workers.dev", ChatRepository.normaliseAddress(" https://royal-miles-chat.example.workers.dev/ "))
    }

    @Test
    fun `an athlete-file entry is parsed, stored with the message and re-read`() {
        val reply = ChatProtocol.parseCoachReply(
            """{"reply":{"text":"Noted.","proposal":null,"memory":{"section":"health","text":"Foot fine after the 11 km.","reason":"She said so.","expires":null,"replaces":"seed-health-foot","replaces_text":"Foot twingey after runs."}}}""",
        )
        val memory = reply.memory!!
        assertEquals(AthleteSection.HEALTH, memory.section)
        assertEquals("seed-health-foot", memory.replaces)
        assertEquals("Foot twingey after runs.", memory.replacesText)
        assertNull(memory.expires)
        val message = ChatMessage(
            id = "m", role = ChatMessage.Role.COACH, text = "Noted.", createdAtMillis = 0,
            memory = memory, memoryState = ChatMessage.MemoryState.PENDING,
        )
        val decoded = ChatStore.decode(ChatStore.encode(listOf(message))).single()
        assertEquals(memory, decoded.memory)
        assertEquals(ChatMessage.MemoryState.PENDING, decoded.memoryState)
    }

    @Test
    fun `an unknown section is ignored, and an older reply's kind still maps to a section`() {
        val reply = ChatProtocol.parseCoachReply("""{"reply":{"text":"ok","memory":{"section":"diagnosis","text":"x"}}}""")
        assertNull(reply.memory)
        val older = ChatProtocol.parseCoachReply("""{"reply":{"text":"ok","memory":{"kind":"philosophy","text":"Strength on Tuesdays."}}}""")
        assertEquals(AthleteSection.WORKS, older.memory!!.section)
        // A conversation stored by an older build reads back with its entry in the right section.
        val stored = """[{"id":"m","role":"COACH","text":"ok","created_at":0,"proposal_state":"NONE","citations":[],
            "memory":{"kind":"about_me","text":"Busy month.","expires":null},"memory_state":"SAVED","memory_note_id":"n1"}]"""
        assertEquals(AthleteSection.ABOUT, ChatStore.decode(stored).single().memory!!.section)
    }

    @Test
    fun `her athlete file parses with sections, sources and the id just saved`() {
        val file = ChatProtocol.parseAthleteFile(
            """{"notes":[{"id":"seed-block-race","section":"block","text":"Racing Richmond.","expires":null,"source":"seed","created_at":null,"expired":false},
               {"id":"n1","kind":"philosophy","text":"Strength on Tuesdays.","expires":null,"created_at":"2026-09-26T10:00:00Z","expired":false},
               {"id":"n2","section":"threads","text":"Busy month.","expires":"2026-09-20","source":"morning","expired":true},
               {"id":"","section":"about","text":"no id"}],"saved_id":"n2"}""",
        )
        assertEquals(listOf("seed-block-race", "n1", "n2"), file.notes.map { it.id })
        assertEquals(AthleteSection.BLOCK, file.notes[0].section)
        assertEquals("seed", file.notes[0].source)
        assertNull(file.notes[0].createdAt)
        assertEquals(AthleteSection.WORKS, file.notes[1].section)
        assertTrue(file.notes[2].expired)
        assertEquals("morning", file.notes[2].source)
        assertEquals("n2", file.savedId)
    }

    @Test
    fun `her journal parses newest first with tags, skipping broken lines`() {
        val entries = ChatProtocol.parseJournal(
            """{"entries":[{"id":"j2","at":"2026-10-04T15:00:00Z","date":"2026-10-04","kind":"check-in","tags":["long-run"],"text":"Did 11.2 km."},
               {"id":"j1","at":"2026-10-04T10:34:00Z","date":"2026-10-04","kind":"morning","tags":[],"text":"HRV back in range."},
               {"id":"","date":"2026-10-04","text":"no id"},{"id":"j0","date":"bad","text":"bad date"}],"tags":["foot"]}""",
        )
        assertEquals(listOf("j2", "j1"), entries.map { it.id })
        assertEquals(listOf("long-run"), entries[0].tags)
        assertEquals("morning", entries[1].kind)
    }

    @Test
    fun `what is sent when she saves an entry`() {
        val json = ChatProtocol.memoryJson(MemoryProposal(AthleteSection.THREADS, "Busy month.", null, "2026-10-31", replaces = "n9"))
        assertEquals("threads", json.getString("section"))
        assertEquals("2026-10-31", json.getString("expires"))
        assertEquals("n9", json.getString("replaces"))
    }

    private fun msg(id: String, role: ChatMessage.Role, text: String, failedResearch: Boolean = false) =
        ChatMessage(id = id, role = role, text = text, createdAtMillis = 0, failedResearch = failedResearch)

    @Test
    fun `a question answered by a failure notice can be sent again`() {
        val failed = ChatStore.failedQuestion(
            listOf(
                msg("c1", ChatMessage.Role.COACH, "Earlier answer."),
                msg("u2", ChatMessage.Role.USER, "So did I just starve or is it water?"),
                msg("n3", ChatMessage.Role.NOTICE, "Today's coach limit is reached (15 of 15). No model was called."),
            ),
        )
        assertEquals(FailedQuestion("n3", "So did I just starve or is it water?", research = false), failed)
    }

    @Test
    fun `a failed web search is sent again as a web search`() {
        val failed = ChatStore.failedQuestion(
            listOf(
                msg("u1", ChatMessage.Role.USER, "Caffeine and half marathons?"),
                msg("n2", ChatMessage.Role.NOTICE, "Research limit reached.", failedResearch = true),
            ),
        )
        assertTrue(failed!!.research)
    }

    @Test
    fun `only the latest exchange offers Send again`() {
        assertNull(
            ChatStore.failedQuestion(
                listOf(
                    msg("u1", ChatMessage.Role.USER, "q"),
                    msg("n2", ChatMessage.Role.NOTICE, "failed"),
                    msg("u3", ChatMessage.Role.USER, "asked again"),
                    msg("c4", ChatMessage.Role.COACH, "answered"),
                ),
            ),
        )
        // A notice that isn't about her question, e.g. a note that didn't save.
        assertNull(
            ChatStore.failedQuestion(
                listOf(msg("c1", ChatMessage.Role.COACH, "answer"), msg("n2", ChatMessage.Role.NOTICE, "That note wasn't saved.")),
            ),
        )
        assertNull(ChatStore.failedQuestion(emptyList()))
    }

    @Test
    fun `the research flag survives being stored on the phone`() {
        val stored = ChatStore.encode(listOf(msg("n1", ChatMessage.Role.NOTICE, "failed", failedResearch = true)))
        assertTrue(ChatStore.decode(stored).single().failedResearch)
        // Notices saved before this existed read back as chat.
        val older = """[{"id":"n1","role":"NOTICE","text":"failed","created_at":0,"proposal_state":"NONE","citations":[]}]"""
        assertFalse(ChatStore.decode(older).single().failedResearch)
    }

    @Test
    fun `how a run felt and any niggle go to the coach with the plan, only once it's done`() {
        val done = session(-1, type = SessionType.LONG_RUN, completed = true).copy(effortRating = 4, bodyNote = "A twinge")
        val planned = session(2).copy(effortRating = 3, bodyNote = "stale")
        val sent = ChatProtocol.planJson(listOf(done, planned), today, "2026-10-04T18:00").getJSONArray("sessions")
        assertEquals(4, sent.getJSONObject(0).getInt("effort"))
        assertEquals("A twinge", sent.getJSONObject(0).getString("body"))
        assertFalse(sent.getJSONObject(1).has("effort"))
        assertFalse(sent.getJSONObject(1).has("body"))
    }

    @Test
    fun `an agreed replacement is sent as replaced, and the reason travels with it`() {
        val longRun = session(0, type = SessionType.LONG_RUN).copy(isSkipped = true, supersededByCoach = true)
        val comeback = session(0).copy(
            id = 500,
            title = "Comeback run",
            isCustom = true,
            notes = "Was: Long run. Changed to Comeback run because: just recovered from a virus.",
        )
        val plain = session(1).copy(notes = "Generated guidance the coach doesn't need")
        val sent = ChatProtocol.planJson(listOf(longRun, comeback, plain), today, "2026-09-27T15:27").getJSONArray("sessions")
        assertEquals("replaced", sent.getJSONObject(0).getString("status"))
        assertTrue(sent.getJSONObject(1).getString("note").contains("recovered from a virus"))
        assertFalse(sent.getJSONObject(2).has("note"))
    }

    @Test
    fun `a kept note's id survives being stored on the phone`() {
        val kept = message(ChatMessage.Role.COACH, "ok").copy(
            memory = MemoryProposal(AthleteSection.THREADS, "Busy month.", null, null),
            memoryState = ChatMessage.MemoryState.SAVED,
            memoryNoteId = "n42",
        )
        assertEquals("n42", ChatStore.decode(ChatStore.encode(listOf(kept))).single().memoryNoteId)
    }

    @Test
    fun `older conversations go to the coach trimmed, with accepted changes flagged`() {
        val zone = java.time.ZoneOffset.UTC
        fun at(day: Int) = LocalDate.of(2026, 9, day).atTime(9, 0).toInstant(zone).toEpochMilli()
        val suggestion = CoachPayload.suggestionFrom(
            org.json.JSONObject("""{"action":"skip","date":"2026-09-27","headline":"Swap Saturday's long run for a comeback run.","reason":"Coming back from a virus."}"""),
        )!!
        val old = listOf(
            ChatMessage("a", ChatMessage.Role.USER, "Too old to matter.", LocalDate.of(2026, 8, 20).atTime(9, 0).toInstant(zone).toEpochMilli()),
            ChatMessage("b", ChatMessage.Role.USER, "I've had a virus all week. " + "x".repeat(400), at(22)),
            ChatMessage("c", ChatMessage.Role.COACH, "Then Saturday shouldn't stand.", at(22), proposal = suggestion, proposalState = ChatMessage.ProposalState.ACCEPTED),
            ChatMessage("d", ChatMessage.Role.NOTICE, "Limit reached.", at(23)),
        )
        val recent = (1..12).map { ChatMessage("r$it", ChatMessage.Role.USER, "recent $it", at(27)) }
        val earlier = ChatProtocol.earlierJson(old + recent, LocalDate.of(2026, 9, 27), zone)

        assertEquals(2, earlier.length(), "the too-old turn, the notice and the recent twelve are left out")
        val virus = earlier.getJSONObject(0)
        assertEquals("2026-09-22", virus.getString("date"))
        assertTrue(virus.getString("text").length <= 280 && virus.getString("text").endsWith("…"))
        assertEquals(
            "Swap Saturday's long run for a comeback run. Coming back from a virus.",
            earlier.getJSONObject(1).getString("agreed_change"),
        )
    }
}
