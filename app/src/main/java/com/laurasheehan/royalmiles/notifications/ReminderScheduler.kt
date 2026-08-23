package com.laurasheehan.royalmiles.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val CHANNEL_ID = "training_reminders"
    private const val WORK_NAME = "daily_training_reminder"
    private val REMINDER_TIME: LocalTime = LocalTime.of(19, 0)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Training reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A gentle evening nudge if today's session hasn't been logged yet."
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var firstRun = now.toLocalDate().atTime(REMINDER_TIME)
        if (firstRun.isBefore(now)) firstRun = firstRun.plusDays(1)
        val initialDelay = ChronoUnit.MINUTES.between(now, firstRun)

        val request = PeriodicWorkRequestBuilder<TrainingReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
