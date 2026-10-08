package com.laurasheehan.royalmiles.data.garmin

import com.laurasheehan.royalmiles.data.AppDatabase
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.coach.CoachRepository
import com.laurasheehan.royalmiles.data.health.ExternalWorkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** An activity she needs to place, and the choices that make sense for it. */
data class PendingActivity(val workout: ExternalWorkout, val kind: ActivityKind, val ask: MatchResult.Ask)

/** An activity linked without asking, shown until she has seen it. */
data class AutoLink(val decision: GarminDecisionEntity, val session: SessionEntity?)

data class GarminInboxState(
    val pending: List<PendingActivity> = emptyList(),
    val autoLinked: List<AutoLink> = emptyList(),
)

/**
 * New Garmin activities, placed in her plan. A clear fit is linked straight away and shown so she
 * can say "not this one"; anything less clear waits for her. Runs when the app comes to the front.
 * Offline or unconnected, it quietly does nothing.
 */
class GarminInbox(
    private val db: AppDatabase,
    private val coach: CoachRepository,
    private val links: GarminLinks = GarminLinks(db),
    private val today: () -> LocalDate = LocalDate::now,
    private val fetch: (String, String) -> List<ExternalWorkout> = GarminActivityFeed::fetch,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    internal var feed: List<ExternalWorkout> = emptyList()

    private val _state = MutableStateFlow(GarminInboxState())
    val state: StateFlow<GarminInboxState> = _state.asStateFlow()

    fun refresh() {
        scope.launch {
            val (address, key) = coach.cloudCredentials() ?: return@launch
            val fetched = runCatching { fetch(address, key) }.getOrNull() ?: return@launch
            lock.withLock {
                feed = fetched
                process()
            }
        }
    }

    fun confirm(item: PendingActivity, session: SessionEntity) =
        act { links.link(item.workout, session.id, if (session.date == item.workout.localDate) GarminDecision.LINKED else GarminDecision.OTHER_DAY) }

    fun swap(item: PendingActivity, planned: SessionEntity) = act { links.swap(item.workout, planned.id) }

    fun extra(item: PendingActivity) = act { links.extra(item.workout) }

    fun ignore(item: PendingActivity) = act { links.ignore(item.workout) }

    /** "Not this one": undone, and from then on it's asked about rather than assumed. */
    fun notThisOne(link: AutoLink) = act { links.undo(link.decision.id) }

    fun keep(link: AutoLink) = act { links.acknowledge(link.decision.id) }

    private fun act(block: suspend () -> Unit) {
        scope.launch {
            lock.withLock {
                block()
                process()
            }
        }
    }

    /**
     * Her answer on the watch ("How did you feel?") becomes the session's rating, so she isn't
     * asked twice. Only where she hasn't rated it in the app: her rating here always wins. Runs on
     * every pass, because Garmin often has the answer only after the run was already linked.
     */
    internal suspend fun fillFeelFromWatch(sessions: List<SessionEntity>): List<SessionEntity> {
        val feelById = feed.mapNotNull { w -> w.sourceActivityId?.let { id -> w.watchFeel?.let { id to it } } }.toMap()
        if (feelById.isEmpty()) return sessions
        var changed = false
        for (session in sessions) {
            if (!session.isCompleted || session.effortRating != null) continue
            val feel = session.sourceActivityId?.let(feelById::get) ?: continue
            db.sessionDao().update(session.copy(effortRating = feel))
            changed = true
        }
        return if (changed) db.sessionDao().getAll() else sessions
    }

    /** Links clear fits, lists the rest. Called under [lock]. */
    internal suspend fun process() {
        val decisions = db.garminDecisionDao().getAll()
        var sessions = db.sessionDao().getAll()
        val day = today()
        val pending = mutableListOf<PendingActivity>()

        for (workout in feed.sortedBy { it.start }) {
            val id = workout.sourceActivityId ?: continue
            if (!GarminMatcher.inWindow(workout.localDate, day)) continue
            val mine = decisions.filter { it.activityId == id }
            if (mine.any { it.isActive } || sessions.any { it.sourceActivityId == id }) continue
            val kind = GarminMatcher.kindOf(workout) ?: continue
            when (val result = GarminMatcher.match(workout, sessions, sheSaidNotThis = mine.isNotEmpty())) {
                MatchResult.NotTraining -> Unit
                is MatchResult.Confident -> {
                    if (links.link(workout, result.session.id, GarminDecision.AUTO_LINKED)) {
                        sessions = db.sessionDao().getAll()
                    }
                }
                is MatchResult.Ask -> pending += PendingActivity(workout, kind, result)
            }
        }

        sessions = fillFeelFromWatch(sessions)

        val byId = sessions.associateBy { it.id }
        val autoLinked = db.garminDecisionDao().getAll()
            .filter { it.isActive && it.decision == GarminDecision.AUTO_LINKED && !it.acknowledged }
            .filter { !it.activityDate.isBefore(day.minusDays(7)) }
            .map { AutoLink(it, it.sessionId?.let(byId::get)) }
        _state.value = GarminInboxState(pending = pending, autoLinked = autoLinked)
    }
}
