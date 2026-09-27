package com.laurasheehan.royalmiles.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The committed schema (app/schemas/.../<version>.json) must match what the app actually
 * builds, or future upgrade tests would be testing the wrong starting point.
 *
 * When the database version goes up: add the Migration to AppDatabase.ALL_MIGRATIONS, commit
 * the new schema JSON, and add a test here that creates the previous version with data in it,
 * runs `helper.runMigrationsAndValidate(DB, NEW_VERSION, true, migration)`, and checks every
 * row came through.
 */
@RunWith(AndroidJUnit4::class)
class SchemaTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun theCommittedSchemaMatchesTheAppAndOpensWithItsRows() {
        helper.createDatabase(DB, AppDatabase.VERSION).apply {
            execSQL(
                "INSERT INTO events (id, name, raceDate, raceDistanceKm, peakLongRunKm, planStartDate, planVersion) " +
                    "VALUES ('royal-parks-2026', 'Royal Parks Half', '2026-10-11', 21.1, 16.0, NULL, 0)",
            )
            execSQL(
                "INSERT INTO sessions (eventId, date, type, title, phase, weekNumber, optional, notes, isCompleted, " +
                    "isCustom, isSkipped, supersededByCoach) " +
                    "VALUES ('royal-parks-2026', '2026-09-28', 'EASY_RUN', 'Easy run', 'BUILD', 1, 0, '', 0, 0, 0, 0)",
            )
            close()
        }
        helper.runMigrationsAndValidate(DB, AppDatabase.VERSION, true)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.open(context, DB)
        try {
            val sessions = runBlocking { db.sessionDao().getAll() }
            assertEquals(listOf("Easy run"), sessions.map { it.title })
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB = "schema-test.db"
        const val UPGRADE_DB = "upgrade-10-11.db"
    }

    @Test
    fun version10UpgradesTo11KeepingEverySession() {
        helper.createDatabase(UPGRADE_DB, 10).apply {
            execSQL(
                "INSERT INTO events (id, name, raceDate, raceDistanceKm, peakLongRunKm, planStartDate, planVersion) " +
                    "VALUES ('royal-parks-2026', 'Royal Parks Half', '2026-10-11', 21.1, 16.0, NULL, 0)",
            )
            for (day in 1..3) {
                execSQL(
                    "INSERT INTO sessions (eventId, date, type, title, phase, weekNumber, optional, notes, isCompleted, " +
                        "isCustom, isSkipped, supersededByCoach, actualDistanceKm, sourceActivityId) " +
                        "VALUES ('royal-parks-2026', '2026-09-0$day', 'EASY_RUN', 'Run $day', 'BUILD', 1, 0, '', 1, 0, 0, 0, 6.2, 'g$day')",
                )
            }
            close()
        }
        val upgraded = helper.runMigrationsAndValidate(UPGRADE_DB, 11, true, AppDatabase.MIGRATION_10_11)
        upgraded.query("SELECT COUNT(*), SUM(actualDistanceKm) FROM sessions").use {
            it.moveToFirst()
            assertEquals(3, it.getInt(0))
            assertEquals(18.6, it.getDouble(1), 0.001)
        }
        upgraded.query("SELECT COUNT(*) FROM garmin_decisions").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        upgraded.close()
    }
}
