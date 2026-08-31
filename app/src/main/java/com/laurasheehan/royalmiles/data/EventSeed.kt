package com.laurasheehan.royalmiles.data

import com.laurasheehan.royalmiles.RaceConfig

internal fun seedEvents(planMeta: PlanMetaEntity?): List<EventEntity> = listOf(
    EventEntity(
        id = RaceConfig.ROYAL_PARKS_EVENT_ID,
        name = RaceConfig.ROYAL_PARKS_EVENT_NAME,
        raceDate = planMeta?.raceDate ?: RaceConfig.ROYAL_PARKS_RACE_DATE,
        raceDistanceKm = planMeta?.raceDistanceKm ?: RaceConfig.ROYAL_PARKS_RACE_DISTANCE_KM,
        peakLongRunKm = planMeta?.peakLongRunKm ?: RaceConfig.ROYAL_PARKS_PEAK_LONG_RUN_KM,
        planStartDate = planMeta?.startDate,
        planVersion = planMeta?.planVersion ?: 0,
    ),
    EventEntity(
        id = RaceConfig.RICHMOND_EVENT_ID,
        name = RaceConfig.RICHMOND_EVENT_NAME,
        raceDate = RaceConfig.RICHMOND_RACE_DATE,
        raceDistanceKm = RaceConfig.RICHMOND_RACE_DISTANCE_KM,
        peakLongRunKm = RaceConfig.RICHMOND_PEAK_LONG_RUN_KM,
        planStartDate = null,
        planVersion = 0,
    ),
)
