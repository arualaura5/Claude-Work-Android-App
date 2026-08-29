package com.laurasheehan.royalmiles.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val CHANNEL_ID = "training_reminders"
    const val WRAP_CHANNEL_ID = "week_wrap"
    private const val WORK_NAME = "daily_training_reminder"
    private const val WRAP_WORK_NAME = "weekly_wrap"

    /**
     * Morning, not evening.
     *
     * This used to fire at 19:00, by which time the decision about whether today's run happened had
     * already been made — so the notification arrived as a verdict on a failure rather than a prompt
     * to act. 07:30 is an invitation.
     */
    private val REMINDER_TIME: LocalTime = LocalTime.of(7, 30)

    /** Sunday evening, once the week is done. */
    private val WRAP_TIME: LocalTime = LocalTime.of(19, 0)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Training reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A gentle morning nudge if today's session hasn't been logged yet."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(WRAP_CHANNEL_ID, "Weekly wrap", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A Sunday-evening summary of the week you just trained."
            },
        )
    }

    fun schedule(context: Context) {
        val now = LocalDateTime.now()

        var firstRun = now.toLocalDate().atTime(REMINDER_TIME)
        if (firstRun.isBefore(now)) firstRun = firstRun.plusDays(1)
        val daily = PeriodicWorkRequestBuilder<TrainingReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(ChronoUnit.MINUTES.between(now, firstRun), TimeUnit.MINUTES)
            .build()

        var firstWrap = now.toLocalDate()
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .atTime(WRAP_TIME)
        if (firstWrap.isBefore(now)) firstWrap = firstWrap.plusWeeks(1)
        val weekly = PeriodicWorkRequestBuilder<WeekWrapWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(ChronoUnit.MINUTES.between(now, firstWrap), TimeUnit.MINUTES)
            .build()

        // UPDATE, not KEEP. KEEP meant the existing 19:00 schedule survived every reinstall and
        // upgrade, so the time could never actually be changed without clearing app data.
        WorkManager.getInstance(context).apply {
            enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, daily)
            enqueueUniquePeriodicWork(WRAP_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, weekly)
        }
    }
}
