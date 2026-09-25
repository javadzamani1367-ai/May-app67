package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.FactSource
import ir.roozban.core.model.PlanningSettings
import ir.roozban.core.model.Project
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.model.TimeEntry
import ir.roozban.core.model.TimeSource
import ir.roozban.core.model.UserSettings
import ir.roozban.core.testing.FakeFocusRepository
import ir.roozban.core.testing.FakeLearningStore
import ir.roozban.core.testing.FakeMemoryRepository
import ir.roozban.core.testing.TEHRAN
import ir.roozban.learning.EstimateFactors
import ir.roozban.learning.Factor
import ir.roozban.learning.OverdueModel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class LearningAndPlanningTest {
    private val h = Harness()
    private val today: LocalDate = h.clock.now.toLocalDate()
    private val now: Instant = h.clock.instant()
    private val memoryRepo = FakeMemoryRepository()
    private val memory = MemoryUseCases(memoryRepo, h.clock)
    private val store = FakeLearningStore()
    private val focus = FakeFocusRepository(h.tasks)
    private val runner = LearningRunner(h.tasks, focus, h.projects, h.settings, store, memory, h.clock)
    private val planner = PlanDayUseCase(h.tasks, h.settings, store, h.update, h.clock)

    private fun task(
        id: String,
        due: TaskDue? = null,
        estimate: Int? = null,
        project: String? = null,
        important: Boolean = false,
        created: Instant = now.minus(Duration.ofDays(20)),
        completed: Instant? = null,
    ) = Task(
        id = id, title = id, due = due, estimateMinutes = estimate, projectId = project, important = important,
        createdAt = created, updatedAt = created, completedAt = completed,
    )

    @Test
    fun `memory keeps user statements over inference and replaces inferred facts by key`() = runTest {
        val said = memory.remember("عصرها باشگاه می‌روم")!!
        assertThat(memory.remember("عصرها باشگاه می‌روم.")!!.id).isEqualTo(said.id)
        memory.replace(FactSource.INFERRED, listOf(DerivedFact("hours", "صبح‌ها کار می‌کنی", 0.5f), DerivedFact(said.key, "x", 1f)))
        assertThat(memoryRepo.byKey(said.key)!!.text).isEqualTo("عصرها باشگاه می‌روم")
        memory.replace(FactSource.INFERRED, listOf(DerivedFact("estimate", "کارها ۱٫۵ برابر طول می‌کشند", 0.6f)))
        assertThat(memoryRepo.all().map { it.key }).containsExactly(said.key, "estimate")
        assertThat(memory.find("باشگاه").single().id).isEqualTo(said.id)

        val inferred = memoryRepo.byKey("estimate")!!
        memory.edit(inferred, "کارها دو برابر طول می‌کشند")
        memory.replace(FactSource.INFERRED, emptyList())
        assertThat(memoryRepo.byKey("estimate")!!.source).isEqualTo(FactSource.USER)

        val undo = memory.clearAll()
        assertThat(memoryRepo.all()).isEmpty()
        undo()
        assertThat(memoryRepo.all()).hasSize(2)
    }

    @Test
    fun `nightly run learns the estimate factor and writes insights`() = runTest {
        h.projects.upsert(Project("p", "گزارش‌ها", 0, createdAt = now, updatedAt = now))
        repeat(8) { i ->
            val end = now.minus(Duration.ofDays(i + 1L))
            h.tasks.upsert(task("t$i", TaskDue.AllDay(today.minusDays(i + 1L)), estimate = 30, project = "p", completed = end))
            // Each took an hour: twice the estimate.
            focus.addTimeEntry(TimeEntry("e$i", "t$i", end.minus(Duration.ofMinutes(60)), end, TimeSource.FOCUS))
        }
        val snapshot = runner.run()!!
        assertThat(snapshot.estimateSamples).isEqualTo(8)
        assertThat(snapshot.factors.global.value).isWithin(0.35).of(1.7)
        assertThat(store.snapshot).isEqualTo(snapshot)
        val facts = memoryRepo.all()
        assertThat(facts.map { it.key }).contains("estimate")
        assertThat(facts.first { it.key == "estimate" }.source).isEqualTo(FactSource.INFERRED)
        assertThat(facts.map { it.key }).contains("settings:day")

        h.settings.update { it.copy(planning = PlanningSettings(learningEnabled = false)) }
        assertThat(runner.run()).isNull()
    }

    @Test
    fun `snapshot survives the text codec`() {
        val s = LearningSnapshot.EMPTY.copy(
            computedAt = now,
            factors = EstimateFactors(Factor(1.4, 12), mapOf("p" to Factor(2.0, 6))),
            overdue = OverdueModel.Logistic(DoubleArray(11) { it / 10.0 }, 40),
            categoryLate = mapOf("p" to 0.4),
            overallLate = 0.3,
            estimateSamples = 12,
            lateSamples = 40,
        )
        val back = LearningSnapshotCodec.decode(LearningSnapshotCodec.encode(s))!!
        assertThat(back.factors).isEqualTo(s.factors)
        assertThat((back.overdue as OverdueModel.Logistic).weights.toList()).isEqualTo((s.overdue as OverdueModel.Logistic).weights.toList())
        assertThat(back.categoryLate).isEqualTo(s.categoryLate)
        assertThat(back.hours.total).isEqualTo(0.0)
        assertThat(LearningSnapshotCodec.decode("garbage")).isNull()
    }

    @Test
    fun `planner places open tasks around timed ones and apply is undoable`() = runTest {
        store.snapshot = LearningSnapshot.EMPTY.copy(factors = EstimateFactors(Factor(2.0, 10), emptyMap()))
        h.tasks.upsert(task("meeting", TaskDue.At(today, LocalTime.of(11, 0)), estimate = 60))
        h.tasks.upsert(task("report", TaskDue.AllDay(today), estimate = 30))
        h.tasks.upsert(task("late", TaskDue.AllDay(today.minusDays(2)), estimate = 15))
        h.tasks.upsert(task("idea", important = true))
        h.tasks.upsert(task("later", TaskDue.AllDay(today.plusDays(3))))
        h.tasks.upsert(task("weekly", TaskDue.AllDay(today)).copy(recurrence = "FREQ=WEEKLY"))

        val plan = planner.propose(today)
        assertThat(plan.fixed.map { it.taskId }).containsExactly("meeting")
        assertThat(plan.placements.map { it.candidate.taskId }).containsExactly("late", "report", "idea")
        // Estimates are corrected by the learned factor.
        assertThat(plan.placements.first { it.candidate.taskId == "report" }.candidate.minutes).isEqualTo(60)
        plan.placements.forEach { p ->
            assertThat(p.start).isAtLeast(LocalTime.of(10, 0))
            val overlapsMeeting = p.start < LocalTime.of(13, 0) && p.end > LocalTime.of(11, 0)
            assertThat(overlapsMeeting).isFalse()
        }

        val result = planner.apply(plan, setOf("report", "late"))
        assertThat(result.applied).isEqualTo(2)
        assertThat(h.tasks.get("report")!!.due).isInstanceOf(TaskDue.At::class.java)
        assertThat(h.tasks.get("idea")!!.due).isNull()
        result.undo()
        assertThat(h.tasks.get("report")!!.due).isEqualTo(TaskDue.AllDay(today))
        assertThat(h.tasks.get("late")!!.due).isEqualTo(TaskDue.AllDay(today.minusDays(2)))
    }

    @Test
    fun `morning routine rolls overdue tasks to today and applies the plan in auto mode`() = runTest {
        h.settings.update { it.copy(planning = PlanningSettings(autoPlan = true)) }
        h.tasks.upsert(task("late", TaskDue.At(today.minusDays(1), LocalTime.of(9, 0)), estimate = 20))
        h.tasks.upsert(task("weekly", TaskDue.AllDay(today.minusDays(1))).copy(recurrence = "FREQ=WEEKLY"))
        val morning = MorningPlanUseCase(h.settings, RolloverUseCase(h.tasks, h.update, h.clock), planner, h.clock)

        val result = morning()
        assertThat(result.rolledOver).isEqualTo(1)
        assertThat(result.autoApplied).isEqualTo(1)
        val due = h.tasks.get("late")!!.due as TaskDue.At
        assertThat(due.date).isEqualTo(today)
        assertThat(h.tasks.get("weekly")!!.due).isEqualTo(TaskDue.AllDay(today.minusDays(1)))
    }

    @Test
    fun `risk uses the late model only for dated tasks`() {
        val s = LearningSnapshot.EMPTY.copy(overdue = OverdueModel.Prior(0.4, 3))
        assertThat(s.risk(task("a"), TEHRAN)).isEqualTo(0.0)
        assertThat(s.risk(task("b", TaskDue.AllDay(today)), TEHRAN)).isEqualTo(0.4)
        assertThat(UserSettings().planning.morningTime).isNull()
    }
}
