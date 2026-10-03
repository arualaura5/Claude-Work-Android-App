package com.laurasheehan.royalmiles

import java.time.LocalDate

/** Her races, and which one the plan is currently built toward. */
object RaceConfig {
    const val ROYAL_PARKS_EVENT_ID = "royal-parks-2026"
    const val ROYAL_PARKS_EVENT_NAME = "Royal Parks Half Marathon"
    val ROYAL_PARKS_RACE_DATE: LocalDate = LocalDate.of(2026, 10, 11)
    const val ROYAL_PARKS_RACE_DISTANCE_KM = 21.1
    const val ROYAL_PARKS_PEAK_LONG_RUN_KM = 15.0

    const val RICHMOND_EVENT_ID = "richmond-2026"
    const val RICHMOND_EVENT_NAME = "Richmond Half Marathon"
    val RICHMOND_RACE_DATE: LocalDate = LocalDate.of(2026, 11, 1)
    const val RICHMOND_RACE_DISTANCE_KM = 21.1
    const val RICHMOND_PEAK_LONG_RUN_KM = 15.0

    /**
     * The race the plan is built toward. Moved from Royal Parks to Richmond on 3 October 2026: she
     * chose to skip Royal Parks and rebuild properly for 1 November instead.
     */
    val RACE_DATE: LocalDate = RICHMOND_RACE_DATE
    const val PEAK_LONG_RUN_KM = RICHMOND_PEAK_LONG_RUN_KM
    const val ACTIVE_EVENT_ID = RICHMOND_EVENT_ID
    const val ACTIVE_EVENT_NAME = RICHMOND_EVENT_NAME
    const val ACTIVE_SHORT_NAME = "Richmond Half"

    /** Which race a plan built backward from [raceDate] belongs to. */
    fun eventIdFor(raceDate: LocalDate): String =
        if (raceDate == RICHMOND_RACE_DATE) RICHMOND_EVENT_ID else ROYAL_PARKS_EVENT_ID
}
