package com.laurasheehan.royalmiles.data.coach

import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity

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
