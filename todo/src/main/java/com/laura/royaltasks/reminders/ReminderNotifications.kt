package com.laura.royaltasks.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.laura.royaltasks.MainActivity
import com.laura.royaltasks.R
import com.laura.royaltasks.data.Task

object ReminderNotifications {
    private const val CHANNEL_ID = "task_reminders"
    private const val ROYAL_PURPLE = 0xFF6C3CE9.toInt()

    fun notificationId(taskId: String): Int = taskId.hashCode()

    fun canPost(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context, task: Task) {
        if (!canPost(context)) return
        ensureChannel(context)
        val id = notificationId(task.id)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun action(action: String) = PendingIntent.getBroadcast(
            context,
            0,
            ReminderScheduler.reminderIntent(context, task.id, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ROYAL_PURPLE)
            .setContentTitle(task.title)
            .setContentText("Tick it off for ${task.priority.xp} XP.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Done", action(ReminderScheduler.ACTION_DONE))
            .addAction(0, "Snooze 1 hour", action(ReminderScheduler.ACTION_SNOOZE))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post: nothing to show.
        }
    }

    fun cancel(context: Context, taskId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(taskId))
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Reminders you set on tasks" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
