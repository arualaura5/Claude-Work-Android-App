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
    version = 3,
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

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "royalmiles.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
