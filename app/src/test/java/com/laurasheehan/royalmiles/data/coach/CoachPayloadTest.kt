package com.laurasheehan.royalmiles.data.coach

import com.laurasheehan.royalmiles.core.model.SessionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoachPayloadTest {

    @Test
    fun `payload with no suggestion parses with no suggestion`() {
        val payload = CoachPayload.parse(basePayload(coachingExtra = ""))

        assertNull(payload.coaching?.suggestion)
    }

    @Test
    fun `valid replace suggestion parses all fields`() {
        val payload = CoachPayload.parse(
            basePayload(
                coachingExtra = """
                    ,
                    "suggestion": {
                      "action": "replace",
                      "date": "2026-09-01",
                      "headline": "Let us swap today for an easy 20 minute spin.",
                      "reason": "Symptoms arrived after last night was measured.",
                      "replace_with": {
                        "type": "CYCLE",
                        "title": "Easy spin - illness hold",
                        "target_duration_min": 20,
                        "target_distance_km": 7.5,
                        "notes": "Conversational only."
                      }
                    }
                """.trimIndent(),
            ),
        )

        val suggestion = payload.coaching?.suggestion
        assertEquals(CoachPayload.Coaching.SuggestionAction.REPLACE, suggestion?.action)
        assertEquals("2026-09-01", suggestion?.date)
        assertEquals("Let us swap today for an easy 20 minute spin.", suggestion?.headline)
        assertEquals("Symptoms arrived after last night was measured.", suggestion?.reason)
        assertEquals(SessionType.CYCLE, suggestion?.replaceWith?.type)
        assertEquals("Easy spin - illness hold", suggestion?.replaceWith?.title)
        assertEquals(20, suggestion?.replaceWith?.targetDurationMin)
        assertEquals(7.5, suggestion?.replaceWith?.targetDistanceKm)
        assertEquals("Conversational only.", suggestion?.replaceWith?.notes)
    }

    @Test
    fun `unknown action yields no suggestion`() {
        val payload = CoachPayload.parse(
            basePayload(
                coachingExtra = """
                    ,
                    "suggestion": {
                      "action": "move",
                      "date": "2026-09-01"
                    }
                """.trimIndent(),
            ),
        )

        assertNull(payload.coaching?.suggestion)
    }

    @Test
    fun `unknown replacement type yields no suggestion`() {
        val payload = CoachPayload.parse(
            basePayload(
                coachingExtra = """
                    ,
                    "suggestion": {
                      "action": "replace",
                      "date": "2026-09-01",
                      "headline": "Let us swap today for an easy 20 minute spin.",
                      "replace_with": {
                        "type": "PILATES",
                        "title": "Easy movement"
                      }
                    }
                """.trimIndent(),
            ),
        )

        assertNull(payload.coaching?.suggestion)
    }

    @Test
    fun `replace without replace_with yields no suggestion`() {
        val payload = CoachPayload.parse(
            basePayload(
                coachingExtra = """
                    ,
                    "suggestion": {
                      "action": "replace",
                      "date": "2026-09-01",
                      "headline": "Let us swap today for an easy 20 minute spin.",
                      "reason": "No upside today."
                    }
                """.trimIndent(),
            ),
        )

        assertNull(payload.coaching?.suggestion)
    }

    @Test
    fun `suggestion without a headline yields no suggestion`() {
        // The headline is the coach's own sentence, and the app must not write one for her.
        // A suggestion with nothing to say is not shown at all.
        val payload = CoachPayload.parse(
            basePayload(
                coachingExtra = """
                    ,
                    "suggestion": {
                      "action": "replace",
                      "date": "2026-09-01",
                      "reason": "No upside today.",
                      "replace_with": {
                        "type": "CYCLE",
                        "title": "Easy spin",
                        "target_duration_min": 20
                      }
                    }
                """.trimIndent(),
            ),
        )

        assertNull(payload.coaching?.suggestion)
    }

    private fun basePayload(coachingExtra: String): String =
        """
        {
          "schema_version": 1,
          "generated_at": "2026-09-01T19:58:49",
          "freshness": {
            "db_daily_max_date": "2026-09-01",
            "db_hrv_max_date": "2026-09-01",
            "scored_date": "2026-09-01",
            "hrv_metrics_date": "2026-09-01",
            "coaching_data_date": "2026-09-01"
          },
          "warnings": [],
          "coaching": {
            "status_summary": "Steady.",
            "on_track": true,
            "action_points": [],
            "coach_note": null,
            "motivation": "Keep it easy.",
            "key_reminder": "No run today.",
            "data_date": "2026-09-01",
            "generated_at": "2026-09-01T19:58:43"$coachingExtra
          },
          "coaching_absent_reason": null
        }
        """.trimIndent()
}
