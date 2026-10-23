package com.laurasheehan.royalmiles.data.coach

import com.laurasheehan.royalmiles.RaceConfig
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import java.time.LocalDate
import kotlin.math.abs

/**
 * Applies a coach suggestion she has accepted, whether it came from the daily coaching or from
 * chat. One path for both, so an accepted chat proposal is recorded exactly like a daily one.
 * Returns false when nothing was changed.
 */
suspend fun PlanRepository.applyCoachSuggestion(
    suggestion: CoachPayload.Coaching.Suggestion,
    session: SessionEntity,
): Boolean = when (suggestion.action) {
    CoachPayload.Coaching.SuggestionAction.SKIP -> {
        markSkipped(session.id)
        true
    }
    // Logged with logFromCoach, which needs no planned session.
    CoachPayload.Coaching.SuggestionAction.LOG -> false
    CoachPayload.Coaching.SuggestionAction.REPLACE -> {
        val replacement = suggestion.replaceWith
        if (replacement == null) {
            false
        } else {
            acceptCoachReplacement(
                replacedSessionId = session.id,
                reason = suggestion.reason,
                replacement = SessionEntity(
                    eventId = session.eventId,
                    date = session.date,
                    type = replacement.type,
                    title = replacement.title,
                    phase = session.phase,
                    weekNumber = session.weekNumber,
                    targetDistanceKm = replacement.targetDistanceKm,
                    targetDurationMin = replacement.targetDurationMin,
                    // notes is optional in the payload but not nullable on the entity.
                    // acceptCoachReplacement rebuilds this field with the full record anyway.
                    notes = replacement.notes.orEmpty(),
                ),
            )
        }
    }
}

/**
 * A session she did that Garmin didn't record, logged as done once she taps Log it: against the
 * planned session of that kind that day if there is one, otherwise as an extra. Only what she told
 * the coach is recorded; no planned figure stands in for one she didn't give.
 */
suspend fun PlanRepository.logFromCoach(
    suggestion: CoachPayload.Coaching.Suggestion,
    sessions: List<SessionEntity>,
): Boolean {
    if (suggestion.action != CoachPayload.Coaching.SuggestionAction.LOG) return false
    val done = suggestion.replaceWith ?: return false
    val date = runCatching { LocalDate.parse(suggestion.date) }.getOrNull() ?: return false
    val note = done.notes?.let { "From coach chat: $it" } ?: "From coach chat: not recorded by Garmin."
    val planned = sessions.firstOrNull { it.date == date && it.isOutstanding && it.type == done.type }
    if (planned != null) {
        updateSession(
            planned.copy(
                isCompleted = true,
                isSkipped = false,
                completedAt = date,
                actualDistanceKm = done.targetDistanceKm,
                actualDurationMin = done.targetDurationMin,
                notes = listOf(planned.notes, note).filter { it.isNotBlank() }.joinToString("\n"),
            ),
        )
        return true
    }
    val template = sessions.minByOrNull { abs(it.date.toEpochDay() - date.toEpochDay()) }
    addCustomSession(
        SessionEntity(
            eventId = template?.eventId ?: RaceConfig.ACTIVE_EVENT_ID,
            date = date,
            type = done.type,
            title = done.title,
            phase = template?.phase ?: TrainingPhase.BASE,
            weekNumber = template?.weekNumber ?: 1,
            notes = note,
            isCompleted = true,
            completedAt = date,
            actualDistanceKm = done.targetDistanceKm,
            actualDurationMin = done.targetDurationMin,
        ),
        eventId = template?.eventId ?: RaceConfig.ACTIVE_EVENT_ID,
    )
    return true
}
