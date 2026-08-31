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
import kotlin.math.round

/**
 * Generates a Monday-to-Sunday week-by-week half marathon plan working backward from race day.
 * Weeks are numbered 1..N, each commencing on a Monday. The output is a starting point only:
 * every [Session] it produces is meant to be persisted and then freely edited.
 */
object TrainingPlanGenerator {

    fun generate(
        raceName: String,
        raceDate: LocalDate,
        today: LocalDate = LocalDate.now(),
        peakLongRunKm: Double = 15.0,
        raceDistanceKm: Double = 21.1,
        baseLongRunStartKm: Double = 7.0,
    ): TrainingPlan {
        require(!raceDate.isBefore(today)) { "Race date must be in the future" }

        val startDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        val raceWeekMonday = raceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val totalWeeks = (ChronoUnit.WEEKS.between(startDate, raceWeekMonday).toInt() + 1).coerceAtLeast(1)

        val taperWeeks = 1
        val progressiveWeeks = (totalWeeks - taperWeeks).coerceAtLeast(0)
        val baseWeeks = if (progressiveWeeks > 0) 1 else 0
        val peakWeeks = if (progressiveWeeks >= 2) 1 else 0
        val buildWeeks = (progressiveWeeks - baseWeeks - peakWeeks).coerceAtLeast(0)

        val phases = buildList {
            repeat(baseWeeks) { add(TrainingPhase.BASE) }
            repeat(buildWeeks) { add(TrainingPhase.BUILD) }
            repeat(peakWeeks) { add(TrainingPhase.PEAK) }
            repeat(taperWeeks) { add(TrainingPhase.TAPER) }
        }.let { phaseList ->
            if (phaseList.size < totalWeeks) {
                List(totalWeeks - phaseList.size) { TrainingPhase.BUILD } + phaseList
            } else {
                phaseList.takeLast(totalWeeks)
            }
        }

        val longRunProgression: List<Double> = if (progressiveWeeks <= 0) {
            emptyList()
        } else if (progressiveWeeks == 1) {
            listOf(peakLongRunKm)
        } else {
            val step = (peakLongRunKm - baseLongRunStartKm) / (progressiveWeeks - 1)
            (0 until progressiveWeeks).map { i -> roundToHalf(baseLongRunStartKm + step * i) }
        }

        var progressionIndex = 0
        val weeks = phases.mapIndexed { index, phase ->
            val weekStart = startDate.plusWeeks(index.toLong())
            val weekNumber = index + 1
            val sessions = when (phase) {
                TrainingPhase.TAPER -> taperWeekSessions(weekStart, raceName, raceDate, raceDistanceKm, phase)
                TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK -> {
                    val longRun = longRunProgression.getOrElse(progressionIndex) { peakLongRunKm }
                    progressionIndex++
                    progressiveWeekSessions(weekStart, phase, longRun)
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

    private fun progressiveWeekSessions(
        weekStart: LocalDate,
        phase: TrainingPhase,
        longRunKm: Double,
    ): List<Session> {
        val (easyDistance, shortDistance) = when (longRunKm) {
            7.0 -> 4.0 to 3.0
            9.0 -> 5.0 to 3.5
            11.0 -> 5.5 to 4.0
            13.0 -> 6.0 to 4.5
            else -> 6.0 to 4.5
        }
        val longRunNotes = if (longRunKm == 13.0) GATE_CHECK_NOTE else EFFORT_CAP_NOTE
        return listOf(
            Session(weekStart, SessionType.REST, "Rest day", phase),
            Session(
                weekStart.plusDays(1),
                SessionType.EASY_RUN,
                "Easy run",
                phase,
                targetDistanceKm = easyDistance,
                notes = "Conversational pace only.",
            ),
            Session(weekStart.plusDays(2), SessionType.STRENGTH, "Strength", phase, targetDurationMin = 35),
            Session(
                weekStart.plusDays(2),
                SessionType.EASY_RUN,
                "Short easy run",
                phase,
                targetDistanceKm = shortDistance,
                notes = "Keep this genuinely easy.",
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
        raceName: String,
        raceDate: LocalDate,
        raceDistanceKm: Double,
        phase: TrainingPhase,
    ): List<Session> {
        val sessions = mutableListOf<Session>()
        var date = weekStart
        while (!date.isAfter(raceDate)) {
            val daysBeforeRace = ChronoUnit.DAYS.between(date, raceDate).toInt()
            when (daysBeforeRace) {
                0 -> sessions.add(
                    Session(
                        date,
                        SessionType.RACE,
                        raceName,
                        phase,
                        targetDistanceKm = raceDistanceKm,
                        notes = RACE_NOTE,
                    ),
                )
                1 -> sessions.add(Session(date, SessionType.REST, "Rest day", phase, notes = "Feet up, hydrate, lay out kit."))
                2 -> sessions.add(
                    Session(
                        date,
                        SessionType.EASY_RUN,
                        "Shakeout run",
                        phase,
                        targetDistanceKm = 3.0,
                        notes = "Very easy, a few relaxed strides if it feels good. Loosening the legs, nothing more.",
                    ),
                )
                3 -> sessions.add(Session(date, SessionType.YOGA, "Yoga / mobility", phase, targetDurationMin = 30))
                4 -> sessions.add(
                    Session(
                        date,
                        SessionType.EASY_RUN,
                        "Easy run",
                        phase,
                        targetDistanceKm = 5.0,
                        notes = "Conversational pace only.",
                    ),
                )
                5 -> sessions.add(
                    Session(
                        date,
                        SessionType.CYCLE,
                        "Easy spin",
                        phase,
                        targetDurationMin = 30,
                        optional = true,
                        notes = "Active recovery from the long run. Very easy — flushing the legs, not training them.",
                    ),
                )
                6 -> sessions.add(
                    Session(
                        date,
                        SessionType.REST,
                        "Rest day",
                        phase,
                        notes = "Day after the long run. Nothing today.",
                    ),
                )
                else -> sessions.add(Session(date, SessionType.REST, "Rest day", phase))
            }
            date = date.plusDays(1)
        }
        return sessions
    }

    private fun roundToHalf(value: Double): Double = round(value * 2) / 2.0

    private const val EFFORT_CAP_NOTE =
        "Keep heart rate under 150. Ease off or take a brief walking break to bring it back down if it drifts."
    private const val GATE_CHECK_NOTE =
        "Gate check. If this run or the days after it bring pain, altered gait, calf/Achilles/plantar symptoms or poor recovery, hold next week at 13 km instead of 15."
    private const val RACE_NOTE =
        "First 14 km at 160 bpm or below. The gap from your longest training run is real - hold the ceiling early, then lift over the last 7 km if it's there."
}
