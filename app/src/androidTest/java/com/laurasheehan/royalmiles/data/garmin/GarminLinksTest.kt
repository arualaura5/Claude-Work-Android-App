package com.laurasheehan.royalmiles.data.garmin

import android.content.Context
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.AppDatabase
import com.laurasheehan.royalmiles.data.EventEntity
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.coach.CoachRepository
import com.laurasheehan.royalmiles.data.health.ExternalWorkout
import com.laurasheehan.royalmiles.data.health.GARMIN_PACKAGE
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Linking, swapping, extras and undo against the real database, on an emulator in CI. */
@RunWith(AndroidJUnit4::class)
class GarminLinksTest {

    private lateinit var context: Context
    private lateinit var name: String
    private lateinit var db: AppDatabase
    private lateinit var links: GarminLinks
    private val today = LocalDate.of(2026, 9, 27)

    private fun run(id: String, km: Double = 9.03, minutes: Long = 62, type: Int = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING): ExternalWorkout {
        val start = today.atTime(12, 17).atZone(ZoneId.systemDefault()).toInstant()
        return ExternalWorkout(
            start = start, end = start.plusSeconds(minutes * 60), exerciseType = type, title = "Tower Hamlets Running",
            distanceKm = km, avgHeartRate = 151, maxHeartRate = 168, calories = 612, sourceApp = GARMIN_PACKAGE, sourceActivityId = id,
        )
    }

    private fun planned(type: SessionType = SessionType.EASY_RUN, km: Double? = 9.0, date: LocalDate = today) = SessionEntity(
        eventId = "royal-parks-2026", date = date, type = type, title = if (type == SessionType.LONG_RUN) "Long run" else "Easy run",
        phase = TrainingPhase.BUILD, weekNumber = 3, targetDistanceKm = km, effortRating = null,
    )

    private fun session(id: Long) = runBlocking { db.sessionDao().getById(id)!! }

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        name = "garmin-test-${UUID.randomUUID()}.db"
        db = AppDatabase.open(context, name)
        db.eventDao().upsert(EventEntity("royal-parks-2026", "Royal Parks Half", LocalDate.of(2026, 10, 11), 21.1, 16.0, null))
        links = GarminLinks(db) { 1_000L }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun linkingRecordsGarminsFiguresAndUndoRestoresTheSessionExactly() = runBlocking {
        val id = db.sessionDao().insert(planned())
        val before = session(id)

        assertTrue(links.link(run("a1"), id, GarminDecision.AUTO_LINKED))
        val linked = session(id)
        assertTrue(linked.isCompleted)
        assertEquals(9.03, linked.actualDistanceKm!!, 0.0)
        assertEquals(62, linked.actualDurationMin)
        assertEquals(151, linked.actualAvgHeartRate)
        assertEquals("a1", linked.sourceActivityId)
        assertEquals(today, linked.completedAt)

        val decision = db.garminDecisionDao().activeFor("a1")!!
        assertEquals(GarminDecision.AUTO_LINKED, decision.decision)
        assertEquals(9.03, decision.distanceKm!!, 0.0)

        assertTrue(links.undo(decision.id))
        assertEquals(before, session(id))
        assertNull(db.garminDecisionDao().activeFor("a1"))
        assertEquals("the record is kept, marked undone", 1000L, db.garminDecisionDao().getById(decision.id)!!.undoneAtMillis)
    }

    @Test
    fun oneActivityOneSessionAndOnlyOutstandingOnes() = runBlocking {
        val first = db.sessionDao().insert(planned())
        val second = db.sessionDao().insert(planned())
        val done = db.sessionDao().insert(planned().copy(isCompleted = true, completedAt = today))

        assertTrue(links.link(run("a1"), first, GarminDecision.LINKED))
        assertFalse("the same activity can't be linked twice", links.link(run("a1"), second, GarminDecision.LINKED))
        assertFalse("a session can't take a second activity", links.link(run("a2"), first, GarminDecision.LINKED))
        assertFalse("a session she already ticked off isn't overwritten", links.link(run("a3"), done, GarminDecision.LINKED))
        assertFalse(session(done).sourceActivityId != null)
    }

