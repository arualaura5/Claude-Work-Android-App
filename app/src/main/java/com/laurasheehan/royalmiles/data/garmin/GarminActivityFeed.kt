package com.laurasheehan.royalmiles.data.garmin

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.laurasheehan.royalmiles.data.coach.CoachRemote
import com.laurasheehan.royalmiles.data.health.ExternalWorkout
import com.laurasheehan.royalmiles.data.health.GARMIN_PACKAGE
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import org.json.JSONObject

/**
 * Recent Garmin activities, read from the cloud refresh's activity feed rather than Health
 * Connect, which only holds a short rolling window and depends on Garmin choosing to write there.
 *
 * The feed is served by the same Worker as the coach, behind the same key, so connecting the
 * cloud coach is all the setup this needs. Garmin's activity id travels as sourceActivityId, which
 * is what stops one activity being logged against two sessions.
 */
object GarminActivityFeed {

    private const val COACH_PATH = "/coach.json"
    private const val FEED_PATH = "/activities.json"

    fun feedAddress(coachAddress: String): String =
        coachAddress.removeSuffix(COACH_PATH).trimEnd('/') + FEED_PATH

    fun fetch(coachAddress: String, key: String): List<ExternalWorkout> =
        parse(CoachRemote.fetch(feedAddress(coachAddress), key))

    fun parse(json: String, zone: ZoneId = ZoneId.systemDefault()): List<ExternalWorkout> {
        val activities = JSONObject(json).getJSONArray("activities")
        return (0 until activities.length())
            .mapNotNull { index -> toWorkout(activities.getJSONObject(index), zone) }
            .sortedByDescending { it.start }
    }

    private fun toWorkout(activity: JSONObject, zone: ZoneId): ExternalWorkout? {
        val id = activity.optStringOrNull("activity_id") ?: return null
        val start = startTime(activity) ?: return null
        val minutes = activity.optIntOrNull("duration_minutes")
            ?: activity.optIntOrNull("moving_minutes")
            ?: 0
        val startInstant = start.atZone(zone).toInstant()
        return ExternalWorkout(
            start = startInstant,
            end = startInstant.plusSeconds(minutes * 60L),
            exerciseType = exerciseType(activity.optStringOrNull("type").orEmpty()),
            title = activity.optStringOrNull("name"),
            distanceKm = activity.optDoubleOrNull("distance_km")?.takeIf { it > 0 },
            avgHeartRate = activity.optIntOrNull("avg_hr"),
            maxHeartRate = activity.optIntOrNull("max_hr"),
            calories = activity.optIntOrNull("calories"),
            sourceApp = GARMIN_PACKAGE,
            sourceActivityId = id,
        )
    }

    /** Garmin's local start time; falls back to midday on the activity's date if it is missing. */
    private fun startTime(activity: JSONObject): LocalDateTime? {
        val local = activity.optStringOrNull("start_time_local")
        if (local != null) {
            try {
                return LocalDateTime.parse(local.replace(' ', 'T').take(19))
            } catch (_: DateTimeParseException) {
            }
        }
        val date = activity.optStringOrNull("date") ?: return null
        return try {
            LocalDate.parse(date.take(10)).atTime(12, 0)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    internal fun exerciseType(garminType: String): Int {
        val type = garminType.lowercase()
        return when {
            "swim" in type && "open_water" in type -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER
            "swim" in type -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
            "run" in type -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            "cycl" in type || "bik" in type -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            "yoga" in type -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
            "strength" in type -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
            "walk" in type -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }
    }

    // Android's org.json turns an explicit null into the string "null", so check isNull first.
    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (isNull(name) || !has(name)) null else optDouble(name).takeIf { !it.isNaN() }?.toInt()

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (isNull(name) || !has(name)) null else optDouble(name).takeIf { !it.isNaN() }
}
