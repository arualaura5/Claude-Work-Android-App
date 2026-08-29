package com.laurasheehan.royalmiles.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.laurasheehan.royalmiles.core.model.SessionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A workout as Garmin (or Strava, or Google Fit) wrote it to Health Connect.
 *
 * The metrics are all nullable on purpose: Health Connect stores each one as its own record type,
 * written independently of the exercise session, so any of them can be absent — either because the
 * source app didn't write it, or because that permission hasn't been granted.
 */
data class ExternalWorkout(
    val start: Instant,
    val end: Instant,
    val exerciseType: Int,
    val title: String?,
    val distanceKm: Double? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val calories: Int? = null,
    val elevationGainM: Int? = null,
) {
    val durationMinutes: Int
        get() = ((end.epochSecond - start.epochSecond) / 60).toInt()

    val localDate: LocalDate
        get() = start.atZone(ZoneId.systemDefault()).toLocalDate()

    /** Average pace in minutes per km, when there's enough to compute it. */
    val paceMinPerKm: Double?
        get() = distanceKm?.takeIf { it > 0 }?.let { durationMinutes / it }

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
 */
class HealthConnectRepository(private val context: Context) {

    /** Without these the app has nothing to show, so they gate the "connected" state. */
    private val requiredPermissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
    )

    /**
     * Richer per-workout metrics. Deliberately optional: if any of these aren't granted the app
     * still works, it just shows less. That keeps a newly-added permission from locking the user
     * out of screens that were working fine before.
     */
    private val optionalPermissions = setOf(
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
        // Not read by any feature yet — requested so the diagnostic can report what Garmin
        // actually writes, which is what decides whether they're worth building on.
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(PowerRecord::class),
    )

    /** Everything worth asking for, in one prompt. */
    val permissions: Set<String> = requiredPermissions + optionalPermissions

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun requestPermissionsContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun hasPermissions(): Boolean = granted().containsAll(requiredPermissions)

    private suspend fun granted(): Set<String> =
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    suspend fun recentWorkouts(days: Long = 7): List<ExternalWorkout> {
        val client = HealthConnectClient.getOrCreate(context)
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.after(Instant.now().minusSeconds(days * 24 * 60 * 60)),
            ),
        )
        val grantedPermissions = granted()
        return response.records
            .map { record ->
                val base = ExternalWorkout(
                    start = record.startTime,
                    end = record.endTime,
                    exerciseType = record.exerciseType,
                    title = record.title,
                )
                enrich(base, grantedPermissions)
            }
            .sortedByDescending { it.start }
    }

    /**
     * Fills in distance, heart rate, calories and elevation for a single workout's time window.
     *
     * Only metrics whose permission is actually granted are requested — asking for an ungranted one
     * makes the whole aggregate call fail, taking the granted metrics down with it. Any failure is
     * swallowed so one unreadable workout can't empty the whole sync list.
     */
    private suspend fun enrich(workout: ExternalWorkout, grantedPermissions: Set<String>): ExternalWorkout {
        val metrics = mutableSetOf<AggregateMetric<*>>()
        if (HealthPermission.getReadPermission(DistanceRecord::class) in grantedPermissions) {
            metrics += DistanceRecord.DISTANCE_TOTAL
        }
        if (HealthPermission.getReadPermission(HeartRateRecord::class) in grantedPermissions) {
            metrics += HeartRateRecord.BPM_AVG
            metrics += HeartRateRecord.BPM_MAX
        }
        if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in grantedPermissions) {
            metrics += TotalCaloriesBurnedRecord.ENERGY_TOTAL
        }
        if (HealthPermission.getReadPermission(ElevationGainedRecord::class) in grantedPermissions) {
            metrics += ElevationGainedRecord.ELEVATION_GAINED_TOTAL
        }
        if (metrics.isEmpty()) return workout

        return try {
            val result = HealthConnectClient.getOrCreate(context).aggregate(
                AggregateRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(workout.start, workout.end),
                ),
            )
            workout.copy(
                distanceKm = result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers,
                avgHeartRate = result[HeartRateRecord.BPM_AVG]?.toInt(),
                maxHeartRate = result[HeartRateRecord.BPM_MAX]?.toInt(),
                calories = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.toInt(),
                elevationGainM = result[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters?.toInt(),
            )
        } catch (e: Exception) {
            workout
        }
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
