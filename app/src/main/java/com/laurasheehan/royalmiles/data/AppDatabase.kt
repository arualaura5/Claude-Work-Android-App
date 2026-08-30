package com.laurasheehan.royalmiles.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SessionEntity::class, PlanMetaEntity::class, AthleteProfileEntity::class],
    version = 7,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun planMetaDao(): PlanMetaDao
    abstract fun athleteProfileDao(): AthleteProfileDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `athlete_profile` (`id` INTEGER NOT NULL, `bodyWeightKg` REAL, PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * The mid-week easy run moved from Wednesday to Tuesday, swapping with strength. The plan
         * generator only runs on a first install, so an existing plan has to be shifted in place.
         *
         * Both moves live in one statement: SQLite evaluates every SET expression against the row's
         * pre-update values, so the Tuesday and Wednesday cases can't cascade into each other.
         *
         * Three things are deliberately left alone — completed sessions (they record what actually
         * happened, on the day it happened), sessions added by hand (`isCustom`, not ours to move),
         * and race week (TAPER runs a bespoke countdown, not the weekly template).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE sessions
                       SET date = CASE
                             WHEN type = 'EASY_RUN' AND CAST(strftime('%w', date) AS INTEGER) = 3
                                  THEN date(date, '-1 day')
                             WHEN type = 'STRENGTH' AND CAST(strftime('%w', date) AS INTEGER) = 2
                                  THEN date(date, '+1 day')
                             ELSE date
                           END
                     WHERE isCompleted = 0
                       AND isCustom = 0
                       AND phase <> 'TAPER'
                       AND (
                             (type = 'EASY_RUN' AND CAST(strftime('%w', date) AS INTEGER) = 3)
                          OR (type = 'STRENGTH' AND CAST(strftime('%w', date) AS INTEGER) = 2)
                           )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Adds an explicit "didn't do this one" flag, so a missed session can be closed off rather
         * than sitting in Up next indefinitely. Defaults to 0, leaving every existing row untouched.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN isSkipped INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Richer Health Connect metrics per session, so training history accumulates over time. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN actualAvgHeartRate INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN actualMaxHeartRate INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN actualCalories INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN actualElevationGainM INTEGER")
            }
        }

        /** Provenance, so a completed session can still link back to the Garmin activity. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN sourceApp TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN sourceActivityId TEXT")
            }
        }

        /**
         * Tracks generated-plan shape independently of user training rows. Existing installs get
         * version 1 so app startup can atomically regenerate only future untouched planned rows.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_meta ADD COLUMN planVersion INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "royalmiles.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                )
                    .build().also { instance = it }
            }
    }
}
