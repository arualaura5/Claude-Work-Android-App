package com.laurasheehan.royalmiles.data.garmin

import java.time.LocalDate
import org.json.JSONObject

/**
 * One activity from the cloud refresh's activity feed (activities.json, served by the same Worker
 * as coach.json). Garmin's own record, so unlike Health Connect it carries Garmin's activity type
 * and training effect, and it covers every day the refresh has seen rather than a rolling window.
 */
data class GarminActivity(
    val activityId: String,
    val date: LocalDate,
    val typeKey: String?,
    val distanceKm: Double?,
    val durationMinutes: Int?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val calories: Int?,
    val aerobicTrainingEffect: Double?,
    val trainingLoad: Double?,
)

object GarminActivityFeed {
    const val SUPPORTED_SCHEMA_VERSION = 1

    /** Rows it can't read are dropped rather than failing the feed; a newer schema is refused. */
    fun parse(json: String): List<GarminActivity> {
        val root = JSONObject(json)
        val version = root.optInt("schema_version", -1)
        require(version == SUPPORTED_SCHEMA_VERSION) { "Unsupported activity feed version $version." }
        val items = root.optJSONArray("activities") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = item.string("activity_id") ?: return@mapNotNull null
            val date = item.string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            GarminActivity(
                activityId = id,
                date = date,
                typeKey = item.string("type"),
                distanceKm = item.double("distance_km"),
                durationMinutes = item.double("duration_minutes")?.toInt()
                    ?: item.double("moving_minutes")?.toInt(),
                avgHeartRate = item.double("avg_hr")?.toInt(),
                maxHeartRate = item.double("max_hr")?.toInt(),
                calories = item.double("calories")?.toInt(),
                aerobicTrainingEffect = item.double("aerobic_training_effect"),
                trainingLoad = item.double("training_load"),
            )
        }
    }

    // JSONObject turns JSON null into the string "null" and a missing number into 0; neither is data.
    private fun JSONObject.string(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun JSONObject.double(name: String): Double? =
        if (isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }
}
