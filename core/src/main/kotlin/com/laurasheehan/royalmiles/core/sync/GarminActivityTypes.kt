package com.laurasheehan.royalmiles.core.sync

import com.laurasheehan.royalmiles.core.model.SessionType

/**
 * Garmin's activity type keys (as stored by the cloud refresh) mapped to plan session types.
 *
 * Deliberately a closed list: anything not here — walking, hiking, pilates, "other" — is not
 * logged automatically, so a walk never quietly becomes a run.
 */
object GarminActivityTypes {
    private val RUNNING = setOf(
        "running", "treadmill_running", "track_running", "trail_running", "street_running",
        "indoor_running", "virtual_run",
    )
    private val CYCLING = setOf(
        "cycling", "indoor_cycling", "road_biking", "virtual_ride", "mountain_biking",
        "gravel_cycling",
    )
    private val SWIMMING = setOf("lap_swimming", "open_water_swimming")

    fun sessionTypeFor(typeKey: String?): SessionType? = when (typeKey?.trim()?.lowercase()) {
        null -> null
        in RUNNING -> SessionType.EASY_RUN
        in CYCLING -> SessionType.CYCLE
        in SWIMMING -> SessionType.SWIM
        "yoga" -> SessionType.YOGA
        "strength_training" -> SessionType.STRENGTH
        else -> null
    }
}
