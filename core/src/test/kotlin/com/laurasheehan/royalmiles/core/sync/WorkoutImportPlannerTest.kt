package com.laurasheehan.royalmiles.core.sync

import com.laurasheehan.royalmiles.core.model.SessionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutImportPlannerTest {

    private val monday = LocalDate.of(2026, 9, 14)

    private fun session(
        id: Long,
        date: LocalDate,
        type: SessionType = SessionType.EASY_RUN,
        target: Double? = 5.0,
        completed: Boolean = false,
        skipped: Boolean = false,
        completedAt: LocalDate? = null,
        sourceId: String? = null,
    ) = SessionFacts(id, date, type, target, completed, skipped, completedAt, sourceId)

    private fun run(id: String?, date: LocalDate = monday, km: Double? = 8.0, type: SessionType? = SessionType.EASY_RUN) =
        WorkoutFacts(id, date, type, km)

    @Test
    fun `matches the nearest outstanding run within two days`() {
        val decisions = WorkoutImportPlanner.plan(
            listOf(run("a")),
            listOf(session(1, monday.minusDays(2)), session(2, monday.plusDays(1))),
        )
        assertEquals(listOf<ImportDecision>(ImportDecision.Match(2)), decisions)
    }

    @Test
    fun `distance breaks a tie between equally close run slots`() {
        val decisions = WorkoutImportPlanner.plan(
            listOf(run("a", km = 8.0)),
            listOf(
                session(1, monday.minusDays(1), target = 5.0),
                session(2, monday.plusDays(1), SessionType.LONG_RUN, target = 8.0),
            ),
        )
        assertEquals(listOf<ImportDecision>(ImportDecision.Match(2)), decisions)
    }

    @Test
    fun `adds an extra session when nothing in the plan is close enough`() {
        val decisions = WorkoutImportPlanner.plan(
            listOf(run("a")),
            listOf(session(1, monday.plusDays(3))),
        )
        assertEquals(listOf<ImportDecision>(ImportDecision.AddExtra), decisions)
    }

    @Test
    fun `never imports a workout that is already on a session`() {
        val decisions = WorkoutImportPlanner.plan(
            listOf(run("a")),
            listOf(session(1, monday, completed = true, completedAt = monday, sourceId = "a"), session(2, monday)),
        )
        assertEquals(listOf<ImportDecision>(ImportDecision.Skip), decisions)
    }

    @Test
    fun `skips workouts with no id and kinds the plan does not track`() {
        val decisions = WorkoutImportPlanner.plan(
            listOf(run(null), run("walk", type = null)),
            listOf(session(1, monday)),
        )
        assertEquals(listOf<ImportDecision>(ImportDecision.Skip, ImportDecision.Skip), decisions)
    }

    @Test
    fun `fills a session ticked off by hand on the same day instead of logging it twice`() {
        val decisions = WorkoutImportPlanner.plan(
            listOf(run("a")),
            listOf(session(1, monday.minusDays(1), completed = true, completedAt = monday), session(2, monday)),
        )
        assertEquals(listOf<ImportDecision>(ImportDecision.Fill(1)), decisions)
    }

    @Test
    fun `leaves skipped sessions and other session types alone`() {
        val decisions = WorkoutImportPlanner.plan(
            listOf(run("a")),
            listOf(session(1, monday, skipped = true), session(2, monday, SessionType.STRENGTH)),
        )
        assertEquals(listOf<ImportDecision>(ImportDecision.AddExtra), decisions)
    }

    @Test
    fun `two workouts never claim the same session`() {
        val decisions = WorkoutImportPlanner.plan(
            listOf(run("a"), run("b")),
            listOf(session(1, monday)),
        )
        assertEquals(listOf(ImportDecision.Match(1), ImportDecision.AddExtra), decisions)
    }
}