    @Test
    fun aSwapReplacesThePlanAndUndoPutsItAllBack() = runBlocking {
        val longId = db.sessionDao().insert(planned(SessionType.LONG_RUN, km = 16.0))
        val before = session(longId)

        assertTrue(links.swap(run("s1", km = 5.1, minutes = 31), longId))
        val replaced = session(longId)
        assertTrue(replaced.isSkipped && replaced.supersededByCoach)
        val decision = db.garminDecisionDao().activeFor("s1")!!
        val created = session(decision.createdSessionId!!)
        assertTrue(created.isCompleted && created.isCustom)
        assertEquals(5.1, created.actualDistanceKm!!, 0.0)
        assertEquals(SessionType.EASY_RUN, created.type)
        assertTrue(created.notes.startsWith("Was: Long run."))

        assertTrue(links.undo(decision.id))
        assertEquals(before, session(longId))
        assertNull(db.sessionDao().getById(decision.createdSessionId!!))
    }

    @Test
    fun anExtraIsLoggedAndAnIgnoredOneIsRemembered() = runBlocking {
        db.sessionDao().insert(planned(SessionType.STRENGTH, km = null))
        assertTrue(links.extra(run("x1", type = ExerciseSessionRecord.EXERCISE_TYPE_BIKING, km = 24.6, minutes = 58)))
        val extra = session(db.garminDecisionDao().activeFor("x1")!!.createdSessionId!!)
        assertEquals(SessionType.CYCLE, extra.type)
        assertEquals("royal-parks-2026", extra.eventId)

        assertTrue(links.ignore(run("i1")))
        assertEquals(GarminDecision.IGNORED, db.garminDecisionDao().activeFor("i1")!!.decision)
        assertFalse("decided once, not again", links.extra(run("i1")))
    }

    @Test
    fun theInboxLinksAClearFitAndAsksAboutTheRest() = runBlocking {
        val easy = db.sessionDao().insert(planned())
        db.sessionDao().insert(planned(SessionType.LONG_RUN, km = 16.0, date = today.plusDays(1)))
        val inbox = GarminInbox(db, CoachRepository(context), links, today = { today })
        inbox.feed = listOf(
            run("clear"),
            run("dummy", km = 0.0, minutes = 0),
            run("bike", type = ExerciseSessionRecord.EXERCISE_TYPE_BIKING, km = 24.6, minutes = 58),
        )
        inbox.process()

        assertEquals("clear", session(easy).sourceActivityId)
        val state = inbox.state.value
        assertEquals(listOf("clear"), state.autoLinked.map { it.decision.activityId })
        assertEquals(listOf("bike"), state.pending.map { it.workout.sourceActivityId })

        // "Not this one": undone, and asked about instead of linked again.
        links.undo(state.autoLinked.single().decision.id)
        inbox.process()
        assertNull(session(easy).sourceActivityId)
        val asked = inbox.state.value.pending.first { it.workout.sourceActivityId == "clear" }
        assertEquals(AskReason.SHE_SAID_NOT_THIS, asked.ask.reason)
    }

    @Test
    fun her_watch_answer_fills_the_rating_once_linked_but_never_overrides_hers() = runBlocking {
        val easy = db.sessionDao().insert(planned())
        val rated = db.sessionDao().insert(planned(date = today.minusDays(2)))
        val inbox = GarminInbox(db, CoachRepository(context), links, today = { today })
        // Linked before Garmin had her answer.
        inbox.feed = listOf(run("today"))
        inbox.process()
        assertTrue(session(easy).isCompleted)
        assertNull(session(easy).effortRating)
        // Her answer arrives on a later refresh: it becomes the rating.
        inbox.feed = listOf(run("today").copy(watchFeel = 4))
        inbox.process()
        assertEquals(4, session(easy).effortRating)
        // A session she rated in the app keeps her rating.
        db.sessionDao().update(session(rated).copy(isCompleted = true, effortRating = 2, sourceActivityId = "earlier"))
        inbox.feed = listOf(run("earlier").copy(watchFeel = 5))
        inbox.fillFeelFromWatch(db.sessionDao().getAll())
        assertEquals(2, session(rated).effortRating)
    }
}
