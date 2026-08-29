package com.laurasheehan.royalmiles.data

import com.laurasheehan.royalmiles.core.gamification.Badge
import com.laurasheehan.royalmiles.core.gamification.CompletedSession
import com.laurasheehan.royalmiles.core.gamification.GamificationEngine
import com.laurasheehan.royalmiles.core.gamification.Level
import com.laurasheehan.royalmiles.core.model.Session
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.core.model.TrainingPlan
import com.laurasheehan.royalmiles.core.model.TrainingWeek
import com.laurasheehan.royalmiles.core.plan.TrainingPlanGenerator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UiWeek(
    val weekNumber: Int,
    val phase: TrainingPhase,
    val startDate: LocalDate,
    val sessions: List<SessionEntity>,
) {
    val longRunKm: Double?
        get() = sessions.firstOrNull { it.type == SessionType.LONG_RUN || it.type == SessionType.RACE }?.targetDistanceKm
}

/**
 * Date first, then what actually happened before what's merely planned: a session carrying real
 * logged numbers leads, then anything else marked done, then the rest. Ties fall back to id so the
 * order is stable rather than arbitrary.
 */
private val sessionOrder = compareBy<SessionEntity>(
    { it.date },
    { loggedRank(it) },
    { it.id },
)

private fun loggedRank(session: SessionEntity): Int = when {
    session.actualDistanceKm != null || session.actualDurationMin != null -> 0
    session.isCompleted -> 1
    else -> 2
}

data class Stats(
    val totalXp: Int,
    val level: Level,
    val xpToNextLevel: Int?,
    /** Consecutive Mon-Sun weeks with something logged. Week-granular so a rest day can't break it. */
    val currentWeekStreak: Int,
    val longestWeekStreak: Int,
    val badges: Set<Badge>,
    /** Effort ratings (1-5) from the most recent completed sessions that have one, oldest first. */
    val recentEffort: List<Int> = emptyList(),
)

