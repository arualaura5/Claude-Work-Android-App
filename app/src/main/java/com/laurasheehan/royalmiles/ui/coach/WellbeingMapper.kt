package com.laurasheehan.royalmiles.ui.coach

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import com.laurasheehan.royalmiles.data.coach.CoachPayload.Coaching.Verdict
import com.laurasheehan.royalmiles.ui.coach.WellbeingUi.Tone
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns the coach payload and her plan into the wellbeing page.
 *
 * Coach-voiced sentences are taken verbatim from the coach; this only describes numbers against
 * her usual ranges ("Inside your usual 62–76 ms"). Null when the payload predates the wellbeing
 * block, so the screen can fall back to the older layout until the next morning's coaching.
 */
internal object WellbeingMapper {

    /** Her running zones from Garmin Connect, as she confirmed them. */
    private val ZONES = mapOf(
        1 to "94–122 bpm · very easy",
        2 to "122–157 bpm · easy, conversational",
        3 to "157–168 bpm · steady",
        4 to "168–178 bpm · threshold",
        5 to "178–187 bpm · hard",
    )
    private val RUNS = setOf(SessionType.EASY_RUN, SessionType.LONG_RUN, SessionType.RACE)
    private val ZONE_IN_NOTES = Regex("""Zone (\d)""")
    // The plan's own "Easy, Zone 2 (122-157 bpm)." opener repeats the zone label, so it is left off the note.
    private val ZONE_SENTENCE = Regex("""^[^.]*Zone \d \(\d+-\d+ bpm\)\.\s*""")

