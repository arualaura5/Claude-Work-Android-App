package com.laurasheehan.royalmiles

import android.app.Application
import androidx.room.withTransaction
import com.laurasheehan.royalmiles.data.AppDatabase
import com.laurasheehan.royalmiles.data.AthleteProfileRepository
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.coach.CoachRepository
import com.laurasheehan.royalmiles.notifications.ReminderScheduler

class RoyalMilesApp : Application() {
    lateinit var repository: PlanRepository
        private set

    lateinit var athleteProfileRepository: AthleteProfileRepository
        private set

    lateinit var coachRepository: CoachRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        repository = PlanRepository(
            database.sessionDao(),
            database.planMetaDao(),
            transaction = { block -> database.withTransaction { block() } },
        )
        athleteProfileRepository = AthleteProfileRepository(database.athleteProfileDao())
        coachRepository = CoachRepository(applicationContext)

        ReminderScheduler.createChannel(this)
        ReminderScheduler.schedule(this)
    }
}