class PlanRepository(
    private val sessionDao: SessionDao,
    private val planMetaDao: PlanMetaDao,
) {
    suspend fun ensureSeeded(raceDate: LocalDate, today: LocalDate = LocalDate.now(), peakLongRunKm: Double = 17.0) {
        if (sessionDao.count() > 0) return
        val plan = TrainingPlanGenerator.generate(raceDate = raceDate, today = today, peakLongRunKm = peakLongRunKm)
        planMetaDao.upsert(
            PlanMetaEntity(
                id = 0,
                raceDate = plan.raceDate,
                startDate = plan.startDate,
                raceDistanceKm = plan.raceDistanceKm,
                peakLongRunKm = plan.peakLongRunKm,
            ),
        )
        val entities = plan.weeks.flatMap { week ->
            week.sessions.map { it.toEntity(week.weekNumber) }
        }
        sessionDao.insertAll(entities)
    }

    fun observeSessions(): Flow<List<SessionEntity>> = sessionDao.observeAll()

    /**
     * Weeks are grouped by the Monday each session actually falls in, not by the stored
     * [SessionEntity.weekNumber] — so moving a session across a week boundary files it under the
     * week it now belongs to, and the header stays pinned to the real Monday rather than drifting
     * to whatever the earliest session happens to be.
     */
    fun observeWeeks(): Flow<List<UiWeek>> = observeSessions().map { sessions ->
        sessions
            .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .toSortedMap()
            .entries
            .mapIndexed { index, entry ->
                val weekSessions = entry.value.sortedWith(sessionOrder)
                UiWeek(
                    weekNumber = index + 1,
                    phase = weekSessions.first().phase,
                    startDate = entry.key,
                    sessions = weekSessions,
                )
            }
    }

    fun observeStats(): Flow<Stats> = observeSessions().map { computeStats(it) }

    suspend fun getSession(id: Long): SessionEntity? = sessionDao.getById(id)

    suspend fun getPlanMeta(): PlanMetaEntity? = planMetaDao.get()

    suspend fun markComplete(
        id: Long,
        actualDistanceKm: Double?,
        actualDurationMin: Int?,
        effortRating: Int? = null,
        completedAt: LocalDate = LocalDate.now(),
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null,
        calories: Int? = null,
        elevationGainM: Int? = null,
        sourceApp: String? = null,
        sourceActivityId: String? = null,
    ) {
        val existing = sessionDao.getById(id) ?: return
        sessionDao.update(
            existing.copy(
                isCompleted = true,
                isSkipped = false,
                sourceApp = sourceApp ?: existing.sourceApp,
                sourceActivityId = sourceActivityId ?: existing.sourceActivityId,
                actualDistanceKm = actualDistanceKm,
                actualDurationMin = actualDurationMin,
                effortRating = effortRating,
                completedAt = completedAt,
                actualAvgHeartRate = avgHeartRate ?: existing.actualAvgHeartRate,
                actualMaxHeartRate = maxHeartRate ?: existing.actualMaxHeartRate,
                actualCalories = calories ?: existing.actualCalories,
                actualElevationGainM = elevationGainM ?: existing.actualElevationGainM,
            ),
        )
    }

    /**
     * Acknowledges a session as not done. Deliberately carries no penalty — it exists so a missed
     * session can stop asking, not so anything can be counted against her.
     */
    suspend fun markSkipped(id: Long) {
        val existing = sessionDao.getById(id) ?: return
        sessionDao.update(
            existing.copy(
                isCompleted = false,
                isSkipped = true,
                actualDistanceKm = null,
                actualDurationMin = null,
                effortRating = null,
                completedAt = null,
            ),
        )
    }

    /** Back to outstanding — clears both "done" and "didn't do it", and everything logged with it. */
    suspend fun markIncomplete(id: Long) {
        val existing = sessionDao.getById(id) ?: return
        sessionDao.update(
            existing.copy(
                isCompleted = false,
                isSkipped = false,
                actualDistanceKm = null,
                actualDurationMin = null,
                effortRating = null,
                completedAt = null,
                actualAvgHeartRate = null,
                actualMaxHeartRate = null,
                actualCalories = null,
                actualElevationGainM = null,
                sourceApp = null,
                sourceActivityId = null,
            ),
        )
    }

    suspend fun updateSession(session: SessionEntity) = sessionDao.update(session)

    suspend fun deleteSession(session: SessionEntity) = sessionDao.delete(session)

    suspend fun addCustomSession(session: SessionEntity) = sessionDao.insert(session.copy(isCustom = true))

    /**
     * Badges care about which planned *slot* got done, so they're matched against the session's
     * scheduled [SessionEntity.date] even if it was logged as complete on a different real day.
     * Streaks care about actual training days, so they use [SessionEntity.completedAt] instead.
     */
    private fun computeStats(sessions: List<SessionEntity>): Stats {
        val completed = sessions.filter { it.isCompleted }
        val slotCompletions = completed.map { it.toCompletedSession(useScheduledDate = true) }
        val calendarCompletions = completed.map { it.toCompletedSession(useScheduledDate = false) }

        val totalXp = GamificationEngine.totalXp(slotCompletions)
        val raceCompleted = completed.any { it.type == SessionType.RACE }
        val level = GamificationEngine.levelFor(totalXp, raceCompleted)
        val badges = GamificationEngine.evaluateBadges(slotCompletions, snapshotPlan(sessions))

        val recentEffort = completed
            .filter { it.effortRating != null }
            .sortedBy { it.completedAt ?: it.date }
            .takeLast(10)
            .mapNotNull { it.effortRating }

        return Stats(
            totalXp = totalXp,
            level = level,
            xpToNextLevel = GamificationEngine.xpToNextLevel(totalXp, raceCompleted),
            currentWeekStreak = GamificationEngine.currentWeekStreak(calendarCompletions),
            longestWeekStreak = GamificationEngine.longestWeekStreak(calendarCompletions),
            badges = badges,
            recentEffort = recentEffort,
        )
    }

    /** Rebuilds a [TrainingPlan] from the live, editable rows so badge rules reflect edits, not the original template. */
    private fun snapshotPlan(sessions: List<SessionEntity>): TrainingPlan {
        val weeks = sessions.groupBy { it.weekNumber }.toSortedMap().map { (weekNumber, weekSessions) ->
            val sorted = weekSessions.sortedBy { it.date }
            TrainingWeek(
                weekNumber = weekNumber,
                phase = sorted.first().phase,
                startDate = sorted.minOf { it.date },
                sessions = sorted.map { it.toCoreSession() },
            )
        }
        val raceDate = sessions.filter { it.type == SessionType.RACE }.minByOrNull { it.date }?.date
            ?: sessions.maxOfOrNull { it.date }
            ?: LocalDate.now()
        val startDate = sessions.minOfOrNull { it.date } ?: LocalDate.now()
        return TrainingPlan(
            raceDate = raceDate,
            startDate = startDate,
            raceDistanceKm = 21.1,
            peakLongRunKm = 17.0,
            weeks = weeks,
        )
    }
}

private fun Session.toEntity(weekNumber: Int): SessionEntity = SessionEntity(
    date = date,
    type = type,
    title = title,
    phase = phase,
    weekNumber = weekNumber,
    targetDistanceKm = targetDistanceKm,
    targetDurationMin = targetDurationMin,
    optional = optional,
    notes = notes,
)

private fun SessionEntity.toCoreSession(): Session = Session(
    date = date,
    type = type,
    title = title,
    phase = phase,
    targetDistanceKm = targetDistanceKm,
    targetDurationMin = targetDurationMin,
    optional = optional,
    notes = notes,
)

private fun SessionEntity.toCompletedSession(useScheduledDate: Boolean): CompletedSession = CompletedSession(
    date = if (useScheduledDate) date else (completedAt ?: date),
    type = type,
    phase = phase,
    distanceKm = actualDistanceKm ?: targetDistanceKm,
    durationMin = actualDurationMin ?: targetDurationMin,
)
