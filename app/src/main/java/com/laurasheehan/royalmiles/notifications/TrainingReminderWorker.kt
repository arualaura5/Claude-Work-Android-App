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
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.data.AppDatabase
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * Runs once a day in the morning. Stays silent unless today has an unlogged, non-optional session —
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
        showNotification(title, encouragementFor(today, outstanding))
        return Result.success()
    }

    /**
     * Varied rather than the same line every day, and specific on Sunday. A notification that reads
     * identically on a Wednesday strength session and on long-run day stops being read at all.
     */
    private fun encouragementFor(today: LocalDate, outstanding: List<com.laurasheehan.royalmiles.data.SessionEntity>): String {
        val longRun = outstanding.firstOrNull { it.type == SessionType.LONG_RUN }
        if (longRun != null) {
            val distance = longRun.targetDistanceKm
            return if (distance != null) {
                "Long run day — ${formatKm(distance)}km, easy effort. Time on feet, not pace."
            } else {
                "Long run day. Easy effort, time on feet."
            }
        }
        if (outstanding.any { it.type == SessionType.RACE }) return "This is it. Trust the training."
        val pool = listOf(
            "No pressure — just showing up counts.",
            "Doesn't have to be good. It just has to happen.",
            "Thirty minutes from now this is already done.",
            "Nobody sees this one but you. It still counts.",
            "One more brick in the foundation.",
        )
        return pool[today.toEpochDay().mod(pool.size)]
    }

    private fun formatKm(value: Double): String {
        val rounded = kotlin.math.round(value * 10) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    private fun showNotification(title: String, body: String) {
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
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(REMINDER_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val REMINDER_NOTIFICATION_ID = 1001
    }
}
