package com.laurasheehan.royalmiles

import java.time.LocalDate

/** Royal Parks Half Marathon, London — the race this whole plan is built backward from. */
object RaceConfig {
    const val ROYAL_PARKS_EVENT_ID = "royal-parks-2026"
    const val ROYAL_PARKS_EVENT_NAME = "Royal Parks Half Marathon"
    val ROYAL_PARKS_RACE_DATE: LocalDate = LocalDate.of(2026, 10, 11)
    const val ROYAL_PARKS_RACE_DISTANCE_KM = 21.1
    const val ROYAL_PARKS_PEAK_LONG_RUN_KM = 15.0

    const val RICHMOND_EVENT_ID = "richmond-2026"
    const val RICHMOND_EVENT_NAME = "Richmond"
    val RICHMOND_RACE_DATE: LocalDate = LocalDate.of(2026, 11, 1)
    const val RICHMOND_RACE_DISTANCE_KM = 21.1
    const val RICHMOND_PEAK_LONG_RUN_KM = 15.0

    val RACE_DATE: LocalDate = ROYAL_PARKS_RACE_DATE
    const val PEAK_LONG_RUN_KM = ROYAL_PARKS_PEAK_LONG_RUN_KM
}
