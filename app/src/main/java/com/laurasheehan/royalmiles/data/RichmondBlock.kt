package com.laurasheehan.royalmiles.data

import com.laurasheehan.royalmiles.RaceConfig
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import java.time.LocalDate

/**
 * The four weeks to the Richmond Half, agreed with Laura on 3 October 2026 when she chose to skip
 * Royal Parks. Built from her Garmin history (last five weeks 5-11.5 km a week, longest 9 km;
 * last year's half off 15 km peaks) and reviewed twice by Codex, the second time against the raw
 * data. Three runs a week: a treadmill run of about 5 km (her limit on a treadmill), an after-work
 * run, and a Sunday long run. Easy means her Zone 2, 122-157 bpm.
 *
 * Not a fixed contract: the coach revises it from her data, one day at a time, with her agreement.
 */
object RichmondBlock {
    val START: LocalDate = LocalDate.of(2026, 10, 4)

    private const val EASY = "Easy, Zone 2 (122-157 bpm)."
    private const val STRENGTH_RULE = "Familiar exercises at a moderate load, nothing to failure, no new exercises. Skip it rather than run on sore legs."

    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    fun sessions(): List<SessionEntity> = listOf(
        // This weekend
        session(day(10, 4), SessionType.LONG_RUN, "Long run", TrainingPhase.BUILD, 1, km = 10.0,
            notes = "$EASY Walk breaks are fine. Notice how the foot feels during the run, not just after. Take something to eat if you're out over an hour."),

        // 5-11 Oct
        session(day(10, 5), SessionType.STRENGTH, "Strength", TrainingPhase.BUILD, 2, minutes = 40, notes = STRENGTH_RULE),
        session(day(10, 6), SessionType.EASY_RUN, "Treadmill easy run", TrainingPhase.BUILD, 2, km = 5.0, notes = EASY),
        session(day(10, 8), SessionType.EASY_RUN, "Easy run + strides", TrainingPhase.BUILD, 2, km = 5.0,
            notes = "4-5 km easy, then 4 × 20 s strides: quick and relaxed, full recovery between."),
        session(day(10, 11), SessionType.LONG_RUN, "Long run", TrainingPhase.BUILD, 2, km = 12.0,
            notes = "$EASY Rehearse race fuelling: 30-60 g carbohydrate an hour, what you'll use on the day."),

        // 12-18 Oct: the peak
        session(day(10, 12), SessionType.STRENGTH, "Strength", TrainingPhase.PEAK, 3, minutes = 40, notes = STRENGTH_RULE),
        session(day(10, 13), SessionType.EASY_RUN, "Treadmill easy run", TrainingPhase.PEAK, 3, km = 5.0, notes = EASY),
        session(day(10, 14), SessionType.STRENGTH, "Strength (light)", TrainingPhase.PEAK, 3, minutes = 30, optional = true,
            notes = "Optional second session. Light, and only if the legs are fresh."),
        session(day(10, 15), SessionType.EASY_RUN, "Easy run + strides", TrainingPhase.PEAK, 3, km = 5.0,
            notes = "5 km easy, then 4 × 20 s strides."),
        session(day(10, 18), SessionType.LONG_RUN, "Long run", TrainingPhase.PEAK, 3, km = 15.0,
            notes = "15 km if last week's 12 km went well: the foot fine by next morning, HRV and resting HR normal, legs not flat. Otherwise 13 km. $EASY Walk breaks are fine. Rehearse fuelling."),

        // 19-25 Oct
        session(day(10, 19), SessionType.STRENGTH, "Strength", TrainingPhase.TAPER, 4, minutes = 40, notes = STRENGTH_RULE),
        session(day(10, 20), SessionType.EASY_RUN, "Treadmill: steady blocks", TrainingPhase.TAPER, 4, km = 5.0,
            notes = "Easy warm-up, then 3 × 3 min low Zone 3 (157-162 bpm) with 2 min easy between. Skip the blocks if the foot niggles or sleep was short."),
        session(day(10, 21), SessionType.STRENGTH, "Strength (light or upper body)", TrainingPhase.TAPER, 4, minutes = 30, optional = true,
            notes = "Optional. Light, or upper body only."),
        session(day(10, 22), SessionType.EASY_RUN, "Easy run", TrainingPhase.TAPER, 4, km = 5.0, notes = EASY),
        session(day(10, 25), SessionType.LONG_RUN, "Long run", TrainingPhase.TAPER, 4, km = 9.0, notes = EASY),

        // Race week
        session(day(10, 26), SessionType.STRENGTH, "Strength (light)", TrainingPhase.TAPER, 5, minutes = 30,
            notes = "Light, and the last of the block. Nothing heavy for the legs after today."),
        session(day(10, 27), SessionType.EASY_RUN, "Treadmill easy + strides", TrainingPhase.TAPER, 5, km = 4.5,
            notes = "4-5 km easy with 4 × 20 s strides."),
        session(day(10, 29), SessionType.EASY_RUN, "Shakeout", TrainingPhase.TAPER, 5, km = 3.5, optional = true,
            notes = "Optional 3-4 km, very easy."),
        session(day(11, 1), SessionType.RACE, RaceConfig.RICHMOND_EVENT_NAME, TrainingPhase.TAPER, 5, km = RaceConfig.RICHMOND_RACE_DISTANCE_KM,
            notes = "Start easy: heart rate under 157 for the first 10 km, run-walk if planned. Fuel as rehearsed. From 15 km, if you feel good, let it rise and finish strong."),
    )

    private fun session(
        date: LocalDate,
        type: SessionType,
        title: String,
        phase: TrainingPhase,
        week: Int,
        km: Double? = null,
        minutes: Int? = null,
        optional: Boolean = false,
        notes: String,
    ) = SessionEntity(
        eventId = RaceConfig.RICHMOND_EVENT_ID,
        date = date,
        type = type,
        title = title,
        phase = phase,
        weekNumber = week,
        targetDistanceKm = km,
        targetDurationMin = minutes,
        optional = optional,
        notes = notes,
    )
}
