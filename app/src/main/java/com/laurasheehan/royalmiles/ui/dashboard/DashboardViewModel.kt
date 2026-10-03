package com.laurasheehan.royalmiles.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.core.gamification.Badge
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.core.progress.WeekSummary
import com.laurasheehan.royalmiles.data.CelebrationStore
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.Stats
import com.laurasheehan.royalmiles.data.coach.CoachRepository
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import com.laurasheehan.royalmiles.data.coach.CoachSuggestionDecisions
import com.laurasheehan.royalmiles.data.coach.CoachState
import com.laurasheehan.royalmiles.data.coach.applyCoachSuggestion
import com.laurasheehan.royalmiles.ui.components.Affirmations
import com.laurasheehan.royalmiles.ui.components.LongRunPoint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A one-off moment for something newly earned. Shown once, then never again. */
data class Celebration(
    val headline: String,
    val detail: String,
    val badges: List<Badge> = emptyList(),
)

data class DashboardUiState(
    val stats: Stats? = null,
    val today: List<SessionEntity> = emptyList(),
    val upNext: List<SessionEntity> = emptyList(),
    val upNextTotal: Int = 0,
    /** Past-dated and still neither done nor written off. Surfaced quietly so it can be closed. */
    val stillOpen: List<SessionEntity> = emptyList(),
    val stillOpenTotal: Int = 0,
    val daysToRace: Long = 0,
    val phaseMessage: String = "",
    val weekNumber: Int = 0,
    val totalWeeks: Int = 0,
    val weekCommencing: LocalDate? = null,
    val longRuns: List<LongRunPoint> = emptyList(),
    val coachMotivation: String? = null,
    val coachKeyReminder: String? = null,
    val coachSuggestion: CoachSuggestionUiState? = null,
    /** The Sunday/Monday week wrap, when there is a week worth wrapping and it hasn't been seen. */
    val weekWrap: WeekSummary? = null,
    /** Badges earned but not yet celebrated: these glow until she's seen them, then settle. */
    val freshBadges: Set<String> = emptySet(),
)

data class CoachSuggestionUiState(
    val suggestion: CoachPayload.Coaching.Suggestion,
    val session: SessionEntity,
)

