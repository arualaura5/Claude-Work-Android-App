package com.laurasheehan.royalmiles

import android.app.Application
import androidx.room.withTransaction
import com.laurasheehan.royalmiles.data.AppDatabase
import com.laurasheehan.royalmiles.data.AthleteProfileRepository
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.coach.CoachRepository
import com.laurasheehan.royalmiles.data.update.AppUpdater
import com.laurasheehan.royalmiles.notifications.ReminderScheduler
import java.time.Instant

class RoyalMilesApp : Application() {
    lateinit var repository: PlanRepository
        private set

    lateinit var athleteProfileRepository: AthleteProfileRepository
        private set

    lateinit var coachRepository: CoachRepository
        private set

    /** Checks GitHub for a newer build; needs no database, so it exists from the start. */
    val updater: AppUpdater by lazy { AppUpdater(this) }

    @Volatile
    var dependenciesInitialized: Boolean = false
        private set

    override fun onCreate() {
        StartupCrashStore.installHandler(this)
        super.onCreate()

        // Leave the data layer untouched while a previous startup crash is waiting to be shown.
        // MainActivity will initialise it after the report has been dismissed.
        if (!StartupCrashStore.hasReport(this)) {
            initializeDependencies()
        }
    }

    @Synchronized
    fun initializeDependencies() {
        if (dependenciesInitialized) return

        val database = AppDatabase.getInstance(this)
        repository = PlanRepository(
            database.sessionDao(),
            database.planMetaDao(),
            runInTransaction = { block -> database.withTransaction { block() } },
        )
        athleteProfileRepository = AthleteProfileRepository(database.athleteProfileDao())
        coachRepository = CoachRepository(applicationContext)

        ReminderScheduler.createChannel(this)
        ReminderScheduler.schedule(this)
        dependenciesInitialized = true
    }
}

internal object StartupCrashStore {
    private const val FILE_NAME = "startup-crash.txt"

    fun installHandler(app: Application) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = buildString {
                    appendLine("Timestamp: ${Instant.now()}")
                    appendLine("Build: ${BuildConfig.GIT_SHA}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(error.stackTraceToString())
                }
                runCatching { reportFile(app).writeText(report) }
            } finally {
                previousHandler?.uncaughtException(thread, error)
            }
        }
    }

    fun hasReport(app: Application): Boolean = reportFile(app).exists()

    fun read(app: Application): String? {
        val file = reportFile(app)
        if (!file.exists()) return null
        return runCatching { file.readText() }
            .getOrElse { error -> "The crash report could not be read: ${error.message}" }
    }

    fun delete(app: Application) {
        reportFile(app).delete()
    }

    private fun reportFile(app: Application) = app.filesDir.resolve(FILE_NAME)
}
