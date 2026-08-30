package com.laurasheehan.royalmiles.data

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlanRepositoryTest {

    @Test
    fun `regeneration preserves completed and custom sessions and is idempotent`() = runBlocking {
        val sessionDao = FakeSessionDao()
        val planMetaDao = FakePlanMetaDao(
            PlanMetaEntity(
                raceDate = RACE_DATE,
                startDate = LocalDate.of(2026, 8, 24),
                raceDistanceKm = 21.1,
                peakLongRunKm = 17.0,
                planVersion = 1,
            ),
        )
        val repository = PlanRepository(sessionDao, planMetaDao, transaction = { block -> block() })

        val completed = sessionDao.insertAndGet(
            SessionEntity(
                date = LocalDate.of(2026, 9, 2),
                type = SessionType.EASY_RUN,
                title = "Logged run",
                phase = TrainingPhase.BASE,
                weekNumber = 2,
                targetDistanceKm = 5.0,
                notes = "user edited notes",
                isCompleted = true,
                actualDistanceKm = 5.2,
                actualDurationMin = 35,
                completedAt = LocalDate.of(2026, 9, 2),
                effortRating = 4,
                actualAvgHeartRate = 145,
                actualMaxHeartRate = 158,
                actualCalories = 390,
                actualElevationGainM = 22,
                sourceApp = "com.garmin.android.apps.connectmobile",
                sourceActivityId = "abc123",
            ),
        )
        val custom = sessionDao.insertAndGet(
            SessionEntity(
                date = LocalDate.of(2026, 9, 9),
                type = SessionType.YOGA,
                title = "Extra mobility",
                phase = TrainingPhase.BUILD,
                weekNumber = 3,
                targetDurationMin = 20,
                notes = "Laura added this",
                isCustom = true,
            ),
        )
        val oldWeekOne = sessionDao.insertAndGet(
            SessionEntity(
                date = LocalDate.of(2026, 8, 30),
                type = SessionType.LONG_RUN,
                title = "Already logged week",
                phase = TrainingPhase.BASE,
                weekNumber = 1,
                targetDistanceKm = 8.0,
                isSkipped = true,
            ),
        )
        sessionDao.insert(
            SessionEntity(
                date = LocalDate.of(2026, 9, 14),
                type = SessionType.LONG_RUN,
                title = "Old generated future row",
                phase = TrainingPhase.BUILD,
                weekNumber = 4,
                targetDistanceKm = 17.0,
            ),
        )

        repository.regeneratePlan(raceDate = RACE_DATE, peakLongRunKm = 15.0)

        val afterFirst = sessionDao.getAll()
        assertTrue(completed in afterFirst)
        assertTrue(custom in afterFirst)
        assertTrue(oldWeekOne in afterFirst)
        assertTrue(afterFirst.none { it.title == "Old generated future row" })
        assertEquals(15.0, planMetaDao.get()?.peakLongRunKm)
        assertEquals(2, planMetaDao.get()?.planVersion)

        val longRuns = afterFirst
            .filter { it.type == SessionType.LONG_RUN }
            .filterNot { it.date.isBefore(LocalDate.of(2026, 8, 31)) }
            .sortedBy { it.date }
            .mapNotNull { it.targetDistanceKm }
        assertEquals(listOf(7.0, 9.0, 11.0, 13.0, 15.0), longRuns)

        val completedAfter = afterFirst.firstOrNull { it.id == completed.id }
        assertNotNull(completedAfter)
        assertEquals(completed, completedAfter)

        repository.regeneratePlan(raceDate = RACE_DATE, peakLongRunKm = 15.0)

        assertEquals(afterFirst, sessionDao.getAll())
    }

    private companion object {
        val RACE_DATE: LocalDate = LocalDate.of(2026, 10, 11)
    }
}

private class FakeSessionDao : SessionDao {
    private val state = MutableStateFlow<List<SessionEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<SessionEntity>> = state

    override suspend fun getById(id: Long): SessionEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun getAll(): List<SessionEntity> = state.value.sortedWith(compareBy<SessionEntity> { it.date }.thenBy { it.id })

    override suspend fun count(): Int = state.value.size

    override suspend fun insertAll(sessions: List<SessionEntity>) {
        sessions.forEach { insert(it) }
    }

    override suspend fun insert(session: SessionEntity): Long {
        val id = if (session.id == 0L) nextId++ else session.id
        state.value = state.value.filterNot { it.id == id } + session.copy(id = id)
        return id
    }

    suspend fun insertAndGet(session: SessionEntity): SessionEntity {
        val id = insert(session)
        return getById(id)!!
    }

    override suspend fun update(session: SessionEntity) {
        state.value = state.value.map { if (it.id == session.id) session else it }
    }

    override suspend fun delete(session: SessionEntity) {
        state.value = state.value.filterNot { it.id == session.id }
    }

    override suspend fun deleteRegeneratableSessions(cutoverDate: LocalDate) {
        state.value = state.value.filterNot {
            !it.date.isBefore(cutoverDate) && !it.isCompleted && !it.isCustom
        }
    }
}

private class FakePlanMetaDao(initial: PlanMetaEntity?) : PlanMetaDao {
    private val state = MutableStateFlow(initial)

    override suspend fun get(): PlanMetaEntity? = state.value

    override fun observe(): Flow<PlanMetaEntity?> = state

    override suspend fun upsert(meta: PlanMetaEntity) {
        state.value = meta
    }
}