class DashboardViewModel(
    private val repository: PlanRepository,
    coachRepository: CoachRepository,
    private val raceDate: LocalDate,
    private val celebrations: CelebrationStore? = null,
    private val suggestionDecisions: CoachSuggestionDecisions? = null,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _affirmations = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val affirmations: SharedFlow<String> = _affirmations.asSharedFlow()

    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()

    private val seenChanges = MutableStateFlow(0)
    private val suggestionDecisionChanges = MutableStateFlow(0)

    /** Badges to record as seen when the current celebration is dismissed. */
    private var pendingSeen: Set<String>? = null

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeStats(),
        repository.observeWeeks(),
        coachRepository.state,
        seenChanges,
        suggestionDecisionChanges,
    ) { stats, weeks, coachState, _, _ ->
        val allSessions = weeks.flatMap { it.sessions }
        val today = todayProvider()
        val currentWeek = weeks.firstOrNull { !it.startDate.isAfter(today) && it.startDate.plusDays(6) >= today }
        val planStarted = weeks.any { !it.startDate.isAfter(today) }
        val coaching = (coachState as? CoachState.Loaded)?.payload?.coaching

        val longRunSessions = allSessions
            .filter { it.type == SessionType.LONG_RUN || it.type == SessionType.RACE }
            .sortedBy { it.date }
        val furthestRun = allSessions
            .filter { it.isCompleted && it.type in RUN_TYPES }
            .mapNotNull { it.knownDistanceKm }
            .maxOrNull()
            ?: 0.0

        val upNext = allSessions
            .filter { it.date.isAfter(today) && it.isOutstanding }
            .sortedBy { it.date }
        val stillOpen = allSessions
            .filter { it.date.isBefore(today) && it.isOutstanding && it.isLoggable }
            .sortedByDescending { it.date }

        DashboardUiState(
            stats = stats,
            today = allSessions.filter { it.date == today },
            upNext = upNext.take(4),
            upNextTotal = upNext.size,
            // Only loggable sessions can be outstanding in a way that means anything — a rest day
            // that was never ticked isn't unfinished business.
            stillOpen = stillOpen.take(5),
            stillOpenTotal = stillOpen.size,
            daysToRace = ChronoUnit.DAYS.between(today, raceDate),
            phaseMessage = phaseMessage(currentWeek?.phase, planStarted),
            weekNumber = currentWeek?.weekNumber ?: 0,
            totalWeeks = weeks.size,
            weekCommencing = currentWeek?.startDate,
            longRuns = ladder(longRunSessions, furthestRun),
            coachMotivation = coaching?.motivation,
            coachKeyReminder = coaching?.keyReminder,
            coachSuggestion = visibleCoachSuggestion(coaching?.suggestion, allSessions, today, suggestionDecisions),
            weekWrap = weekWrapFor(stats, today),
            freshBadges = celebrations?.let { store ->
                stats.badges.map { it.name }.filterNot { it in store.seenBadges() }.toSet()
            }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        viewModelScope.launch {
            repository.observeStats().collect { checkForCelebrations(it) }
        }
    }

    /**
     * The week wrap shows on Sunday (the week closing) and Monday (the week just gone), once each
     * week, and only when there is something in it. A week with nothing logged produces no card at
     * all rather than a card reporting zero.
     */
    private fun weekWrapFor(stats: Stats, today: LocalDate): WeekSummary? {
        val summary = when (today.dayOfWeek) {
            DayOfWeek.SUNDAY -> stats.thisWeek
            DayOfWeek.MONDAY -> stats.lastWeek
            else -> null
        } ?: return null
        if (summary.isEmpty) return null
        val store = celebrations ?: return summary
        return summary.takeUnless { store.weekWrapDismissed(it.weekCommencing.toString()) }
    }

    fun dismissWeekWrap() {
        val summary = uiState.value.weekWrap ?: return
        celebrations?.dismissWeekWrap(summary.weekCommencing.toString())
        seenChanges.value += 1
    }

    fun acceptCoachSuggestion() {
        val suggestionState = uiState.value.coachSuggestion ?: return
        viewModelScope.launch {
            val suggestion = suggestionState.suggestion
            if (!repository.applyCoachSuggestion(suggestion, suggestionState.session)) return@launch
            suggestionDecisions?.markAccepted(suggestion.date)
            suggestionDecisionChanges.value += 1
            // Names the result, not the action - the button already named the action.
            _affirmations.tryEmit(
                when (suggestion.action) {
                    CoachPayload.Coaching.SuggestionAction.SKIP -> "Skipped."
                    CoachPayload.Coaching.SuggestionAction.REPLACE -> "Swapped."
                },
            )
        }
    }

    fun dismissCoachSuggestion() {
        val suggestion = uiState.value.coachSuggestion?.suggestion ?: return
        suggestionDecisions?.markDismissed(suggestion.date)
        suggestionDecisionChanges.value += 1
        _affirmations.tryEmit("Kept as planned.")
    }

    /**
     * Fires a celebration the first time a badge is earned. Primes itself on first run so an
     * existing history doesn't produce a pile of backdated unlocks after an update. Levels and XP
     * are no longer shown, so reaching one is not celebrated.
     */
    private fun checkForCelebrations(stats: Stats) {
        val store = celebrations ?: return
        val names = stats.badges.map { it.name }.toSet()
        store.primeIfUnset(names, stats.level.number)

        val newBadges = stats.badges.filter { it.name !in store.seenBadges() }
        if (newBadges.isEmpty()) return
        if (_celebration.value != null) return
        pendingSeen = names

        _celebration.value = when {
            newBadges.size == 1 -> Celebration(
                headline = newBadges.first().title,
                detail = newBadges.first().description,
                badges = newBadges,
            )
            else -> Celebration(
                headline = "${newBadges.size} new badges",
                detail = newBadges.joinToString(" · ") { it.title },
                badges = newBadges,
            )
        }
    }

    /** Marked seen on dismissal rather than on display, so a missed dialog isn't a lost moment. */
    fun dismissCelebration() {
        pendingSeen?.let { celebrations?.markBadgesSeen(it) }
        pendingSeen = null
        _celebration.value = null
        // The badges just celebrated settle from glowing to the quieter theme colour.
        seenChanges.value += 1
    }

    fun toggleComplete(session: SessionEntity) {
        viewModelScope.launch {
            if (session.isCompleted) {
                repository.markIncomplete(session.id)
            } else {
                // Done is a fact she states; distance and time come from Garmin or from her.
                repository.markComplete(session.id, actualDistanceKm = null, actualDurationMin = null)
                _affirmations.tryEmit(Affirmations.random())
            }
        }
    }

    fun rate(session: SessionEntity, rating: Int) {
        viewModelScope.launch { repository.setEffortRating(session.id, rating) }
    }

    fun skip(session: SessionEntity) {
        viewModelScope.launch { repository.markSkipped(session.id) }
    }

    fun undoSkip(sessionId: Long) {
        viewModelScope.launch { repository.markIncomplete(sessionId) }
    }

}

