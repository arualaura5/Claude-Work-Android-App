package com.laurasheehan.royalmiles.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.AppDatabase
import com.laurasheehan.royalmiles.data.AthleteProfileEntity
import com.laurasheehan.royalmiles.data.EventEntity
import com.laurasheehan.royalmiles.data.PlanMetaEntity
import com.laurasheehan.royalmiles.data.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Backup and restore on a real Android SQLite, against the app's real database class. Runs in
 * CI on an emulator (android-build.yml), because nothing on the JVM behaves like the device.
 */
@RunWith(AndroidJUnit4::class)
class LogBackupTest {

    private lateinit var context: Context
    private lateinit var name: String
    private lateinit var db: AppDatabase
    private lateinit var safety: SafetyCopies
    private lateinit var backup: LogBackup

    private val event = EventEntity("royal-parks-2026", "Royal Parks Half", LocalDate.of(2026, 10, 11), 21.1, 16.0, LocalDate.of(2026, 7, 1), 2)

    private fun session(day: Int, done: Boolean, km: Double? = null) = SessionEntity(
        eventId = event.id,
        date = LocalDate.of(2026, 9, day),
        type = if (day % 2 == 0) SessionType.EASY_RUN else SessionType.STRENGTH,
        title = "Session $day",
        phase = TrainingPhase.BUILD,
        weekNumber = 1,
        targetDistanceKm = 6.0,
        isCompleted = done,
        completedAt = if (done) LocalDate.of(2026, 9, day) else null,
        actualDistanceKm = km,
        effortRating = if (done) 4 else null,
        sourceActivityId = if (km != null) "garmin-$day" else null,
    )

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "backups").deleteRecursively()
        name = "backup-test-${UUID.randomUUID()}.db"
        db = AppDatabase.open(context, name)
        db.eventDao().upsert(event)
        db.planMetaDao().upsert(PlanMetaEntity(raceDate = event.raceDate, startDate = LocalDate.of(2026, 7, 1), raceDistanceKm = 21.1, peakLongRunKm = 16.0, planVersion = 2))
        db.athleteProfileDao().upsert(AthleteProfileEntity(bodyWeightKg = 61.4))
        db.sessionDao().insertAll(listOf(session(1, true, 6.4), session(2, true), session(3, false), session(20, false)))
        safety = SafetyCopies(context)
        backup = LogBackup(context, db, safety)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
        File(context.filesDir, "backups").deleteRecursively()
    }

    private fun exported(): ByteArray = runBlocking { ByteArrayOutputStream().also { backup.export(it) }.toByteArray() }

    private fun inspect(bytes: ByteArray): Inspection = runBlocking { backup.inspect(ByteArrayInputStream(bytes)) }

    /** Edits a backup file the way a damaged or foreign one might differ. */
    private fun tampered(bytes: ByteArray, change: (SQLiteDatabase) -> Unit): ByteArray {
        val file = File(context.cacheDir, "tamper-${UUID.randomUUID()}.db").apply { writeBytes(bytes) }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use(change)
        return file.readBytes().also { BackupFiles.delete(file) }
    }

    @Test
    fun aBackupRestoresEveryRowExactly() = runBlocking {
        val before = db.sessionDao().getAll()
        val bytes = exported()

        // Her log changes after the backup: one session deleted, one edited, one added.
        db.sessionDao().delete(before[0])
        db.sessionDao().update(before[1].copy(actualDistanceKm = 99.0))
        db.sessionDao().insert(session(25, false))

        val ready = inspect(bytes) as Inspection.Ready
        assertEquals(4, ready.preview.sessions)
        assertEquals(2, ready.preview.completed)
        assertEquals(LocalDate.of(2026, 9, 1), ready.preview.firstDate)
        assertNull(ready.preview.upgradedFrom)

        backup.restore(ready)
        assertEquals(before, db.sessionDao().getAll())
        assertEquals(listOf(event), db.eventDao().getAll())
        assertEquals(61.4, db.athleteProfileDao().observe().first()?.bodyWeightKg)
        assertEquals(2, db.planMetaDao().get()?.planVersion)
    }

    @Test
    fun restoringKeepsACopyOfTheLogItReplaced() = runBlocking {
        val bytes = exported()
        db.sessionDao().insert(session(26, true, 8.0))
        backup.restore(inspect(bytes) as Inspection.Ready)

        val copy = safety.list().single { it.kind == SafetyCopies.BEFORE_RESTORE }
        val undo = backup.inspect(copy) as Inspection.Ready
        assertEquals(5, undo.preview.sessions)
    }

    @Test
    fun aDamagedOrTruncatedFileIsRefusedAndChangesNothing() = runBlocking {
        val before = db.sessionDao().getAll()
        val bytes = exported()
        assertTrue(inspect(bytes.copyOf(bytes.size / 2)) is Inspection.Problem)
        assertTrue(inspect(ByteArray(4096) { it.toByte() }) is Inspection.Problem)
        assertTrue(inspect("not a backup".toByteArray()) is Inspection.Problem)
        assertEquals(before, db.sessionDao().getAll())
    }

    @Test
    fun aBackupFromANewerAppIsRefused() {
        val newer = tampered(exported()) { it.version = AppDatabase.VERSION + 1 }
        val problem = inspect(newer) as Inspection.Problem
        assertTrue(problem.message, "newer version" in problem.message)
    }

    @Test
    fun aBackupMissingRowsIsRefused() {
        val incomplete = tampered(exported()) { it.execSQL("DELETE FROM sessions WHERE date = '2026-09-03'") }
        val problem = inspect(incomplete) as Inspection.Problem
        assertTrue(problem.message, "incomplete" in problem.message)
    }

    @Test
    fun aDatabaseThatIsNotABackupIsRefused() {
        val noManifest = tampered(exported()) { it.execSQL("DROP TABLE ${Manifest.TABLE}") }
        assertTrue(inspect(noManifest) is Inspection.Problem)
    }

    @Test
    fun aCopyIsKeptBeforeAnUpgradeRunsAndOnlyThen() {
        val old = File(context.cacheDir, "old-${UUID.randomUUID()}.db")
        SQLiteDatabase.openOrCreateDatabase(old, null).use { raw ->
            raw.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY, date TEXT)")
            raw.execSQL("INSERT INTO sessions (date) VALUES ('2026-09-01')")
            raw.version = AppDatabase.VERSION - 1
        }
        safety.beforeUpgrade(old, AppDatabase.VERSION)
        val copies = safety.list()
        assertEquals(1, copies.size)
        assertEquals("${SafetyCopies.BEFORE_UPGRADE}-v${AppDatabase.VERSION - 1}-to-v${AppDatabase.VERSION}", copies[0].kind)

        safety.beforeUpgrade(old, AppDatabase.VERSION - 1)
        assertEquals("no copy when nothing will be upgraded", 1, safety.list().size)
        BackupFiles.delete(old)
    }
}
