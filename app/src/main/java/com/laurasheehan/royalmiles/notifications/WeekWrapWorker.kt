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
import com.laurasheehan.royalmiles.core.gamification.CompletedSession
import com.laurasheehan.royalmiles.core.progress.WeekSummaries
import com.laurasheehan.royalmiles.data.AppDatabase
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * Sunday evening: what the week held.
 *
 * Sunday is the last day of the week everywhere in this app's data model and nothing used to happen
 * on it. This is the one guaranteed positive moment in the week, and it arrives whether or not the
 * week went well — so it only ever states what was done. There is no denominator, nothing about
 * what was missed, and a week with nothing logged produces silence rather than a zero.
 */
class WeekWrapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessions = AppDatabase.getInstance(applicationContext).sessionDao().observeAll().first()
        val completions = sessions.filter { it.isCompleted }.map {
            CompletedSession(
                date = it.completedAt ?: it.date,
                type = it.type,
                phase = it.phase,
                distanceKm = it.actualDistanceKm ?: it.targetDistanceKm,
                durationMin = it.actualDurationMin ?: it.targetDurationMin,
            )
        }

        val summary = WeekSummaries.summarise(completions, WeekSummaries.weekCommencing(LocalDate.now()))
        if (summary.isEmpty) return Result.success()

        val title = buildString {
            append(summary.sessions)
            append(if (summary.sessions == 1) " session this week" else " sessions this week")
            if (summary.distanceKm > 0) append(" · ${formatKm(summary.distanceKm)}km")
        }
        val body = summary.longestRunKm?.let { longest ->
            if (summary.isFurthestYet) {
                "Longest run ${formatKm(longest)}km — furthest you've been since starting."
            } else {
                "Longest run ${formatKm(longest)}km. That's banked."
            }
        } ?: "That's the week. It's banked."

        showNotification(title, body)
        return Result.success()
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
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            android.content.Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.WRAP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(WRAP_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val WRAP_NOTIFICATION_ID = 1002
    }
}