    private val dayLong = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
    private val dayShort = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH)
    private val dateShort = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun build(payload: CoachPayload, sessions: List<SessionEntity>, today: LocalDate, now: LocalTime): WellbeingUi? {
        val wellbeing = payload.wellbeing ?: return null
        val days = wellbeing.days.mapNotNull { day -> parseDate(day.date)?.let { it to day } }.sortedBy { it.first }
        if (days.isEmpty()) return null
        val dataDate = parseDate(wellbeing.dataDate) ?: days.last().first
        val last = days.lastOrNull { it.first == dataDate }?.second ?: days.last().second
        val dayBefore = days.lastOrNull { it.first == dataDate.minusDays(1) }?.second

        val readiness = payload.readiness?.takeIf { it.available }
        // The coach's words only stand beside the numbers they were written from.
        val coaching = payload.coaching?.takeIf { it.dataDate == null || it.dataDate == wellbeing.dataDate }

        return WellbeingUi(
            dateLine = today.format(dayLong) + " · " + dataLabel(dataDate, today),
            greeting = when {
                now.hour < 12 -> "Good morning, Laura"
                now.hour < 18 -> "Good afternoon, Laura"
                else -> "Good evening, Laura"
            },
            headline = coaching?.brief?.headline ?: readiness?.headline ?: "Your body today",
            detail = coaching?.brief?.detail ?: readiness?.reason,
            recommendation = coaching?.keyReminder,
            readinessScore = readiness?.score,
            readinessLabel = readiness?.label,
            basis = readiness?.let { basis(it) },
            warnings = payload.warnings,
            training = training(sessions, coaching, today),
            signals = signals(wellbeing, days.map { it.second }, last),
            rhythm = WellbeingUi.Rhythm(
                dates = days.map { it.first },
                hrv = days.map { it.second.hrvMs?.toFloat() },
                hrvBand = wellbeing.usualHrv?.band(),
                rhr = days.map { it.second.rhr?.toFloat() },
                rhrBand = wellbeing.usualRhr?.band(),
                sleepHours = days.map { it.second.sleepHours?.toFloat() },
                sleepUsual = wellbeing.usualSleepHours?.mean?.toFloat(),
                sleepBand = wellbeing.usualSleepHours?.band(),
                runDays = wellbeing.runs.mapNotNull { parseDate(it.date) }.toSet(),
            ),
            sleep = sleep(days.map { it.second }, last),
            coachTake = coaching?.actionPoints.orEmpty().take(4).map { WellbeingUi.CoachPoint(it.title, it.body) },
            coachAbsent = when {
                coaching != null -> null
                payload.coaching != null -> "The coaching was written for ${payload.coaching.dataDate}, before these numbers. Today's will follow."
                else -> payload.coachingAbsentReason
            },
            fullCoaching = listOfNotNull(coaching?.statusSummary, coaching?.motivation).joinToString("\n\n").ifBlank { null },
            basedOn = readiness?.components.orEmpty().mapNotNull { c ->
                val name = c.name ?: return@mapNotNull null
                val detail = c.detail ?: return@mapNotNull null
                name to detail
            },
            moreContext = moreContext(wellbeing, dayBefore, payload.weight),
        )
    }

    // ── the brief ────────────────────────────────────────────────────────────

    private fun dataLabel(dataDate: LocalDate, today: LocalDate): String = when (dataDate) {
        today -> "data to this morning"
        today.minusDays(1) -> "data to yesterday"
        else -> "data to ${dataDate.format(dateShort)}"
    }

    private fun basis(readiness: CoachPayload.Readiness): String? {
        val signals = readiness.componentCount?.let { "Based on $it signals" }
        val confidence = readiness.confidence?.let { "$it confidence" }
        return listOfNotNull(signals, confidence).joinToString(" · ").ifBlank { null }
    }

    // ── her next session ─────────────────────────────────────────────────────

    internal fun training(sessions: List<SessionEntity>, coaching: CoachPayload.Coaching?, today: LocalDate): WellbeingUi.TrainingContext? {
        val upcoming = sessions.filter {
            it.isOutstanding && !it.supersededByCoach && it.type != SessionType.REST &&
                !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(7))
        }
        val check = coaching?.sessionCheck?.takeIf { parseDate(it.date)?.isBefore(today) == false }
        val suggestion = coaching?.suggestion?.takeIf { parseDate(it.date)?.isBefore(today) == false }
        // Runs first on a day that has a run and something else, as the coach's check is about the run.
        val runsFirst = compareBy<SessionEntity>({ it.date }, { if (it.type in RUNS) 0 else 1 }, { it.id })
        val session = check?.let { c -> upcoming.filter { it.date.toString() == c.date }.minWithOrNull(runsFirst) }
            ?: upcoming.filterNot { it.optional }.minWithOrNull(runsFirst)
            ?: return null

        var verdict: String? = null
        var tone = Tone.NORMAL
        var reason: String? = null
        var reasonDetail: String? = null
        if (suggestion != null && suggestion.date == session.date.toString()) {
            verdict = "Coach suggests a change"
            tone = Tone.NOTICE
            reason = suggestion.headline
            reasonDetail = suggestion.reason
        } else if (check != null && check.date == session.date.toString()) {
            verdict = when (check.verdict) {
                Verdict.AS_PLANNED -> "Suitable as planned"
                Verdict.GO_EASY -> "Keep it easy"
                Verdict.SHORTEN -> "Consider shortening"
                Verdict.REST_OK -> "Rest is a good option"
            }
            tone = if (check.verdict == Verdict.AS_PLANNED) Tone.NORMAL else Tone.NOTICE
            reason = check.reason
        }

        val zone = when {
            check?.verdict == Verdict.GO_EASY && check.date == session.date.toString() -> 2
            session.type == SessionType.RACE -> null
            else -> ZONE_IN_NOTES.find(session.notes)?.groupValues?.get(1)?.toIntOrNull()
                ?: if (session.type in RUNS) 2 else null
        }?.takeIf { it in ZONES }

        val target = session.targetDistanceKm?.let { "${formatKm(it)} km" } ?: session.targetDurationMin?.let { "$it min" }
        val note = session.notes.replace(ZONE_SENTENCE, "").trim().ifBlank { null }

        return WellbeingUi.TrainingContext(
            whenLabel = when (session.date) {
                today -> "Today · ${session.date.format(dayShort)}"
                today.plusDays(1) -> "Tomorrow · ${session.date.format(dayShort)}"
                else -> session.date.format(dayShort)
            },
            session = listOfNotNull(session.title, target).joinToString(" · "),
            verdict = verdict,
            verdictTone = tone,
            reason = reason,
            reasonDetail = reasonDetail,
            zone = zone?.let { "Zone $it" },
            zoneDetail = zone?.let { ZONES[it] },
            note = note?.let { "From your plan: $it" },
        )
    }

    private fun formatKm(km: Double): String = if (km % 1.0 == 0.0) km.toInt().toString() else "%.1f".format(km)

    // ── signals ──────────────────────────────────────────────────────────────

    internal fun signals(
        wellbeing: CoachPayload.Wellbeing,
        days: List<CoachPayload.Wellbeing.Day>,
        last: CoachPayload.Wellbeing.Day,
    ): List<WellbeingUi.Signal> {
        val recent = days.takeLast(14)
        val hrv = last.hrvMs?.let { value ->
            val usual = wellbeing.usualHrv
            WellbeingUi.Signal(
                name = "HRV overnight",
                value = "$value ms",
                note = usual?.let { u ->
                    val range = "${u.low.roundToInt()}–${u.high.roundToInt()} ms"
                    when {
                        value < u.low -> "Below your usual $range"
                        value > u.high -> "Above your usual $range"
                        else -> "Inside your usual $range"
                    }
                } ?: "Too few nights yet for a usual range",
                explain = "The variation between heartbeats while you sleep, averaged over the night. Training, " +
                    "sleep, illness, alcohol and stress all move it. Your usual range is the middle two thirds " +
                    "of the 30 nights before last night.",
                series = recent.mapNotNull { it.hrvMs?.toFloat() },
                band = usual?.band(),
                tone = usual?.let { lowIsBad(value.toDouble(), it) } ?: Tone.NORMAL,
            )
        }
        val sleep = last.sleepHours?.let { hours ->
            val usual = wellbeing.usualSleepHours
            WellbeingUi.Signal(
                name = "Sleep",
                value = formatHours(hours.toFloat()),
                note = usual?.let { u ->
                    val minutes = ((hours - u.mean) * 60).roundToInt()
                    val usualText = formatHours(u.mean.toFloat())
                    when {
                        abs(minutes) < 15 -> "About your usual $usualText"
                        minutes > 0 -> "${formatMinutes(minutes)} more than your usual $usualText"
                        else -> "${formatMinutes(-minutes)} less than your usual $usualText"
                    }
                } ?: "Too few nights yet for a usual",
                explain = "Time asleep, as Garmin's sleep record measures it. Your usual is your average over the " +
                    "30 nights before last night.",
                series = recent.mapNotNull { it.sleepHours?.toFloat() },
                band = usual?.band(),
                tone = usual?.let { lowIsBad(hours, it) } ?: Tone.NORMAL,
            )
        }
        val rhr = last.rhr?.let { value ->
            val usual = wellbeing.usualRhr
            WellbeingUi.Signal(
                name = "Resting heart rate",
                value = "$value bpm",
                note = usual?.let { u ->
                    val range = "${u.low.roundToInt()}–${u.high.roundToInt()} bpm"
                    when {
                        value > u.high -> "${(value - u.mean).roundToInt()} bpm above your usual ${u.mean.roundToInt()}"
                        value < u.low -> "Below your usual $range"
                        else -> "Inside your usual $range"
                    }
                } ?: "Too few days yet for a usual range",
                explain = "Your lowest steady heart rate of the day, as Garmin measures it. Fatigue, illness, heat " +
                    "and alcohol can all raise it. Your usual range is the middle two thirds of the 30 days before today.",
                series = recent.mapNotNull { it.rhr?.toFloat() },
                band = usual?.band(),
                tone = usual?.let { highIsBad(value.toDouble(), it) } ?: Tone.NORMAL,
            )
        }
        val bedtimes = recent.mapNotNull { it.bed?.let(::minutesAfterSixPm) }
        val spread = bedtimeSpread(days)
        val bedtime = last.bed?.let { bed ->
            WellbeingUi.Signal(
                name = "Bedtime",
                value = bed,
                note = spread?.let { (minutes, _) -> "Bedtimes spread over ${formatMinutes(minutes)} this week" } ?: "Too few nights to compare",
                explain = "When you fell asleep over the last 14 nights, from Garmin's sleep record. The line rises " +
                    "for later nights.",
                series = bedtimes.map { it.toFloat() },
                band = null,
                tone = if ((spread?.first ?: 0) > 90) Tone.NOTICE else Tone.NORMAL,
            )
        }
        // HRV and sleep always; the third is whichever of resting HR and bedtime is telling her more.
        val third = when {
            rhr != null && rhr.tone != Tone.NORMAL -> rhr
            bedtime != null && bedtime.tone != Tone.NORMAL -> bedtime
            else -> rhr ?: bedtime
        }
        return listOfNotNull(hrv, sleep, third)
    }

    /** Outside her usual range in the unhelpful direction is worth noticing; twice as far out is a flag. */
    private fun lowIsBad(value: Double, usual: CoachPayload.Wellbeing.Usual): Tone {
        val spread = spreadOf(usual)
        return when {
            value < usual.low - spread -> Tone.FLAG
            value < usual.low -> Tone.NOTICE
            else -> Tone.NORMAL
        }
    }

    /** Never zero, so a month of near-identical nights can't turn a small change straight into a flag. */
    private fun spreadOf(usual: CoachPayload.Wellbeing.Usual): Double = maxOf(usual.high - usual.mean, usual.mean * 0.05)

    private fun highIsBad(value: Double, usual: CoachPayload.Wellbeing.Usual): Tone {
        val spread = spreadOf(usual)
        return when {
            value > usual.high + spread -> Tone.FLAG
            value > usual.high -> Tone.NOTICE
            else -> Tone.NORMAL
        }
    }

    // ── sleep ────────────────────────────────────────────────────────────────

    private fun sleep(days: List<CoachPayload.Wellbeing.Day>, last: CoachPayload.Wellbeing.Day): WellbeingUi.SleepDetail {
        val nights = days.takeLast(14).mapNotNull { day ->
            val bed = day.bed?.let(::parseTime)
            val wake = day.wake?.let(::parseTime)
            if (bed != null && wake != null) bed to wake else null
        }
        val spread = bedtimeSpread(days)
        return WellbeingUi.SleepDetail(
            lastNight = last.sleepHours?.let { formatHours(it.toFloat()) } ?: "No sleep recorded",
            note = if (last.bed != null && last.wake != null) "Asleep ${last.bed}, awake ${last.wake}." else null,
            nights = nights,
            restingHr = last.rhr?.let { "$it bpm" },
            minimumHr = last.minHr?.let { "$it bpm" },
            consistency = spread?.let { (minutes, range) ->
                "Your bedtime varied by ${formatMinutes(minutes)} over the last 7 nights, from ${range.first} to ${range.second}."
            },
        )
    }

    /** Minutes between her earliest and latest bedtime over the last seven nights, and those two times. */
    private fun bedtimeSpread(days: List<CoachPayload.Wellbeing.Day>): Pair<Int, Pair<String, String>>? {
        val beds = days.takeLast(7).mapNotNull { day -> day.bed?.let { bed -> minutesAfterSixPm(bed)?.let { it to bed } } }
        if (beds.size < 3) return null
        val earliest = beds.minBy { it.first }
        val latest = beds.maxBy { it.first }
        return (latest.first - earliest.first) to (earliest.second to latest.second)
    }

    /** Bedtimes either side of midnight on one scale: 22:00 is 240, 00:30 is 390. */
    private fun minutesAfterSixPm(clock: String): Int? {
        val time = parseTime(clock) ?: return null
        return ((time.hour * 60 + time.minute - 18 * 60) + 24 * 60) % (24 * 60)
    }

    // ── more context ─────────────────────────────────────────────────────────

    private fun moreContext(
        wellbeing: CoachPayload.Wellbeing,
        yesterday: CoachPayload.Wellbeing.Day?,
        weight: CoachPayload.Weight?,
    ): List<Pair<String, String>> {
        val number = NumberFormat.getIntegerInstance(Locale.UK)
        val items = mutableListOf<Pair<String, String>>()
        yesterday?.steps?.let { steps ->
            val usual = wellbeing.usualSteps?.let { " · usual ${number.format(it.mean.roundToInt())}" }.orEmpty()
            items += "Steps yesterday" to number.format(steps) + usual
        }
        yesterday?.stress?.let { stress ->
            val usual = wellbeing.usualStress?.let { " · usual ${it.mean.roundToInt()}" }.orEmpty()
            items += "Average stress yesterday" to "$stress$usual"
        }
        // A trend only, never a weigh-in, and only while the scale is in use.
        if (weight != null && (weight.daysSinceLast ?: Int.MAX_VALUE) <= 14) {
            val (change, over) = weight.change90dKg?.let { it to 90 } ?: weight.change30dKg?.let { it to 30 } ?: (null to 0)
            if (change != null) {
                items += "Weight trend" to when {
                    abs(change) < 1.0 -> "Broadly stable over $over days"
                    change < 0 -> "Down ${"%.1f".format(-change)} kg over $over days"
                    else -> "Up ${"%.1f".format(change)} kg over $over days"
                }
            }
        }
        return items
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun CoachPayload.Wellbeing.Usual.band(): ClosedFloatingPointRange<Float> = low.toFloat()..high.toFloat()

    private fun formatMinutes(minutes: Int): String =
        if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseTime(value: String): LocalTime? = try {
        LocalTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}
