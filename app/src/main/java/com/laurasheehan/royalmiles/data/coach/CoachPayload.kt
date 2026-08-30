package com.laurasheehan.royalmiles.data.coach

import org.json.JSONArray
import org.json.JSONObject

/**
 * The coach payload exported from the Garmin dashboard on the laptop
 * (`scripts/export_coach_payload.py` → `data/exports/coach.json`).
 *
 * Garmin does not write HRV to Health Connect, so none of this is reachable from the phone —
 * the laptop holds seven years of history and the analysis that turns it into a readiness score,
 * an H0 reading and a coaching narrative. This app reads the conclusions, not the data: the whole
 * document is a few KB and carries no reconstructable health record.
 *
 * Every field is nullable because the exporter emits nulls rather than inventing values when a
 * signal is missing, and because a payload written by a newer exporter must still parse here.
 * Parsing is deliberately tolerant: a payload that half-loads is more useful than one that throws.
 */
data class CoachPayload(
    val schemaVersion: Int,
    val generatedAt: String?,
    val freshness: Freshness,
    val coverage: Coverage?,
    val readiness: Readiness?,
    val hrv: HrvMetrics?,
    val plan: PlanStatus?,
    val warnings: List<String>,
    val coaching: Coaching?,
    val coachingAbsentReason: String?,
) {
    data class Freshness(
        val dbDailyMaxDate: String?,
        val dbHrvMaxDate: String?,
        val scoredDate: String?,
        val hrvMetricsDate: String?,
        val coachingDataDate: String?,
    )

    /** How many of the last 30 days actually carry each signal — a thin day and a bad day look identical without it. */
    data class Coverage(
        val windowDays: Int,
        val hrvNights: Int,
        val sleepNights: Int,
        val rhrDays: Int,
    )

    data class Readiness(
        val available: Boolean,
        val date: String?,
        val score: Int?,
        val label: String?,
        val status: String?,
        val headline: String?,
        val reason: String?,
        val confidence: String?,
        val confidenceDetail: String?,
        val componentCount: Int?,
        val components: List<Component>,
    ) {
        data class Component(
            val name: String?,
            val score: Double?,
            val detail: String?,
            val direction: String?,
            val baselineQuality: String?,
        )
    }

    data class HrvMetrics(
        val hrv7d: Double?,
        val hrv30d: Double?,
        val h0: Double?,
        val h07d: Double?,
        val nightlyAvg: Double?,
        val nightlyPeak: Double?,
        val rhr: Double?,
        val trend14d: String?,
        val trend30d: String?,
        val highHrvNights: Int?,
        val highHrvThresholdMs: Double?,
    )

    data class PlanStatus(
        val active: Boolean,
        val window: String?,
        val weeksElapsed: Double?,
        val milestone: Milestone?,
    ) {
        data class Milestone(
            val date: String?,
            val hrv7d: Double?,
            val h0: Double?,
            val rhr: Double?,
            val highHrvNights: Int?,
        )
    }

    data class Coaching(
        val statusSummary: String?,
        val onTrack: Boolean,
        val actionPoints: List<ActionPoint>,
        val coachNote: String?,
        val dataDate: String?,
        val generatedAt: String?,
    ) {
        data class ActionPoint(
            val title: String,
            val priority: String,
            val body: String,
        )
    }

    companion object {
        /** Highest schema version this build understands. A newer payload still parses; unknown keys are ignored. */
        const val SUPPORTED_SCHEMA_VERSION = 1

        fun parse(json: String): CoachPayload {
            val root = JSONObject(json)
            val freshness = root.obj("freshness")

            return CoachPayload(
                schemaVersion = root.optInt("schema_version", 0),
                generatedAt = root.str("generated_at"),
                freshness = Freshness(
                    dbDailyMaxDate = freshness.str("db_daily_max_date"),
                    dbHrvMaxDate = freshness.str("db_hrv_max_date"),
                    scoredDate = freshness.str("scored_date"),
                    hrvMetricsDate = freshness.str("hrv_metrics_date"),
                    coachingDataDate = freshness.str("coaching_data_date"),
                ),
                coverage = root.optJSONObject("coverage")?.let {
                    Coverage(
                        windowDays = it.optInt("window_days", 30),
                        hrvNights = it.optInt("hrv_nights", 0),
                        sleepNights = it.optInt("sleep_nights", 0),
                        rhrDays = it.optInt("rhr_days", 0),
                    )
                },
                readiness = root.optJSONObject("readiness")?.let { readiness ->
                    Readiness(
                        available = readiness.optBoolean("available", false),
                        date = readiness.str("date"),
                        score = readiness.int("score"),
                        label = readiness.str("label"),
                        status = readiness.str("status"),
                        headline = readiness.str("headline"),
                        reason = readiness.str("reason"),
                        confidence = readiness.str("confidence"),
                        confidenceDetail = readiness.str("confidence_detail"),
                        componentCount = readiness.int("component_count"),
                        components = readiness.optJSONArray("components").map { component ->
                            Readiness.Component(
                                name = component.str("name"),
                                score = component.dbl("score"),
                                detail = component.str("detail"),
                                direction = component.str("direction"),
                                baselineQuality = component.str("baseline_quality"),
                            )
                        },
                    )
                },
                hrv = root.optJSONObject("hrv")?.let {
                    HrvMetrics(
                        hrv7d = it.dbl("hrv_7d"),
                        hrv30d = it.dbl("hrv_30d"),
                        h0 = it.dbl("h0"),
                        h07d = it.dbl("h0_7d"),
                        nightlyAvg = it.dbl("nightly_avg"),
                        nightlyPeak = it.dbl("nightly_peak"),
                        rhr = it.dbl("rhr"),
                        trend14d = it.str("trend_14d"),
                        trend30d = it.str("trend_30d"),
                        highHrvNights = it.int("high_hrv_nights"),
                        highHrvThresholdMs = it.dbl("high_hrv_threshold_ms"),
                    )
                },
                plan = root.optJSONObject("plan")?.let { plan ->
                    PlanStatus(
                        active = plan.optBoolean("active", false),
                        window = plan.str("window"),
                        weeksElapsed = plan.dbl("weeks_elapsed"),
                        milestone = plan.optJSONObject("milestone")?.let {
                            PlanStatus.Milestone(
                                date = it.str("date"),
                                hrv7d = it.dbl("hrv_7d"),
                                h0 = it.dbl("h0"),
                                rhr = it.dbl("rhr"),
                                highHrvNights = it.int("high_hrv_nights"),
                            )
                        },
                    )
                },
                warnings = root.optJSONArray("warnings").strings(),
                coaching = root.optJSONObject("coaching")?.let { coaching ->
                    Coaching(
                        statusSummary = coaching.str("status_summary"),
                        onTrack = coaching.optBoolean("on_track", true),
                        actionPoints = coaching.optJSONArray("action_points").map { point ->
                            Coaching.ActionPoint(
                                title = point.str("title").orEmpty(),
                                priority = point.str("priority") ?: "medium",
                                body = point.str("body").orEmpty(),
                            )
                        }.filter { it.title.isNotBlank() },
                        coachNote = coaching.str("coach_note"),
                        dataDate = coaching.str("data_date"),
                        generatedAt = coaching.str("generated_at"),
                    )
                },
                coachingAbsentReason = root.str("coaching_absent_reason"),
            )
        }

        // JSONObject returns the string "null" for JSON nulls and 0 for missing numbers, neither of
        // which is distinguishable from a real value. These read through to genuine nulls instead.

        private fun JSONObject.obj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

        private fun JSONObject.str(key: String): String? =
            if (isNull(key)) null else optString(key, "").ifBlank { null }

        private fun JSONObject.dbl(key: String): Double? =
            if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

        private fun JSONObject.int(key: String): Int? =
            if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }?.toInt()

        private fun <T> JSONArray?.map(transform: (JSONObject) -> T): List<T> {
            val array = this ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map(transform)
        }

        private fun JSONArray?.strings(): List<String> {
            val array = this ?: return emptyList()
            return (0 until array.length())
                .mapNotNull { array.optString(it, "").takeIf { value -> value.isNotBlank() } }
        }
    }
}
