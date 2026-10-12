package com.laura.royaltasks.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.laura.royaltasks.data.Task
import com.laura.royaltasks.data.nextOccurrence
import java.time.ZoneId

/**
 * Keeps one alarm per open task with a reminder, always for its next
 * occurrence. [sync] is idempotent: it's run whenever the task list changes,
 * on app start, after a reboot or update, and after each reminder fires.
 */
class ReminderScheduler(context: Context) {
    private val app = context.applicationContext
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val prefs = app.getSharedPreferences("reminder_alarms", Context.MODE_PRIVATE)

    fun sync(tasks: List<Task>, nowMillis: Long = System.currentTimeMillis()) {
        val zone = ZoneId.systemDefault()
        val scheduled = mutableSetOf<String>()
        tasks.forEach { task ->
            val reminder = task.reminder
            if (task.isDone || reminder == null) return@forEach
            val at = nextOccurrence(reminder, nowMillis, zone) ?: return@forEach
            schedule(task.id, at, ACTION_FIRE)
            scheduled += task.id
        }
        // Alarms for tasks that were ticked, deleted or had their reminder removed.
        val previous = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        (previous - scheduled).forEach { cancel(it) }
        prefs.edit().putStringSet(KEY_IDS, scheduled).apply()
    }

    fun snooze(taskId: String, atMillis: Long) = schedule(taskId, atMillis, ACTION_SNOOZE_FIRE)

    private fun schedule(taskId: String, atMillis: Long, action: String) {
        val pending = pendingIntent(taskId, action, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            // Without exact-alarm access Android may deliver it a few minutes late.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    private fun cancel(taskId: String) {
        pendingIntent(taskId, ACTION_FIRE, PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(taskId: String, action: String, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            app,
            0,
            reminderIntent(app, taskId, action),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val ACTION_FIRE = "com.laura.royaltasks.REMINDER_FIRE"
        const val ACTION_SNOOZE_FIRE = "com.laura.royaltasks.REMINDER_SNOOZE_FIRE"
        const val ACTION_DONE = "com.laura.royaltasks.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.laura.royaltasks.REMINDER_SNOOZE"
        const val EXTRA_TASK_ID = "task_id"
        private const val KEY_IDS = "scheduled_task_ids"

        /** The data URI keeps each task's (and each action's) PendingIntent distinct. */
        fun reminderIntent(context: Context, taskId: String, action: String): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("royaltasks://$action/$taskId"))
                .putExtra(EXTRA_TASK_ID, taskId)
    }
}
