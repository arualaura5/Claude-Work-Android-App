package com.laurasheehan.royalmiles.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.laurasheehan.royalmiles.core.model.SessionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ExternalWorkout(
    val start: Instant,
    val end: Instant,
    val exerciseType: Int,
    val title: String?,
) {
    val durationMinutes: Int
        get() = ((end.epochSecond - start.epochSecond) / 60).toInt()

    val localDate: LocalDate
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
 * Today's food logging total, read straight from Health Connect (Cronometer writes here).
 * Purely informational — nothing in this app turns these numbers into XP, streaks, or badges.
 */
data class NutritionSummary(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

/**
 * Reads from Android Health Connect — populated by Strava, Garmin Connect, Google Fit, or
 * Cronometer if the user has enabled writing to it in those apps' own settings.
 *
 * Workout distance isn't read here: Health Connect stores it as a separate, route-shaped record
 * type with a fussier API, and getting duration + activity type auto-filled already removes most
 * of the "did I actually log today's run" friction. Distance stays a quick manual confirmation.
 */
class HealthConnectRepository(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
    )

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

    /** Sums every nutrition entry logged for today's calendar date. Returns null if nothing's been logged yet. */
    suspend fun nutritionSummaryForToday(): NutritionSummary? {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = NutritionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    today.atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
            ),
        )
        if (response.records.isEmpty()) return null

        var kcal = 0.0
        var protein = 0.0
        var carbs = 0.0
        var fat = 0.0
        response.records.forEach { record ->
            kcal += record.energy?.inKilocalories ?: 0.0
            protein += record.protein?.inGrams ?: 0.0
            carbs += record.totalCarbohydrate?.inGrams ?: 0.0
            fat += record.totalFat?.inGrams ?: 0.0
        }
        return NutritionSummary(kcal = kcal, proteinG = protein, carbsG = carbs, fatG = fat)
    }
}
