package com.laurasheehan.royalmiles.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlin.reflect.KClass

/** What one record type actually holds, as opposed to what we assume it holds. */
data class ProbeResult(
    val label: String,
    val granted: Boolean,
    val count: Int,
    val sample: String? = null,
    val error: String? = null,
)

/**
 * Details of a single exercise session, including the provenance fields that decide whether a link
 * back to the source activity is even possible. [clientRecordId] is where a writing app may stash
 * its own identifier — if Garmin puts an activity id there, a deep link becomes constructible.
 */
data class WorkoutProvenance(
    val title: String,
    val start: Instant,
    val sourceApp: String,
    val clientRecordId: String?,
    val healthConnectId: String,
    val segments: Int,
    val laps: Int,
)

data class HealthDiagnosticsReport(
    val probes: List<ProbeResult>,
    val workouts: List<WorkoutProvenance>,
)

/**
 * Reads every record type we might plausibly want and reports what's genuinely present, rather
 * than guessing at what Garmin does and doesn't write. Intended as a one-off investigation aid.
 */
class HealthDiagnostics(private val context: Context) {

    suspend fun run(days: Long = 30): HealthDiagnosticsReport {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val since = Instant.now().minusSeconds(days * 24 * 60 * 60)

        val probes = listOf(
            probe("Workouts", ExerciseSessionRecord::class, since, granted) {
                "${it.exerciseType} · ${it.title ?: "untitled"}"
            },
            probe("Distance", DistanceRecord::class, since, granted) {
                "%.2f km".format(it.distance.inKilometers)
            },
            probe("Heart rate series", HeartRateRecord::class, since, granted) {
                "${it.samples.size} samples"
            },
            probe("Resting heart rate", RestingHeartRateRecord::class, since, granted) {
                "${it.beatsPerMinute} bpm"
            },
            probe("HRV (RMSSD)", HeartRateVariabilityRmssdRecord::class, since, granted) {
                "%.1f ms".format(it.heartRateVariabilityMillis)
            },
            probe("VO2 max", Vo2MaxRecord::class, since, granted) {
                "%.1f".format(it.vo2MillilitersPerMinuteKilogram)
            },
            probe("Sleep", SleepSessionRecord::class, since, granted) { "${it.stages.size} stages" },
            probe("Weight", WeightRecord::class, since, granted) {
                "%.1f kg".format(it.weight.inKilograms)
            },
            probe("Body fat", BodyFatRecord::class, since, granted) { "recorded" },
            // Body composition: lean mass is stored directly, so fat-free mass needs no deriving.
            probe("Lean body mass", LeanBodyMassRecord::class, since, granted) {
                "%.1f kg".format(it.mass.inKilograms)
            },
            probe("Basal metabolic rate", BasalMetabolicRateRecord::class, since, granted) {
                "${it.basalMetabolicRate.inKilocaloriesPerDay.toInt()} kcal/day"
            },
            probe("Body water mass", BodyWaterMassRecord::class, since, granted) {
                "%.1f kg".format(it.mass.inKilograms)
            },
            probe("Bone mass", BoneMassRecord::class, since, granted) {
                "%.1f kg".format(it.mass.inKilograms)
            },
            probe("Height", HeightRecord::class, since, granted) {
                "%.2f m".format(it.height.inMeters)
            },
            probe("Total calories", TotalCaloriesBurnedRecord::class, since, granted) {
                "${it.energy.inKilocalories.toInt()} kcal"
            },
            probe("Active calories", ActiveCaloriesBurnedRecord::class, since, granted) {
                "${it.energy.inKilocalories.toInt()} kcal"
            },
            probe("Elevation", ElevationGainedRecord::class, since, granted) {
                "${it.elevation.inMeters.toInt()} m"
            },
            probe("Steps", StepsRecord::class, since, granted) { "${it.count} steps" },
            probe("Speed series", SpeedRecord::class, since, granted) { "${it.samples.size} samples" },
            probe("Power series", PowerRecord::class, since, granted) { "${it.samples.size} samples" },
            probe("Nutrition", NutritionRecord::class, since, granted) { "logged" },
        )

        return HealthDiagnosticsReport(probes = probes, workouts = workoutProvenance(since, granted))
    }

    /** The provenance of recent workouts — the evidence for whether a source link is possible. */
    private suspend fun workoutProvenance(since: Instant, granted: Set<String>): List<WorkoutProvenance> {
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) !in granted) return emptyList()
        return try {
            HealthConnectClient.getOrCreate(context).readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(since),
                ),
            ).records
                .sortedByDescending { it.startTime }
                .take(10)
                .map { record ->
                    WorkoutProvenance(
                        title = record.title ?: "Untitled workout",
                        start = record.startTime,
                        sourceApp = record.metadata.dataOrigin.packageName,
                        clientRecordId = record.metadata.clientRecordId,
                        healthConnectId = record.metadata.id,
                        segments = record.segments.size,
                        laps = record.laps.size,
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun <T : Record> probe(
        label: String,
        type: KClass<T>,
        since: Instant,
        granted: Set<String>,
        describe: (T) -> String,
    ): ProbeResult {
        if (HealthPermission.getReadPermission(type) !in granted) {
            return ProbeResult(label = label, granted = false, count = 0)
        }
        return try {
            val records = HealthConnectClient.getOrCreate(context).readRecords(
                ReadRecordsRequest(recordType = type, timeRangeFilter = TimeRangeFilter.after(since)),
            ).records
            ProbeResult(
                label = label,
                granted = true,
                count = records.size,
                sample = records.lastOrNull()?.let { runCatching { describe(it) }.getOrNull() },
            )
        } catch (e: Exception) {
            ProbeResult(label = label, granted = true, count = 0, error = e.javaClass.simpleName)
        }
    }
}
