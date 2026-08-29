package com.laurasheehan.royalmiles.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.laurasheehan.royalmiles.MainActivity
import com.laurasheehan.royalmiles.R
import com.laurasheehan.royalmiles.data.AppDatabase
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * Runs once a day in the evening. Stays silent unless today has an unlogged, non-optional session —
 * the point is a gentle nudge, not a daily ping regardless of whether there's anything to nudge about.
 */
class TrainingReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionDao = AppDatabase.getInstance(applicationContext).sessionDao()
        val today = LocalDate.now()
        val sessions = sessionDao.observeAll().first()
        val outstanding = sessions.filter {
            it.date == today && it.isLoggable && !it.optional && it.isOutstanding
        }
        if (outstanding.isEmpty()) return Result.success()

        val title = if (outstanding.size == 1) {
            "Today: ${outstanding.first().title}"
        } else {
            "${outstanding.size} sessions on today's plan"
        }
        showNotification(title)
        return Result.success()
    }

    private fun showNotification(title: String) {
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openIntent = android.content.Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("No pressure — just showing up counts.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(REMINDER_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val REMINDER_NOTIFICATION_ID = 1001
    }
}
