package com.laurasheehan.royalmiles.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.laurasheehan.royalmiles.core.model.SessionType
import java.time.Instant
import java.time.ZoneId

data class ExternalWorkout(
    val start: Instant,
    val end: Instant,
    val exerciseType: Int,
    val title: String?,
) {
    val durationMinutes: Int
        get() = ((end.epochSecond - start.epochSecond) / 60).toInt()

    val localDate: java.time.LocalDate
        get() = start.atZone(ZoneId.systemDefault()).toLocalDate()

    /** A best guess at the matching plan session type — Health Connect can't tell easy from long, so this is only a hint. */
    val guessedType: SessionType
        get() = when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> SessionType.EASY_RUN
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> SessionType.CYCLE
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> SessionType.YOGA
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> SessionType.STRENGTH
            else -> SessionType.EASY_RUN
        }
}

/**
 * Reads completed workouts from Android Health Connect — populated by Strava, Garmin Connect,
 * or Google Fit if the user has enabled writing to it in those apps' own settings.
 *
 * Distance isn't read here: Health Connect stores it as a separate, route-shaped record type with a
 * fussier API, and getting duration + activity type auto-filled already removes most of the
 * "did I actually log today's run" friction. Distance stays a quick manual confirmation.
 */
class HealthConnectRepository(private val context: Context) {

    val permissions = setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class))

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun requestPermissionsContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun hasPermissions(): Boolean {
        val client = HealthConnectClient.getOrCreate(context)
        return client.permissionController.getGrantedPermissions().containsAll(permissions)
    }

    suspend fun recentWorkouts(days: Long = 7): List<ExternalWorkout> {
        val client = HealthConnectClient.getOrCreate(context)
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.after(Instant.now().minusSeconds(days * 24 * 60 * 60)),
            ),
        )
        return response.records.map {
            ExternalWorkout(start = it.startTime, end = it.endTime, exerciseType = it.exerciseType, title = it.title)
        }.sortedByDescending { it.start }
    }
}
