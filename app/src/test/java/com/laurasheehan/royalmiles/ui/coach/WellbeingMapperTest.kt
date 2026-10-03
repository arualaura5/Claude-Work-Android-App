package com.laurasheehan.royalmiles.ui.coach

import com.laurasheehan.royalmiles.data.coach.CoachPayload
import com.laurasheehan.royalmiles.ui.coach.WellbeingUi.Tone
import org.json.JSONObject
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WellbeingMapperTest {

    private val today = WellbeingSamples.today
    private val morning = LocalTime.of(8, 30)

    private fun build(payload: CoachPayload = WellbeingSamples.payload()) =
        WellbeingMapper.build(payload, WellbeingSamples.sessions, today, morning)!!

    @Test
    fun `an older payload without the wellbeing block falls back to the old layout`() {
        val old = JSONObject(WellbeingSamples.payloadJson()).apply { remove("wellbeing") }.toString()
        assertNull(WellbeingMapper.build(CoachPayload.parse(old), WellbeingSamples.sessions, today, morning))
    }

    @Test
    fun `the brief is the coach's own words, falling back to the readiness wording`() {
        val ui = build()
        assertEquals("Well rested and steady", ui.headline)
        assertEquals(78, ui.readinessScore)
        assertEquals("Based on 4 signals · High confidence", ui.basis)
        assertEquals("Good morning, Laura", ui.greeting)

        val noBrief = build(WellbeingSamples.payload(brief = false))
        assertEquals("Recovery looks steady", noBrief.headline)
        assertEquals("HRV in range.", noBrief.detail)
    }

    @Test
    fun `signals describe last night against her usual range`() {
        val signals = build().signals
        assertEquals(listOf("HRV overnight", "Sleep", "Bedtime"), signals.map { it.name })
        val hrv = signals[0]
        assertEquals("70 ms", hrv.value)
        assertEquals("Inside your usual 62–76 ms", hrv.note)
        assertEquals(Tone.NORMAL, hrv.tone)
        assertEquals("9 h 22", signals[1].value)
        assertEquals("1 h 25 more than your usual 7 h 57", signals[1].note)
        // 22:16 to 00:25 over the last seven nights.
        assertEquals("Bedtimes spread over 2 h 09 this week", signals[2].note)
        assertEquals(Tone.NOTICE, signals[2].tone)
    }

    @Test
    fun `low HRV is worth noticing, and far below her range is a flag`() {
        val payload = WellbeingSamples.payload()
        val wellbeing = payload.wellbeing!!
        fun toneFor(hrv: Int): Tone {
            val days = wellbeing.days.dropLast(1) + wellbeing.days.last().copy(hrvMs = hrv)
            return WellbeingMapper.signals(wellbeing, days, days.last()).first().tone
        }
        assertEquals(Tone.NORMAL, toneFor(80))
        assertEquals(Tone.NOTICE, toneFor(58))
        assertEquals(Tone.FLAG, toneFor(50))
    }

    @Test
    fun `the session card shows the coach's verdict for her next run`() {
        val training = assertNotNull(build().training)
        assertEquals("Tomorrow · Sunday 4 Oct", training.whenLabel)
        assertEquals("Long run · 10 km", training.session)
        assertEquals("Suitable as planned", training.verdict)
        assertEquals(Tone.NORMAL, training.verdictTone)
        assertEquals("Zone 2", training.zone)
        assertEquals("122–157 bpm · easy, conversational", training.zoneDetail)
        assertTrue(training.note!!.startsWith("From your plan: Walk breaks are fine."), "the zone sentence isn't repeated")
    }

    @Test
    fun `a suggestion for that day takes the card, in the coach's words`() {
        val payload = WellbeingSamples.payload(
            suggestion = """{"action":"skip","date":"2026-10-04","headline":"Rest on Sunday.","reason":"HRV 50 ms."}""",
        )
        val training = assertNotNull(build(payload).training)
        assertEquals("Coach suggests a change", training.verdict)
        assertEquals(Tone.NOTICE, training.verdictTone)
        assertEquals("Rest on Sunday.", training.reason)
    }

    @Test
    fun `with no session check the card shows the session without a verdict`() {
        val training = assertNotNull(build(WellbeingSamples.payload(sessionCheck = null)).training)
        assertNull(training.verdict)
        assertEquals("Long run · 10 km", training.session)
    }

    @Test
    fun `go easy keeps her in zone 2 and nothing planned means no card`() {
        val easy = build(WellbeingSamples.payload(sessionCheck = """{"date":"2026-10-04","verdict":"go_easy","reason":"Short sleep."}"""))
        assertEquals("Keep it easy", easy.training?.verdict)
        assertEquals("Zone 2", easy.training?.zone)
        assertNull(WellbeingMapper.training(emptyList(), null, today))
    }

    @Test
    fun `weight shows only as a trend`() {
        assertTrue(build().moreContext.contains("Weight trend" to "Broadly stable over 90 days"))
        assertTrue(build().moreContext.none { it.second.contains("61.2") })
    }

    @Test
    fun `coaching written for an earlier day never sits beside newer numbers`() {
        val json = JSONObject(WellbeingSamples.payloadJson())
        json.getJSONObject("coaching").put("data_date", "2026-10-02")
        val ui = build(CoachPayload.parse(json.toString()))
        assertEquals("Recovery looks steady", ui.headline, "falls back to the readiness wording")
        assertNull(ui.recommendation)
        assertNull(ui.training?.verdict)
        assertTrue(ui.coachTake.isEmpty())
        assertTrue(ui.coachAbsent!!.contains("2026-10-02"))
    }

    @Test
    fun `the next session is the next one planned, whatever it is, but optional extras don't take the card`() {
        val strength = WellbeingSamples.sessions.first().copy(
            id = 9, date = today, type = com.laurasheehan.royalmiles.core.model.SessionType.STRENGTH,
            title = "Strength", targetDistanceKm = null, notes = "",
        )
        val withStrength = WellbeingMapper.training(WellbeingSamples.sessions + strength, null, today)
        assertEquals("Strength", withStrength?.session)
        assertNull(withStrength?.zone)
        val optional = WellbeingMapper.training(WellbeingSamples.sessions + strength.copy(optional = true), null, today)
        assertEquals("Long run · 10 km", optional?.session)
    }

    @Test
    fun `sleep windows stay on the axis either side of it`() {
        assertEquals(0f to 1f, sleepWindow(LocalTime.of(20, 30), LocalTime.of(10, 30)))
        val (from, to) = sleepWindow(LocalTime.of(0, 12), LocalTime.of(9, 34))!!
        assertEquals(3 * 60 + 12, Math.round(from * 13 * 60))
        assertEquals(12 * 60 + 34, Math.round(to * 13 * 60))
    }

    @Test
    fun `a night with no reading stays a gap`() {
        val json = JSONObject(WellbeingSamples.payloadJson())
        json.getJSONObject("wellbeing").getJSONArray("days").getJSONObject(10).put("hrv_ms", JSONObject.NULL)
        val ui = build(CoachPayload.parse(json.toString()))
        assertNull(ui.rhythm.hrv[10])
        assertEquals(30, ui.rhythm.dates.size)
    }
}