internal fun visibleCoachSuggestion(
    suggestion: CoachPayload.Coaching.Suggestion?,
    sessions: List<SessionEntity>,
    today: LocalDate,
    suggestionDecisions: CoachSuggestionDecisions?,
): CoachSuggestionUiState? {
    suggestion ?: return null
    val suggestionDate = runCatching { LocalDate.parse(suggestion.date) }.getOrNull() ?: return null
    // Today, or a planned day in the next week: the morning coach can now see her plan and
    // suggest a change before the session, not only on the day.
    if (suggestionDate.isBefore(today) || suggestionDate.isAfter(today.plusDays(SUGGESTION_DAYS_AHEAD))) return null
    if (suggestionDecisions?.isDismissed(suggestion.date) == true) return null
    if (suggestionDecisions?.isAccepted(suggestion.date) == true) return null
    val session = sessions.firstOrNull { it.date == suggestionDate && !it.isCompleted } ?: return null
    return CoachSuggestionUiState(suggestion = suggestion, session = session)
}

private const val SUGGESTION_DAYS_AHEAD = 7L

private val RUN_TYPES = setOf(SessionType.EASY_RUN, SessionType.LONG_RUN, SessionType.RACE)

/**
 * Builds the long-run ladder from the planned rungs plus how far she has actually run.
 *
 * A rung counts as climbed once she has covered that distance, whether or not that particular
 * scheduled session was the one that did it — the ladder is about distance reached, not adherence
 * to a date. And when her furthest run so far sits below the first planned rung, it goes on the
 * front as its own completed bar: 5km back is real progress and the chart should say so rather
 * than starting at 8 and showing nothing done.
 */
private fun ladder(longRunSessions: List<SessionEntity>, furthestRun: Double): List<LongRunPoint> {
    val rungs = longRunSessions.mapNotNull { session ->
        val km = if (session.isCompleted) {
            session.actualDistanceKm ?: session.targetDistanceKm
        } else {
            session.targetDistanceKm
        }
        km?.takeIf { it > 0 }?.let { distance ->
            Triple(distance, session.isCompleted || furthestRun >= distance, session.type == SessionType.RACE)
        }
    }

    val withFurthest = if (furthestRun > 0 && rungs.none { it.first <= furthestRun }) {
        listOf(Triple(furthestRun, true, false)) + rungs
    } else {
        rungs
    }

    val nextIndex = withFurthest.indexOfFirst { !it.second }
    return withFurthest.mapIndexed { index, (km, done, isRace) ->
        LongRunPoint(km = km, done = done, isNext = index == nextIndex, isRace = isRace)
    }
}

private fun phaseMessage(phase: TrainingPhase?, planStarted: Boolean): String = when {
    !planStarted -> "Your plan starts Monday. This week is just about resting up."
    phase == TrainingPhase.BASE -> "Laying the foundation — this part isn't about speed."
    phase == TrainingPhase.BUILD -> "Building back, one weekend at a time."
    phase == TrainingPhase.PEAK -> "Biggest week of the block. Trust what you've built."
    phase == TrainingPhase.TAPER -> "Almost there. Let your legs feel it."
    else -> ""
}
