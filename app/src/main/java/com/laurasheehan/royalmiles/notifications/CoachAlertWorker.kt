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
import com.laurasheehan.royalmiles.RoyalMilesApp
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import com.laurasheehan.royalmiles.data.coach.CoachState
import com.laurasheehan.royalmiles.data.coach.CoachSuggestionDecisionStore
import com.laurasheehan.royalmiles.ui.dashboard.visibleCoachSuggestion
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Checks the cloud coach every few hours and tells her once when it suggests changing her plan.
 * Nothing else notifies: most days there is no change, and the app stays quiet.
 */
class CoachAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RoyalMilesApp
        app.initializeDependencies()
        // Offline or not connected: try again next time, quietly.
        app.coachRepository.refreshFromRememberedSource()
        val payload = (app.coachRepository.state.value as? CoachState.Loaded)?.payload ?: return Result.success()
        val suggestion = payload.coaching?.suggestion ?: return Result.success()

        val sessions = app.repository.observeSessions().first()
        val decisions = CoachSuggestionDecisionStore(applicationContext)
        val visible = visibleCoachSuggestion(suggestion, sessions, LocalDate.now(), decisions) ?: return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = CoachAlerts.key(visible.suggestion)
        if (prefs.getStringSet(NOTIFIED, emptySet()).orEmpty().contains(key)) return Result.success()

        if (show(CoachAlerts.title(visible.suggestion, LocalDate.now()), CoachAlerts.body(visible.suggestion))) {
            val notified = prefs.getStringSet(NOTIFIED, emptySet()).orEmpty().toMutableSet().apply { add(key) }
            prefs.edit().putStringSet(NOTIFIED, notified.toList().takeLast(30).toSet()).apply()
        }
        return Result.success()
    }

    private fun show(title: String, body: String): Boolean {
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            android.content.Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.COACH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(COACH_NOTIFICATION_ID, notification)
        return true
    }

    private companion object {
        const val COACH_NOTIFICATION_ID = 1003
        const val PREFS = "coach_alerts"
        const val NOTIFIED = "notified"
    }
}

/** What the notification says. Pure, so it can be tested. */
object CoachAlerts {
    private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH)

    /** One notification per suggestion: the same day and wording never notify twice. */
    fun key(suggestion: CoachPayload.Coaching.Suggestion) = "${suggestion.date}|${suggestion.headline}"

    fun title(suggestion: CoachPayload.Coaching.Suggestion, today: LocalDate): String {
        val date = runCatching { LocalDate.parse(suggestion.date) }.getOrNull()
        val day = when (date) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            null -> "your plan"
            else -> date.format(dayFormat)
        }
        return "Your coach suggests a change for $day"
    }

    fun body(suggestion: CoachPayload.Coaching.Suggestion): String =
        listOfNotNull(suggestion.headline, suggestion.reason?.takeIf { it.isNotBlank() }).joinToString(" ") +
            " Open Royal Miles to accept or keep your plan."
}
