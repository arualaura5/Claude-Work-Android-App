package com.laurasheehan.royalmiles.ui.coach

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Laura's real Garmin numbers, 4 Sep to 3 Oct 2026, in the shape coach.py publishes. Coach wording is sample text. */
internal object WellbeingSamples {
    val today: LocalDate = LocalDate.of(2026, 10, 3)

    private val hrv = listOf(67, 79, 69, 57, 71, 65, 62, 41, 61, 66, 70, 63, 70, 74, 74, 73, 78, 81, 74, 69, 72, 70, 72, 68, 80, 73, 67, 62, 69, 70)
    private val rhr = listOf(52, 48, 50, 50, 50, 52, 53, 57, 54, 48, 48, 51, 50, 47, 45, 48, 44, 45, 46, 51, 49, 47, 46, 51, 49, 46, 48, 50, 48, 47)
    private val sleep = listOf(
        9.52, 7.97, 6.67, 8.51, 8.03, 7.56, 6.84, 7.4, 4.55, 7.43, 8.02, 8.08, 7.82, 7.78, 7.58,
        9.87, 9.35, 8.2, 7.0, 7.38, 8.99, 8.77, 9.13, 7.51, 5.73, 9.38, 7.83, 9.28, 6.98, 9.37,
    )
    // The last 14 nights; earlier nights had no times in the sample.
    private val nights = listOf(
        "23:23" to "08:44", "23:23" to "07:31", "23:19" to "07:00", "00:07" to "07:30", "00:29" to "07:23",
        "21:55" to "07:34", "22:48" to "09:08", "00:25" to "07:25", "23:30" to "05:42", "23:58" to "07:39",
        "22:16" to "07:29", "23:39" to "07:37", "22:20" to "06:59", "00:12" to "09:34",
    )
    private val minHr = listOf(44, 44, 44, 47, 43, 46, 42, 50, 43, 43, 47, 49, 40, 46)
    private val stress = mapOf(29 to 16, 28 to 23, 27 to 31, 26 to 27)

    fun payloadJson(
        brief: Boolean = true,
        sessionCheck: String? = """{"date":"2026-10-04","verdict":"as_planned","reason":"HRV and resting HR are both normal for you and you slept long. Fine for 10 km easy."}""",
        suggestion: String? = null,
    ): String {
        val days = JSONArray()
        for (i in 0 until 30) {
            val night = (i - 16).takeIf { it >= 0 }
            days.put(
                JSONObject()
                    .put("date", today.minusDays((29 - i).toLong()).toString())
                    .put("hrv_ms", hrv[i])
                    .put("rhr", rhr[i])
                    .put("min_hr", night?.let { minHr[it] } ?: JSONObject.NULL)
                    .put("sleep_h", sleep[i])
                    .put("bed", night?.let { nights[it].first } ?: JSONObject.NULL)
                    .put("wake", night?.let { nights[it].second } ?: JSONObject.NULL)
                    .put("stress", stress[i] ?: JSONObject.NULL)
                    .put("steps", if (i == 28) 3193 else 7400),
            )
        }
        val coaching = JSONObject(
            """
            {"status_summary":"HRV 7-day average 70 ms, back inside your usual range after the dip around 11 September.",
             "on_track":true,
             "action_points":[
               {"title":"Long run, easy","priority":"high","body":"Keep tomorrow under 157 bpm and notice how the foot feels at 5 km."},
               {"title":"Bedtime","priority":"medium","body":"Bedtimes are moving around by two hours. Aim for lights out by 23:30 tonight."},
               {"title":"Keep the long sleeps","priority":"low","body":"Two of the last three nights were over nine hours, and it shows in your HRV."}],
             "motivation":"Recovery has come back properly. Enjoy tomorrow.",
             "key_reminder":"Tomorrow's 10 km long run looks good as planned. Keep it conversational."}
            """,
        )
        if (brief) {
            coaching.put(
                "brief",
                JSONObject()
                    .put("headline", "Well rested and steady")
                    .put("detail", "Overnight HRV is 70 ms, right in your usual range, and resting HR is 47, a touch below your normal 49. You slept 9 h 22, well over your usual."),
            )
        }
        sessionCheck?.let { coaching.put("session_check", JSONObject(it)) }
        suggestion?.let { coaching.put("suggestion", JSONObject(it)) }
        return JSONObject()
            .put("schema_version", 1)
            .put("freshness", JSONObject().put("db_daily_max_date", today.toString()))
            .put(
                "readiness",
                JSONObject(
                    """{"available":true,"score":78,"label":"Steady","headline":"Recovery looks steady","reason":"HRV in range.",
                       "confidence":"High","component_count":4,
                       "components":[{"name":"HRV","detail":"70 ms against a 30-day 69 ms"},{"name":"Resting HR","detail":"47 bpm against 49"}]}""",
                ),
            )
            .put("warnings", JSONArray())
            .put("coaching", coaching)
            .put(
                "wellbeing",
                JSONObject()
                    .put("data_date", today.toString())
                    .put("days", days)
                    .put(
                        "runs",
                        JSONArray(listOf("2026-09-08", "2026-09-10", "2026-09-14", "2026-09-27", "2026-09-30").map { JSONObject().put("date", it).put("km", 5.0) }),
                    )
                    .put(
                        "usual",
                        JSONObject()
                            .put("hrv_ms", JSONObject().put("mean", 69).put("low", 62).put("high", 76).put("nights", 30))
                            .put("rhr", JSONObject().put("mean", 49).put("low", 46).put("high", 52).put("nights", 30))
                            .put("sleep_h", JSONObject().put("mean", 7.95).put("low", 6.95).put("high", 8.95).put("nights", 30))
                            .put("stress", JSONObject().put("mean", 26).put("low", 20).put("high", 32).put("nights", 28))
                            .put("steps", JSONObject().put("mean", 8418).put("low", 2970).put("high", 13865).put("nights", 28)),
                    )
                    .put("bedtime_spread_7d_min", 129),
            )
            .put("weight", JSONObject().put("avg_7d_kg", 61.2).put("change_30d_kg", -0.3).put("change_90d_kg", -0.6).put("days_since_last", 2))
            .toString()
    }

    fun payload(
        brief: Boolean = true,
        sessionCheck: String? = """{"date":"2026-10-04","verdict":"as_planned","reason":"HRV and resting HR are both normal for you and you slept long. Fine for 10 km easy."}""",
        suggestion: String? = null,
    ): CoachPayload = CoachPayload.parse(payloadJson(brief, sessionCheck, suggestion))

    val sessions = listOf(
        SessionEntity(
            id = 1, eventId = "richmond", date = LocalDate.of(2026, 10, 4), type = SessionType.LONG_RUN, title = "Long run",
            phase = TrainingPhase.BUILD, weekNumber = 1, targetDistanceKm = 10.0,
            notes = "Easy, Zone 2 (122-157 bpm). Walk breaks are fine. Notice how the foot feels during the run, not just after. Take something to eat if you're out over an hour.",
        ),
        SessionEntity(
            id = 2, eventId = "richmond", date = LocalDate.of(2026, 10, 6), type = SessionType.EASY_RUN, title = "Treadmill run",
            phase = TrainingPhase.BUILD, weekNumber = 2, targetDistanceKm = 5.0, notes = "Easy, Zone 2 (122-157 bpm).",
        ),
    )
}
