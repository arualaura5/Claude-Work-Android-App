package com.laurasheehan.royalmiles

import android.app.Application
import com.laurasheehan.royalmiles.data.AppDatabase
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.notifications.ReminderScheduler

class RoyalMilesApp : Application() {
    lateinit var repository: PlanRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        repository = PlanRepository(database.sessionDao(), database.planMetaDao())

        ReminderScheduler.createChannel(this)
        ReminderScheduler.schedule(this)
    }
}
