package com.laurasheehan.royalmiles.ui.coach

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import com.laurasheehan.royalmiles.data.coach.chat.ChatMessage
import com.laurasheehan.royalmiles.data.coach.chat.ChatUsage
import com.laurasheehan.royalmiles.ui.theme.RoyalMilesTheme
import org.junit.Rule
import org.junit.Test

/**
 * Renders the Coach tab and coach chat to PNG with sample data, so a change to either screen can be
 * looked at before it ships. Output: app/build/reports/paparazzi/ (and snapshots/ when recording).
 */
class CoachScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    @Test
    fun coachTab() {
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                CoachContent(
                    state = CoachUiState(payload = SamplePayload, sourceRemembered = true, dataAgeDays = 0),
                    onRefresh = {},
                    onConnect = {},
                    onPick = {},
                    onOpenChat = {},
                )
            }
        }
    }

    @Test
    fun chatFirstOpen() {
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                chat(ChatUiState(connected = true, usage = SampleUsage))
            }
        }
    }

    @Test
    fun chatConversation() {
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                chat(ChatUiState(connected = true, usage = SampleUsage, messages = SampleConversation))
            }
        }
    }

    @Test
    fun chatWebResearch() {
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                chat(ChatUiState(connected = true, usage = SampleUsage, messages = SampleResearch, researchMode = true))
            }
        }
    }

    @Test
    fun chatRememberThis() {
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                chat(ChatUiState(connected = true, usage = SampleUsage, messages = SampleRemember))
            }
        }
    }

    @Test
    fun whatYourCoachKnows() {
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                chat(ChatUiState(connected = true, usage = SampleUsage, memoryNotes = SampleNotes), showMemory = true)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun chat(state: ChatUiState, showMemory: Boolean = false) = ChatContent(
        state = state,
        onBack = {},
        onSend = {},
        onToggleResearch = {},
        onAccept = {},
        onDismiss = {},
        onConnect = { _, _ -> },
        onClear = {},
        onOpenLink = {},
        onRemember = { _, _ -> },
        onNotNow = {},
        onLoadMemory = {},
        onForget = {},
        initialShowMemory = showMemory,
    )

    private companion object {
        val SampleSuggestion = CoachPayload.Coaching.Suggestion(
            action = CoachPayload.Coaching.SuggestionAction.REPLACE,
            date = "2026-09-28",
            headline = "Swap Monday's run for an easy 30-minute spin.",
            reason = "Your legs are heavy after the longest run of the block, and resting HR is 2 bpm up.",
            replaceWith = CoachPayload.Coaching.Suggestion.Replacement(
                type = SessionType.CYCLE,
                title = "Easy spin",
                targetDurationMin = 30,
                targetDistanceKm = null,
                notes = null,
            ),
        )

        val SampleUsage = ChatUsage(
            chatEnabled = true,
            researchEnabled = true,
            callsToday = 2,
            callsMonth = 14,
            costMonthUsd = 0.11,
            dailyCap = 15,
            monthlyCap = 150,
            monthlyBudgetUsd = 3.0,
            researchCallsMonth = 1,
            researchMonthlyCap = 20,
            laptopConnected = true,
        )

        val SampleConversation = listOf(
            ChatMessage(
                id = "u1",
                role = ChatMessage.Role.USER,
                text = "My legs felt heavy on the last 3 km today. Should I still run on Monday?",
                createdAtMillis = 0,
            ),
            ChatMessage(
                id = "c1",
                role = ChatMessage.Role.COACH,
                text = "Heavy legs after 16 km is normal, and your HRV is still inside its usual band, so this is " +
                    "fatigue rather than a recovery problem. Resting HR is up 2 bpm, though. I'd make Monday a " +
                    "genuinely easy day: a short spin keeps you moving without more impact.",
                createdAtMillis = 1,
                proposal = SampleSuggestion,
                proposalState = ChatMessage.ProposalState.PENDING,
                basis = "Claude on your laptop · Garmin data to 2026-09-26 · plan as of 2026-09-26 08:30",
            ),
        )

        val SampleRemember = listOf(
            ChatMessage(id = "u3", role = ChatMessage.Role.USER, text = "My left calf always tightens on hilly routes, so I avoid them.", createdAtMillis = 0),
            ChatMessage(
                id = "c3",
                role = ChatMessage.Role.COACH,
                text = "That's worth planning around. Keep hills out of your easy runs for now, and we'll add calf raises " +
                    "to Tuesday's strength so they can come back gradually.",
                createdAtMillis = 1,
                basis = "Claude on your laptop · Garmin data to 2026-09-26",
                memory = com.laurasheehan.royalmiles.data.coach.chat.MemoryProposal(
                    kind = com.laurasheehan.royalmiles.data.coach.chat.MemoryKind.ABOUT_ME,
                    text = "My left calf tightens on hilly routes, so I avoid hills for now.",
                    reason = "She said so, and it affects route and strength choices.",
                    expires = null,
                ),
                memoryState = ChatMessage.MemoryState.PENDING,
            ),
        )

        val SampleNotes = listOf(
            com.laurasheehan.royalmiles.data.coach.chat.MemoryNote("n1", com.laurasheehan.royalmiles.data.coach.chat.MemoryKind.ABOUT_ME, "My left calf tightens on hilly routes, so I avoid hills for now.", null, "2026-09-26T16:40:00Z", false),
            com.laurasheehan.royalmiles.data.coach.chat.MemoryNote("n2", com.laurasheehan.royalmiles.data.coach.chat.MemoryKind.ABOUT_ME, "Busy stretch at work, so my evening runs are short.", "2026-10-15", "2026-09-26T16:42:00Z", false),
            com.laurasheehan.royalmiles.data.coach.chat.MemoryNote("n3", com.laurasheehan.royalmiles.data.coach.chat.MemoryKind.PHILOSOPHY, "Strength works better for me on Tuesdays than Thursdays.", null, "2026-09-26T16:45:00Z", false),
        )

        val SampleResearch = listOf(
            ChatMessage(id = "u2", role = ChatMessage.Role.USER, text = "Does strength training make runners faster?", createdAtMillis = 0),
            ChatMessage(
                id = "r1",
                role = ChatMessage.Role.RESEARCH,
                text = "**Yes, modestly.** Reviews of recreational and trained runners find:\n\n" +
                    "- Heavy and plyometric strength work **twice a week** improves running economy by around 2–8% [1]\n" +
                    "- No loss of endurance when sessions are kept apart from hard runs [1][2]\n" +
                    "- Evidence for *injury prevention* is suggestive but weaker [2]\n\n" +
                    "### In practice\n" +
                    "1. Lift heavy on an easy-run day\n" +
                    "2. Keep 48 h between lifting and intervals",
                createdAtMillis = 1,
                citations = listOf("https://pubmed.ncbi.nlm.nih.gov/example-review", "https://bjsm.bmj.com/example"),
                basis = "Searched by Claude on your laptop",
            ),
        )

        val SamplePayload = CoachPayload(
            schemaVersion = 1,
            generatedAt = "2026-09-26T12:41:00",
            freshness = CoachPayload.Freshness("2026-09-26", "2026-09-26", "2026-09-26", "2026-09-26", "2026-09-26"),
            coverage = CoachPayload.Coverage(windowDays = 30, hrvNights = 28, sleepNights = 29, rhrDays = 30),
            readiness = CoachPayload.Readiness(
                available = true,
                date = "2026-09-26",
                score = 72,
                label = "Steady",
                status = "fair",
                headline = null,
                reason = "HRV close to baseline, sleep a little short.",
                confidence = "high",
                confidenceDetail = null,
                componentCount = 5,
                components = emptyList(),
            ),
            hrv = CoachPayload.HrvMetrics(
                hrv7d = 69.0, hrv30d = 67.0, h0 = 58.0, h07d = 57.0, nightlyAvg = 68.0, nightlyPeak = 96.0,
                rhr = 50.0, trend14d = "stable", trend30d = "improving", highHrvNights = 3, highHrvThresholdMs = 80.0,
            ),
            plan = null,
            warnings = listOf("Resting HR has been 2+ bpm above baseline for 3 days."),
            coaching = CoachPayload.Coaching(
                statusSummary = "HRV 68 ms sits inside your band and the 7-day average is flat. Resting HR is 2 bpm up " +
                    "after two nights under 7 hours, so today's long run should stay genuinely easy. The quality " +
                    "work this week can wait until Wednesday, when sleep should have caught up.",
                onTrack = true,
                actionPoints = listOf(
                    CoachPayload.Coaching.ActionPoint("Keep the long run in Zone 2", "high", "108–135 bpm; don't chase the final 3 km."),
                    CoachPayload.Coaching.ActionPoint("Protect tonight's sleep", "medium", "In bed by 23:00."),
                    CoachPayload.Coaching.ActionPoint("Lift on Tuesday as planned", "medium", "Lower body, away from Wednesday's intervals."),
                ),
                coachNote = null,
                motivation = "Consistency is doing the work.",
                keyReminder = "Easy means 108–135 bpm today.",
                dataDate = "2026-09-26",
                generatedAt = "2026-09-26T12:41:00",
                suggestion = null,
            ),
            coachingAbsentReason = null,
        )
    }
}
