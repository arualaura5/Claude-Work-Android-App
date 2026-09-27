package com.laurasheehan.royalmiles.ui.garmin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.ExerciseSessionRecord
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.garmin.ActivityKind
import com.laurasheehan.royalmiles.data.garmin.AskReason
import com.laurasheehan.royalmiles.data.garmin.AutoLink
import com.laurasheehan.royalmiles.data.garmin.GarminDecision
import com.laurasheehan.royalmiles.data.garmin.GarminDecisionEntity
import com.laurasheehan.royalmiles.data.garmin.GarminInboxState
import com.laurasheehan.royalmiles.data.garmin.MatchResult
import com.laurasheehan.royalmiles.data.garmin.PendingActivity
import com.laurasheehan.royalmiles.data.health.ExternalWorkout
import com.laurasheehan.royalmiles.ui.theme.RoyalMilesTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class GarminInboxScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private val today = LocalDate.of(2026, 9, 27)

    private fun session(id: Long, type: SessionType, title: String, date: LocalDate, km: Double?) = SessionEntity(
        id = id, eventId = "e", date = date, type = type, title = title, phase = TrainingPhase.BUILD, weekNumber = 3, targetDistanceKm = km,
    )

    private fun workout(type: Int, km: Double, minutes: Long, hr: Int, title: String) = ExternalWorkout(
        start = today.atTime(11, 0).toInstant(ZoneOffset.UTC),
        end = today.atTime(11, 0).toInstant(ZoneOffset.UTC).plusSeconds(minutes * 60),
        exerciseType = type,
        title = title,
        distanceKm = km,
        avgHeartRate = hr,
        sourceActivityId = "1",
    )

    @Test
    fun garminCards() {
        val easy = session(1, SessionType.EASY_RUN, "Easy run", today, 9.0)
        val long = session(2, SessionType.LONG_RUN, "Long run", today, 16.0)
        val tuesdayRun = session(3, SessionType.EASY_RUN, "Easy run", today.plusDays(2), 8.0)
        val state = GarminInboxState(
            autoLinked = listOf(
                AutoLink(
                    GarminDecisionEntity(
                        activityId = "21001", decision = GarminDecision.AUTO_LINKED, sessionId = 1, activityDate = today,
                        activityName = "Tower Hamlets Running", activityKind = "RUN", distanceKm = 9.03, durationMin = 62,
                        avgHeartRate = 151, maxHeartRate = 168, previousSessionJson = null, replacedSessionJson = null,
                        createdSessionId = null, decidedAtMillis = 0,
                    ),
                    easy,
                ),
            ),
            pending = listOf(
                PendingActivity(
                    workout(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 5.1, 31, 142, "Tower Hamlets Running"),
                    ActivityKind.RUN,
                    MatchResult.Ask(AskReason.DIFFERENT_SIZE, listOf(long), listOf(long), listOf(tuesdayRun)),
                ),
            ),
        )
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                Box(Modifier.background(MaterialTheme.colorScheme.background).fillMaxWidth().padding(16.dp)) {
                    GarminInboxCards(state, GarminInboxActions(), today)
                }
            }
        }
    }

    @Test
    fun bikeInsteadOfRun() {
        val easy = session(1, SessionType.EASY_RUN, "Easy run", today, 9.0)
        val state = GarminInboxState(
            pending = listOf(
                PendingActivity(
                    workout(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 24.6, 58, 128, "Tower Hamlets Cycling"),
                    ActivityKind.CYCLE,
                    MatchResult.Ask(AskReason.DIFFERENT_KIND, listOf(easy), emptyList(), emptyList()),
                ),
            ),
        )
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                Box(Modifier.background(MaterialTheme.colorScheme.background).fillMaxWidth().padding(16.dp)) {
                    GarminInboxCards(state, GarminInboxActions(), today)
                }
            }
        }
    }
}
