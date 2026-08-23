package com.laurasheehan.royalmiles.core.plan

import com.laurasheehan.royalmiles.core.model.Session
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.core.model.TrainingPlan
import com.laurasheehan.royalmiles.core.model.TrainingWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * Generates a Monday-to-Sunday week-by-week half marathon plan working backward from race day.
 * Weeks are numbered 1..N, each commencing on a Monday. The output is a starting point only —
 * every [Session] it produces is meant to be persisted and then freely edited.
 */
object TrainingPlanGenerator {

    fun generate(
        raceDate: LocalDate,
        today: LocalDate = LocalDate.now(),
        peakLongRunKm: Double = 17.0,
        raceDistanceKm: Double = 21.1,
        baseLongRunStartKm: Double = 8.0,
    ): TrainingPlan {
        require(!raceDate.isBefore(today)) { "Race date must be in the future" }

        val startDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        val raceWeekMonday = raceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val totalWeeks = (ChronoUnit.WEEKS.between(startDate, raceWeekMonday).toInt() + 1).coerceAtLeast(1)

        val taperWeeks = 1
        val peakWeeks = if (totalWeeks >= 3) 1 else 0
        val baseWeeks = when {
            totalWeeks >= 6 -> 2
            totalWeeks >= 4 -> 1
            else -> 0
        }
        val buildWeeks = (totalWeeks - taperWeeks - peakWeeks - baseWeeks).coerceAtLeast(0)

        val phases = buildList {
            repeat(baseWeeks) { add(TrainingPhase.BASE) }
            repeat(buildWeeks) { add(TrainingPhase.BUILD) }
            repeat(peakWeeks) { add(TrainingPhase.PEAK) }
            repeat(taperWeeks) { add(TrainingPhase.TAPER) }
        }.let { phaseList ->
            // Guard: if rounding left us short (very small totalWeeks), pad the front with BUILD.
            if (phaseList.size < totalWeeks) {
                List(totalWeeks - phaseList.size) { TrainingPhase.BUILD } + phaseList
            } else {
                phaseList.takeLast(totalWeeks)
            }
        }

        val rampWeeks = buildWeeks + peakWeeks
        val longRunProgression: List<Double> = if (rampWeeks <= 0) {
            emptyList()
        } else if (rampWeeks == 1) {
            listOf(peakLongRunKm)
        } else {
            val step = (peakLongRunKm - baseLongRunStartKm) / (rampWeeks - 1)
            (0 until rampWeeks).map { i -> roundToHalf(baseLongRunStartKm + step * i) }
        }

        var rampIndex = 0
        val weeks = phases.mapIndexed { index, phase ->
            val weekStart = startDate.plusWeeks(index.toLong())
            val weekNumber = index + 1
            val sessions = when (phase) {
                TrainingPhase.BASE -> baseWeekSessions(weekStart, weekNumber, phase)
                TrainingPhase.TAPER -> taperWeekSessions(weekStart, raceDate, raceDistanceKm, phase)
                TrainingPhase.BUILD, TrainingPhase.PEAK -> {
                    val longRun = longRunProgression.getOrElse(rampIndex) { peakLongRunKm }
                    rampIndex++
                    buildOrPeakWeekSessions(weekStart, weekNumber, phase, longRun)
                }
            }
            TrainingWeek(weekNumber = weekNumber, phase = phase, startDate = weekStart, sessions = sessions)
        }

        return TrainingPlan(
            raceDate = raceDate,
            startDate = startDate,
            raceDistanceKm = raceDistanceKm,
            peakLongRunKm = peakLongRunKm,
            weeks = weeks,
        )
    }

