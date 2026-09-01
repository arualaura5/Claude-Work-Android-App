package com.laurasheehan.royalmiles.data

import com.laurasheehan.royalmiles.RaceConfig
import com.laurasheehan.royalmiles.core.model.Session
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.ui.session.SessionEditUiState
import com.laurasheehan.royalmiles.ui.session.toNewSessionEntity
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
    fun `generated plan sessions use Royal Parks event id`() {
        val entity = Session(
            date = LocalDate.of(2026, 9, 1),
            type = SessionType.EASY_RUN,
            title = "Easy run",
            phase = TrainingPhase.BASE,
            targetDistanceKm = 5.0,
        ).toEntity(weekNumber = 2)

        assertEquals(RaceConfig.ROYAL_PARKS_EVENT_ID, entity.eventId)
    }

    @Test
    fun `addCustomSession forces Royal Parks event id`() = runBlocking {
        val sessionDao = FakeSessionDao()
        val repository = PlanRepository(sessionDao, FakePlanMetaDao(null), runInTransaction = { block -> block() })

        val id = repository.addCustomSession(
            SessionEntity(
                eventId = "other-event",
                date = LocalDate.of(2026, 9, 3),
                type = SessionType.YOGA,
                title = "Yoga",
                phase = TrainingPhase.BASE,
                weekNumber = 2,
            ),
        )

        val inserted = sessionDao.getById(id)
        assertEquals(RaceConfig.ROYAL_PARKS_EVENT_ID, inserted?.eventId)
        assertEquals(true, inserted?.isCustom)
    }

    @Test
    fun `accepting coach replace skips original and inserts custom replacement for original event`() = runBlocking {
        val sessionDao = FakeSessionDao()
        val repository = PlanRepository(sessionDao, FakePlanMetaDao(null), runInTransaction = { block -> block() })
        val original = sessionDao.insertAndGet(
            SessionEntity(
                eventId = RaceConfig.RICHMOND_EVENT_ID,
                date = LocalDate.of(2026, 11, 2),
                type = SessionType.EASY_RUN,
                title = "Easy run",
                phase = TrainingPhase.BASE,
                weekNumber = 1,
                targetDistanceKm = 5.0,
            ),
        )

        val accepted = repository.acceptCoachReplacement(
            replacedSessionId = original.id,
            reason = "Symptoms arrived after last night was measured.",
            replacement = SessionEntity(
                eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
                date = LocalDate.of(2026, 9, 1),
                type = SessionType.CYCLE,
                title = "Easy spin - illness hold",
                phase = TrainingPhase.TAPER,
                weekNumber = 99,
                targetDurationMin = 20,
                notes = "Conversational only.",
            ),
        )

        assertTrue(accepted)
        val skipped = sessionDao.getById(original.id)
        assertEquals(true, skipped?.isSkipped)
        assertEquals(false, skipped?.isCompleted)

        val replacement = sessionDao.getAll().single { it.id != original.id }
        assertEquals(SessionType.CYCLE, replacement.type)
        assertEquals("Easy spin - illness hold", replacement.title)
        assertEquals(20, replacement.targetDurationMin)
        assertEquals(true, replacement.isCustom)
        assertEquals(RaceConfig.RICHMOND_EVENT_ID, replacement.eventId)
        assertEquals(original.date, replacement.date)
        assertEquals(original.weekNumber, replacement.weekNumber)
        assertEquals(original.phase, replacement.phase)
        assertEquals(
            "Was: Easy Run, 5.0 km - \"Easy run\".\n" +
                "Changed to Cycle, 20 min - \"Easy spin - illness hold\" because: Symptoms arrived after last night was measured.\n" +
                "Conversational only.",
            replacement.notes,
        )
    }

    @Test
    fun `coach replacement notes describe original replacement reason and instructions`() {
        val original = SessionEntity(
            eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
            date = LocalDate.of(2026, 9, 1),
            type = SessionType.EASY_RUN,
            title = "Tuesday shakeout",
            phase = TrainingPhase.BASE,
            weekNumber = 2,
            targetDistanceKm = 5.0,
        )
        val replacement = SessionEntity(
            eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
            date = LocalDate.of(2026, 9, 1),
            type = SessionType.CYCLE,
            title = "Easy spin",
            phase = TrainingPhase.BASE,
            weekNumber = 2,
            targetDurationMin = 20,
            notes = "Conversational only. Stop at 10 minutes if the head is worse.",
        )

        assertEquals(
            "Was: Easy Run, 5.0 km - \"Tuesday shakeout\".\n" +
                "Changed to Cycle, 20 min - \"Easy spin\" because: symptoms arrived after last night was measured.\n" +
                "Conversational only. Stop at 10 minutes if the head is worse.",
            coachReplacementNotes(
                original = original,
                replacement = replacement,
                reason = "symptoms arrived after last night was measured",
            ),
        )
    }

    @Test
    fun `coach replacement notes omit empty optional clauses cleanly`() {
        val original = SessionEntity(
            eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
            date = LocalDate.of(2026, 9, 3),
            type = SessionType.YOGA,
            title = "Yoga / mobility",
            phase = TrainingPhase.BASE,
            weekNumber = 2,
        )
        val replacement = SessionEntity(
            eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
            date = LocalDate.of(2026, 9, 3),
            type = SessionType.REST,
            title = "Rest day",
            phase = TrainingPhase.BASE,
            weekNumber = 2,
            notes = " ",
        )

        assertEquals(
            "Was: Yoga - \"Yoga / mobility\".\n" +
                "Changed to Rest - \"Rest day\".",
            coachReplacementNotes(
                original = original,
                replacement = replacement,
                reason = "",
            ),
        )
    }

    @Test
    fun `new session edit state builds Royal Parks session`() {
        val entity = SessionEditUiState(
            loading = false,
            isNew = true,
            title = "Extra run",
            type = SessionType.EASY_RUN,
            date = LocalDate.of(2026, 9, 4),
            weekNumber = 2,
            phase = TrainingPhase.BASE,
        ).toNewSessionEntity()

        assertEquals(RaceConfig.ROYAL_PARKS_EVENT_ID, entity.eventId)
    }

    @Test
    fun `event seeds include Royal Parks from plan metadata and Richmond from config`() {
        val meta = PlanMetaEntity(
            raceDate = LocalDate.of(2026, 10, 12),
            startDate = LocalDate.of(2026, 8, 24),
            raceDistanceKm = 22.0,
            peakLongRunKm = 16.0,
            planVersion = 2,
        )

        val seeds = seedEvents(meta)

        val royalParks = seeds.single { it.id == RaceConfig.ROYAL_PARKS_EVENT_ID }
        assertEquals(RaceConfig.ROYAL_PARKS_EVENT_NAME, royalParks.name)
        assertEquals(meta.raceDate, royalParks.raceDate)
        assertEquals(meta.raceDistanceKm, royalParks.raceDistanceKm)
        assertEquals(meta.peakLongRunKm, royalParks.peakLongRunKm)
        assertEquals(meta.startDate, royalParks.planStartDate)
        assertEquals(meta.planVersion, royalParks.planVersion)

        val richmond = seeds.single { it.id == RaceConfig.RICHMOND_EVENT_ID }
        assertEquals(RaceConfig.RICHMOND_EVENT_NAME, richmond.name)
        assertEquals(RaceConfig.RICHMOND_RACE_DATE, richmond.raceDate)
        assertEquals(RaceConfig.RICHMOND_RACE_DISTANCE_KM, richmond.raceDistanceKm)
        assertEquals(RaceConfig.RICHMOND_PEAK_LONG_RUN_KM, richmond.peakLongRunKm)
        assertEquals(null, richmond.planStartDate)
        assertEquals(0, richmond.planVersion)
    }

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
        val repository = PlanRepository(sessionDao, planMetaDao, runInTransaction = { block -> block() })

        val completed = sessionDao.insertAndGet(
            SessionEntity(
                eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
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
                eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
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
        val skipped = sessionDao.insertAndGet(
            SessionEntity(
                eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
                date = LocalDate.of(2026, 9, 7),
                type = SessionType.EASY_RUN,
                title = "Skipped future run",
                phase = TrainingPhase.BUILD,
                weekNumber = 3,
                targetDistanceKm = 5.0,
                isSkipped = true,
            ),
        )
        val oldWeekOne = sessionDao.insertAndGet(
            SessionEntity(
                eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
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
                eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
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
        assertTrue(skipped in afterFirst)
        assertTrue(oldWeekOne in afterFirst)
        assertTrue(afterFirst.none { it.title == "Old generated future row" })
        assertEquals(15.0, planMetaDao.get()?.peakLongRunKm)
        assertEquals(CURRENT_PLAN_VERSION, planMetaDao.get()?.planVersion)

        val longRuns = afterFirst
            .filter { it.type == SessionType.LONG_RUN }
            .filterNot { it.date.isBefore(LocalDate.of(2026, 8, 31)) }
            .sortedBy { it.date }
            .mapNotNull { it.targetDistanceKm }
        assertEquals(listOf(7.0, 9.0, 11.0, 13.0, 15.0), longRuns)

        val completedAfter = afterFirst.firstOrNull { it.id == completed.id }
        assertNotNull(completedAfter)
        assertEquals(completed, completedAfter)
        assertTrue(afterFirst.all { it.eventId == RaceConfig.ROYAL_PARKS_EVENT_ID })

        repository.regeneratePlan(raceDate = RACE_DATE, peakLongRunKm = 15.0)

        assertEquals(afterFirst, sessionDao.getAll())
    }

    @Test
    fun `markIncomplete clears logged metrics but preserves source activity link`() = runBlocking {
        val sessionDao = FakeSessionDao()
        val repository = PlanRepository(sessionDao, FakePlanMetaDao(null), runInTransaction = { block -> block() })
        val session = sessionDao.insertAndGet(
            SessionEntity(
                eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
                date = LocalDate.of(2026, 9, 2),
                type = SessionType.EASY_RUN,
                title = "Matched run",
                phase = TrainingPhase.BASE,
                weekNumber = 2,
                targetDistanceKm = 5.0,
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

        repository.markIncomplete(session.id)

        val updated = sessionDao.getById(session.id)!!
        assertEquals(false, updated.isCompleted)
        assertEquals(null, updated.actualDistanceKm)
        assertEquals(null, updated.actualDurationMin)
        assertEquals(null, updated.completedAt)
        assertEquals(null, updated.effortRating)
        assertEquals(null, updated.actualAvgHeartRate)
        assertEquals(null, updated.actualMaxHeartRate)
        assertEquals(null, updated.actualCalories)
        assertEquals(null, updated.actualElevationGainM)
        assertEquals("com.garmin.android.apps.connectmobile", updated.sourceApp)
        assertEquals("abc123", updated.sourceActivityId)
    }

    @Test
    fun `scaleDownWeek cascades future long runs without touching completed skipped or custom rows`() = runBlocking {
        val sessionDao = FakeSessionDao()
        val repository = PlanRepository(sessionDao, FakePlanMetaDao(null), runInTransaction = { block -> block() })
        val week4 = LocalDate.of(2026, 9, 21)
        val week4LongRun = sessionDao.insertAndGet(longRun(week4.plusDays(6), 13.0, 4))
        val week5LongRun = sessionDao.insertAndGet(longRun(week4.plusWeeks(1).plusDays(6), 15.0, 5))
        val week6LongRun = sessionDao.insertAndGet(longRun(week4.plusWeeks(2).plusDays(6), 17.0, 6))
        val week7Completed = sessionDao.insertAndGet(longRun(week4.plusWeeks(3).plusDays(6), 20.0, 7).copy(isCompleted = true))
        val week8Skipped = sessionDao.insertAndGet(longRun(week4.plusWeeks(4).plusDays(6), 21.0, 8).copy(isSkipped = true))
        val week9Custom = sessionDao.insertAndGet(longRun(week4.plusWeeks(5).plusDays(6), 22.0, 9).copy(isCustom = true))

        repository.scaleDownWeek(week4)

        assertEquals(10.0, sessionDao.getById(week4LongRun.id)?.targetDistanceKm)
        assertEquals(13.0, sessionDao.getById(week5LongRun.id)?.targetDistanceKm)
        assertEquals(16.5, sessionDao.getById(week6LongRun.id)?.targetDistanceKm)
        assertEquals(20.0, sessionDao.getById(week7Completed.id)?.targetDistanceKm)
        assertEquals(21.0, sessionDao.getById(week8Skipped.id)?.targetDistanceKm)
        assertEquals(22.0, sessionDao.getById(week9Custom.id)?.targetDistanceKm)
    }

    private companion object {
        val RACE_DATE: LocalDate = LocalDate.of(2026, 10, 11)

        fun longRun(date: LocalDate, distanceKm: Double, weekNumber: Int): SessionEntity = SessionEntity(
            eventId = RaceConfig.ROYAL_PARKS_EVENT_ID,
            date = date,
            type = SessionType.LONG_RUN,
            title = "Long run",
            phase = TrainingPhase.BUILD,
            weekNumber = weekNumber,
            targetDistanceKm = distanceKm,
        )
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
            !it.date.isBefore(cutoverDate) && !it.isCompleted && !it.isSkipped && !it.isCustom
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
