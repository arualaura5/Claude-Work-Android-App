package com.laurasheehan.royalmiles.data.garmin

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.data.health.GARMIN_PACKAGE
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GarminActivityFeedTest {

    private val london = ZoneId.of("Europe/London")

    private val feed = """
        {
          "schema_version": 1,
          "window_days": 60,
          "activities": [
            {
              "activity_id": "20411234567",
              "date": "2026-09-14",
              "start_time_local": "2026-09-14 18:05:12",
              "type": "running",
              "name": "London Running",
              "distance_km": 8.02,
              "duration_minutes": 44,
              "moving_minutes": 43,
              "avg_hr": 152,
              "max_hr": 171,
              "calories": 512
            },
            {
              "activity_id": "20399999999",
              "date": "2026-09-10",
              "start_time_local": null,
              "type": "lap_swimming",
              "name": null,
              "distance_km": null,
              "duration_minutes": 30,
              "avg_hr": null,
              "max_hr": null,
              "calories": null
            },
            { "activity_id": "", "date": "2026-09-09", "type": "running" }
          ]
        }
    """.trimIndent()

    @Test
    fun `the Monday 8k arrives as a Garmin run with its own id`() {
        val run = GarminActivityFeed.parse(feed, london).first()
        assertEquals("20411234567", run.sourceActivityId)
        assertEquals(GARMIN_PACKAGE, run.sourceApp)
        assertEquals(LocalDate.of(2026, 9, 14), run.localDate)
        assertEquals(44, run.durationMinutes)
        assertEquals(8.02, run.distanceKm)
        assertEquals(152, run.avgHeartRate)
        assertEquals(SessionType.EASY_RUN, run.guessedType)
        assertEquals("https://connect.garmin.com/modern/activity/20411234567", run.garminUrl)
    }

    @Test
    fun `her watch answer comes through as feel 1 to 5 and anything else is ignored`() {
        val json = """{"schema_version":1,"activities":[
            {"activity_id":"1","date":"2026-10-08","start_time_local":"2026-10-08 18:00:00","type":"running","distance_km":5.37,"duration_minutes":35,"feel":4},
            {"activity_id":"2","date":"2026-10-07","start_time_local":"2026-10-07 18:00:00","type":"running","distance_km":5.0,"duration_minutes":32,"feel":null},
            {"activity_id":"3","date":"2026-10-06","start_time_local":"2026-10-06 18:00:00","type":"running","distance_km":5.0,"duration_minutes":32,"feel":9}
        ]}"""
        val feel = GarminActivityFeed.parse(json, london).associate { it.sourceActivityId to it.watchFeel }
        assertEquals(mapOf("1" to 4, "2" to null, "3" to null), feel)
    }

    @Test
    fun `missing numbers stay missing rather than becoming zero`() {
        val swim = GarminActivityFeed.parse(feed, london)[1]
        assertEquals(LocalDate.of(2026, 9, 10), swim.localDate)
        assertEquals(SessionType.SWIM, swim.guessedType)
        assertNull(swim.distanceKm)
        assertNull(swim.avgHeartRate)
        assertNull(swim.calories)
        assertNull(swim.title)
    }

    @Test
    fun `an activity without an id is dropped, since it could be logged twice`() {
        assertEquals(2, GarminActivityFeed.parse(feed, london).size)
    }

    @Test
    fun `newest workout comes first`() {
        val dates = GarminActivityFeed.parse(feed, london).map { it.localDate }
        assertEquals(dates.sortedDescending(), dates)
    }

    @Test
    fun `the feed lives beside the coach on the same Worker`() {
        assertEquals(
            "https://royal-miles-coach.example.workers.dev/activities.json",
            GarminActivityFeed.feedAddress("https://royal-miles-coach.example.workers.dev/coach.json"),
        )
    }

    @Test
    fun `garmin activity types map to sensible session types`() {
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, GarminActivityFeed.exerciseType("treadmill_running"))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, GarminActivityFeed.exerciseType("road_biking"))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER, GarminActivityFeed.exerciseType("open_water_swimming"))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, GarminActivityFeed.exerciseType("strength_training"))
        assertTrue(GarminActivityFeed.exerciseType("pilates") == ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT)
    }
}