    private fun baseWeekSessions(weekStart: LocalDate, weekNumber: Int, phase: TrainingPhase): List<Session> {
        val easyDistance = roundToHalf((3.0 + (weekNumber - 1) * 1.0).coerceAtMost(5.0))
        return listOf(
            Session(weekStart, SessionType.REST, "Rest day", phase, notes = "Full recovery. Legs up if you can."),
            Session(weekStart.plusDays(1), SessionType.STRENGTH, "Strength", phase, targetDurationMin = 35),
            Session(
                weekStart.plusDays(2),
                SessionType.EASY_RUN,
                "Easy run",
                phase,
                targetDistanceKm = easyDistance,
                notes = "Conversational pace only.",
            ),
            Session(weekStart.plusDays(3), SessionType.YOGA, "Yoga / mobility", phase, targetDurationMin = 30),
            Session(weekStart.plusDays(4), SessionType.STRENGTH, "Strength", phase, targetDurationMin = 35),
            Session(
                weekStart.plusDays(5),
                SessionType.CYCLE,
                "Easy spin",
                phase,
                targetDurationMin = 40,
                optional = true,
                notes = "Optional cross-training, easy effort.",
            ),
            Session(
                weekStart.plusDays(6),
                SessionType.EASY_RUN,
                "Easy run",
                phase,
                targetDistanceKm = easyDistance,
                notes = "Conversational pace only.",
            ),
        )
    }

    private fun buildOrPeakWeekSessions(
        weekStart: LocalDate,
        weekNumber: Int,
        phase: TrainingPhase,
        longRunKm: Double,
    ): List<Session> {
        val midWeekDistance = if (phase == TrainingPhase.PEAK) 6.0 else 5.0
        val longRunNotes = if (phase == TrainingPhase.PEAK) {
            "Peak long run. Easy, controlled effort — this is about time on feet, not pace."
        } else {
            "Steady, easy effort. Practice your race-day fuelling."
        }
        return listOf(
            Session(weekStart, SessionType.REST, "Rest day", phase),
            Session(weekStart.plusDays(1), SessionType.STRENGTH, "Strength", phase, targetDurationMin = 35),
            Session(
                weekStart.plusDays(2),
                SessionType.EASY_RUN,
                "Easy run",
                phase,
                targetDistanceKm = midWeekDistance,
            ),
            Session(weekStart.plusDays(3), SessionType.YOGA, "Yoga / mobility", phase, targetDurationMin = 30),
            Session(weekStart.plusDays(4), SessionType.STRENGTH, "Strength", phase, targetDurationMin = 30),
            Session(
                weekStart.plusDays(5),
                SessionType.CYCLE,
                "Easy spin",
                phase,
                targetDurationMin = 40,
                optional = true,
            ),
            Session(
                weekStart.plusDays(6),
                SessionType.LONG_RUN,
                "Long run",
                phase,
                targetDistanceKm = longRunKm,
                notes = longRunNotes,
            ),
        )
    }

    private fun taperWeekSessions(
        weekStart: LocalDate,
        raceDate: LocalDate,
        raceDistanceKm: Double,
        phase: TrainingPhase,
    ): List<Session> {
        val sessions = mutableListOf<Session>()
        var date = weekStart
        while (!date.isAfter(raceDate)) {
            val daysBeforeRace = ChronoUnit.DAYS.between(date, raceDate).toInt()
            val session = when (daysBeforeRace) {
                0 -> Session(
                    date,
                    SessionType.RACE,
                    "Royal Parks Half Marathon",
                    phase,
                    targetDistanceKm = raceDistanceKm,
                    notes = "This is it. Trust the training.",
                )
                1 -> Session(date, SessionType.REST, "Rest day", phase, notes = "Feet up, hydrate, lay out kit.")
                2 -> Session(
                    date,
                    SessionType.EASY_RUN,
                    "Shakeout run",
                    phase,
                    targetDistanceKm = 2.0,
                    optional = true,
                    notes = "Very easy, a few strides if it feels good.",
                )
                3 -> Session(date, SessionType.REST, "Rest day", phase)
                4 -> Session(
                    date,
                    SessionType.EASY_RUN,
                    "Easy run with strides",
                    phase,
                    targetDistanceKm = 3.0,
                    notes = "Include 4 x 20s relaxed strides.",
                )
                5 -> Session(
                    date,
                    SessionType.STRENGTH,
                    "Light strength",
                    phase,
                    targetDurationMin = 20,
                    optional = true,
                    notes = "Keep it light — mobility over load.",
                )
                6 -> Session(date, SessionType.EASY_RUN, "Easy run", phase, targetDistanceKm = 3.0)
                else -> Session(date, SessionType.REST, "Rest day", phase)
            }
            sessions.add(session)
            date = date.plusDays(1)
        }
        return sessions
    }

    private fun roundToHalf(value: Double): Double = (value * 2).roundToInt() / 2.0
}
