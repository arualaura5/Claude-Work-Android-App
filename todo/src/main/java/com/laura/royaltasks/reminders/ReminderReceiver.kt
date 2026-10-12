package com.laura.royaltasks.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.laura.royaltasks.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Alarms and notification buttons. Not exported: only this app's own PendingIntents reach it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handle(app, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        val repo = TaskRepository(context)
        val scheduler = ReminderScheduler(context)
        when (intent.action) {
            ReminderScheduler.ACTION_FIRE, ReminderScheduler.ACTION_SNOOZE_FIRE -> {
                val tasks = repo.tasks.first()
                // Ticked or deleted since the alarm was set: stay quiet.
                val task = tasks.find { it.id == taskId && !it.isDone } ?: return
                if (intent.action == ReminderScheduler.ACTION_FIRE && task.reminder == null) return
                ReminderNotifications.show(context, task)
                // Line up the next one for a repeating reminder.
                scheduler.sync(tasks)
            }
            ReminderScheduler.ACTION_DONE -> {
                repo.complete(taskId, LocalDate.now().toEpochDay())
                ReminderNotifications.cancel(context, taskId)
                scheduler.sync(repo.tasks.first())
            }
            ReminderScheduler.ACTION_SNOOZE -> {
                ReminderNotifications.cancel(context, taskId)
                scheduler.snooze(taskId, System.currentTimeMillis() + SNOOZE_MILLIS)
            }
        }
    }

    private companion object {
        const val SNOOZE_MILLIS = 60 * 60 * 1000L
    }
}

/** Alarms don't survive a reboot; put them back. Also after updates and clock changes. */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ReminderScheduler(app).sync(TaskRepository(app).tasks.first())
            } finally {
                pending.finish()
            }
        }
    }
}
