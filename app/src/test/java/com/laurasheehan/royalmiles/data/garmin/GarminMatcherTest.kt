package com.laurasheehan.royalmiles.data.garmin

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.health.ExternalWorkout
import com.laurasheehan.royalmiles.data.health.GARMIN_PACKAGE
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GarminMatcherTest {

    private val today = LocalDate.of(2026, 9, 27)

    private fun workout(
        type: Int = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        km: Double? = 9.03,
        minutes: Long = 62,
        date: LocalDate = today,
    ): ExternalWorkout {
        val start = date.atTime(12, 17).atZone(ZoneId.systemDefault()).toInstant()
        return ExternalWorkout(
            start = start,
            end = start.plusSeconds(minutes * 60),
            exerciseType = type,
            title = "Tower Hamlets Running",
            distanceKm = km,
            avgHeartRate = 151,
            sourceApp = GARMIN_PACKAGE,
            sourceActivityId = "21001",
        )
    }

    private var nextId = 1L

    private fun session(
        type: SessionType = SessionType.EASY_RUN,
        date: LocalDate = today,
        km: Double? = 9.0,
        minutes: Int? = null,
        done: Boolean = false,
        linked: String? = null,
    ) = SessionEntity(
        id = nextId++,
        eventId = "e",
        date = date,
        type = type,
        title = when (type) {
            SessionType.LONG_RUN -> "Long run"
            SessionType.CYCLE -> "Easy spin"
            SessionType.STRENGTH -> "Strength"
            else -> "Easy run"
        },
        phase = TrainingPhase.BUILD,
        weekNumber = 3,
        targetDistanceKm = km,
        targetDurationMin = minutes,
        isCompleted = done,
        sourceActivityId = linked,
    )

    @Test
    fun `her run today, with one easy run planned, is linked without asking`() {
        val easy = session()
        val result = GarminMatcher.match(workout(), listOf(easy, session(SessionType.STRENGTH, today.plusDays(1), km = null)))
        assertEquals(MatchResult.Confident(easy), result)
    }

    @Test
    fun `a six-second recording, or a walk, is never raised`() {
        assertEquals(MatchResult.NotTraining, GarminMatcher.match(workout(km = 0.0, minutes = 0), listOf(session())))
        assertEquals(MatchResult.NotTraining, GarminMatcher.match(workout(km = 0.2, minutes = 5), listOf(session())))
        assertEquals(
            MatchResult.NotTraining,
            GarminMatcher.match(workout(type = ExerciseSessionRecord.EXERCISE_TYPE_WALKING, km = 3.0, minutes = 40), listOf(session())),
        )
    }

    @Test
    fun `a much shorter or longer run than planned is asked about`() {
        val long = session(SessionType.LONG_RUN, km = 16.0)
        val shorter = assertIs<MatchResult.Ask>(GarminMatcher.match(workout(km = 5.1, minutes = 31), listOf(long)))
        assertEquals(AskReason.DIFFERENT_SIZE, shorter.reason)
        assertEquals(listOf(long), shorter.sameKindThatDay)
        assertEquals(listOf(long), shorter.plannedThatDay)

        val longer = assertIs<MatchResult.Ask>(GarminMatcher.match(workout(km = 12.0), listOf(session(km = 8.0))))
        assertEquals(AskReason.DIFFERENT_SIZE, longer.reason)
    }

    @Test
    fun `within a quarter of the plan counts as the same session`() {
        assertIs<MatchResult.Confident>(GarminMatcher.match(workout(km = 7.6), listOf(session(km = 10.0))))
        assertIs<MatchResult.Ask>(GarminMatcher.match(workout(km = 7.4), listOf(session(km = 10.0))))
        // With no planned distance, time decides.
        assertIs<MatchResult.Confident>(GarminMatcher.match(workout(minutes = 50), listOf(session(km = null, minutes = 45))))
        assertIs<MatchResult.Ask>(GarminMatcher.match(workout(minutes = 90), listOf(session(km = null, minutes = 45))))
    }

    @Test
    fun `a bike ride on a run day is asked about, with a swap offered`() {
        val run = session()
        val ask = assertIs<MatchResult.Ask>(
            GarminMatcher.match(workout(type = ExerciseSessionRecord.EXERCISE_TYPE_BIKING, km = 25.0, minutes = 60), listOf(run)),
        )
        assertEquals(AskReason.DIFFERENT_KIND, ask.reason)
        assertEquals(listOf(run), ask.plannedThatDay)
        assertTrue(ask.sameKindThatDay.isEmpty())
    }

    @Test
    fun `a session done on another day is offered as done early or late`() {
        val tomorrow = session(date = today.plusDays(1))
        val twoDaysAgo = session(date = today.minusDays(2))
        val farAway = session(date = today.plusDays(5))
        val ask = assertIs<MatchResult.Ask>(GarminMatcher.match(workout(), listOf(farAway, twoDaysAgo, tomorrow)))
        assertEquals(AskReason.NOTHING_PLANNED, ask.reason)
        assertEquals(listOf(tomorrow, twoDaysAgo), ask.nearby, "nearest first, and only within three days")
    }

    @Test
    fun `two runs planned that day means asking which`() {
        val ask = assertIs<MatchResult.Ask>(GarminMatcher.match(workout(), listOf(session(), session(km = 9.0))))
        assertEquals(AskReason.SEVERAL, ask.reason)
    }

    @Test
    fun `once she says not this one, it is never assumed again`() {
        val easy = session()
        val ask = assertIs<MatchResult.Ask>(GarminMatcher.match(workout(), listOf(easy), sheSaidNotThis = true))
        assertEquals(AskReason.SHE_SAID_NOT_THIS, ask.reason)
        assertEquals(listOf(easy), ask.sameKindThatDay)
    }

    @Test
    fun `done or already-linked sessions are never candidates`() {
        val result = GarminMatcher.match(workout(), listOf(session(done = true), session(linked = "999")))
        assertEquals(AskReason.NOTHING_PLANNED, assertIs<MatchResult.Ask>(result).reason)
    }

    @Test
    fun `only recent activities, from the day this started, are raised`() {
        assertTrue(GarminMatcher.inWindow(today, today))
        assertTrue(GarminMatcher.inWindow(GarminMatcher.SMART_MATCH_SINCE, today))
        assertFalse(GarminMatcher.inWindow(GarminMatcher.SMART_MATCH_SINCE.minusDays(1), today))
        assertFalse(GarminMatcher.inWindow(today.plusDays(10).minusDays(8), today.plusDays(10)))
        assertFalse(GarminMatcher.inWindow(today.plusDays(1), today))
    }
}
