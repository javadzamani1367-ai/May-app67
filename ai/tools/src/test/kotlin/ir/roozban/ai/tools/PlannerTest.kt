package ir.roozban.ai.tools

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.model.TaskDue
import ir.roozban.core.testing.jalali
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.time.LocalTime

class PlannerTest {
    private val f = Fixture()

    private fun call(tool: String, vararg args: Pair<String, Any?>) = ToolCall(
        tool,
        args.associate { (k, v) ->
            k to when (v) {
                null -> JsonNull
                is String -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                else -> error("bad arg")
            }
        },
    )

    private suspend fun plan(c: ToolCall) = ActionPlanner(f.context()).planOne(c)

    @Test
    fun `create task resolves persian time with the app parser`() = runTest {
        val p = plan(call("create_task", "title" to "زنگ به مامان", "when" to "فردا ساعت ۵ عصر", "repeat" to null, "important" to true, "urgent" to false, "minutes" to 15, "project" to "خانه"))
        val op = p.operation as Operation.CreateTask
        assertThat(op.due).isEqualTo(TaskDue.At(jalali("1405-07-03"), LocalTime.of(17, 0)))
        assertThat(op.important).isTrue()
        assertThat(op.minutes).isEqualTo(15)
        assertThat(op.project).isEqualTo("خانه")
        assertThat(p.summary).isEqualTo("کار جدید: «زنگ به مامان» — فردا ۱۷:۰۰")
    }

    @Test
    fun `create task accepts iso and jalali dates`() = runTest {
        val iso = plan(call("create_task", "title" to "a", "when" to "2026-10-01 08:30")).operation as Operation.CreateTask
        assertThat(iso.due).isEqualTo(TaskDue.At(java.time.LocalDate.of(2026, 10, 1), LocalTime.of(8, 30)))
        val j = plan(call("create_task", "title" to "b", "when" to "۱۴۰۵/۰۸/۱۰")).operation as Operation.CreateTask
        assertThat(j.due).isEqualTo(TaskDue.AllDay(jalali("1405-08-10")))
    }

    @Test
    fun `unclear times and missing titles are problems, not guesses`() = runTest {
        val p = plan(call("create_task", "title" to "x", "when" to "یه وقتی"))
        assertThat(p.runnable).isFalse()
        assertThat(p.problem).contains("یه وقتی")
        assertThat(plan(call("create_task", "title" to " ")).runnable).isFalse()
        assertThat(plan(call("fly_to_moon")).runnable).isFalse()
    }

    @Test
    fun `repeat without a time starts today`() = runTest {
        val op = plan(call("create_task", "title" to "ورزش", "when" to null, "repeat" to "هر روز")).operation as Operation.CreateTask
        assertThat(op.recurrence).isNotNull()
        assertThat(op.due).isEqualTo(TaskDue.AllDay(f.today))
    }

    @Test
    fun `tasks are found by number or fuzzy title`() = runTest {
        f.task("خرید نان", f.at(0, 18))
        f.task("تمدید بیمه ماشین", f.at(1, 9))
        f.task("جلسه با علی")
        assertThat((plan(call("complete_task", "task" to "#2")).operation as Operation.CompleteTask).task.title).isEqualTo("تمدید بیمه ماشین")
        assertThat((plan(call("complete_task", "task" to "۳")).operation as Operation.CompleteTask).task.title).isEqualTo("جلسه با علی")
        assertThat((plan(call("complete_task", "task" to "بیمه")).operation as Operation.CompleteTask).task.title).isEqualTo("تمدید بیمه ماشین")
        assertThat((plan(call("delete_task", "task" to "نان خریدن")).operation as Operation.DeleteTask).task.title).isEqualTo("خرید نان")
        assertThat((plan(call("complete_task", "task" to "جلسه علي")).operation as Operation.CompleteTask).task.title).isEqualTo("جلسه با علی")
        assertThat(plan(call("complete_task", "task" to "#9")).runnable).isFalse()
        assertThat(plan(call("complete_task", "task" to "کتاب")).problem).contains("پیدا نکردم")
    }

    @Test
    fun `ambiguous references offer candidates`() = runTest {
        f.task("خرید نان")
        f.task("خرید میوه")
        val p = plan(call("complete_task", "task" to "خرید"))
        assertThat(p.runnable).isFalse()
        assertThat(p.candidates.map { it.title }).containsExactly("خرید نان", "خرید میوه")
    }

    @Test
    fun `reschedule keeps the time when only a day is given`() = runTest {
        f.task("جلسه", f.at(0, 14, 30))
        val op = plan(call("reschedule", "task" to "#1", "when" to "شنبه")).operation as Operation.Reschedule
        assertThat(op.due).isEqualTo(TaskDue.At(jalali("1405-07-04"), LocalTime.of(14, 30)))
        val op2 = plan(call("reschedule", "task" to "#1", "when" to "فردا ساعت ۸")).operation as Operation.Reschedule
        assertThat(op2.due).isEqualTo(TaskDue.At(jalali("1405-07-03"), LocalTime.of(8, 0)))
    }

    @Test
    fun `update only changes given fields`() = runTest {
        f.task("گزارش")
        val op = plan(call("update_task", "task" to "#1", "title" to null, "important" to true, "urgent" to null, "notes" to null)).operation as Operation.UpdateTask
        assertThat(op.updated.important).isTrue()
        assertThat(op.updated.title).isEqualTo("گزارش")
        assertThat(plan(call("update_task", "task" to "#1", "title" to null, "important" to null)).runnable).isFalse()
    }

    @Test
    fun `habits and focus`() = runTest {
        f.habit("نوشیدن آب", perDay = 8)
        f.task("مقاله")
        val h = plan(call("create_habit", "name" to "مطالعه", "per_week" to 3, "per_day" to 1, "reminder" to "ساعت ۷ صبح")).operation as Operation.CreateHabit
        assertThat(h.schedule).isEqualTo(HabitSchedule.TimesPerWeek(3))
        assertThat(h.reminder).isEqualTo(LocalTime.of(7, 0))
        val daily = plan(call("create_habit", "name" to "ورزش", "per_week" to null, "per_day" to 1, "reminder" to null)).operation as Operation.CreateHabit
        assertThat(daily.schedule).isEqualTo(HabitSchedule.Daily)
        assertThat((plan(call("log_habit", "habit" to "آب", "count" to null)).operation as Operation.LogHabit).habit.name).isEqualTo("نوشیدن آب")
        assertThat((plan(call("start_focus", "task" to null)).operation as Operation.StartFocus).task).isNull()
        assertThat((plan(call("start_focus", "task" to "مقاله")).operation as Operation.StartFocus).task?.title).isEqualTo("مقاله")
    }

    @Test
    fun `destructive, bulk and many writes need confirmation`() = runTest {
        f.task("الف", f.at(-2, 9))
        f.task("ب", f.at(-1, 9))
        val planner = ActionPlanner(f.context())
        fun planOf(vararg calls: ToolCall) = planner.plan(AssistantResponse(calls.toList(), ""))
        assertThat(planOf(call("complete_task", "task" to "#1")).needsConfirmation).isFalse()
        assertThat(planOf(call("delete_task", "task" to "#1")).needsConfirmation).isTrue()
        val bulk = planOf(call("reschedule_overdue", "when" to "فردا"))
        assertThat(bulk.needsConfirmation).isTrue()
        assertThat((bulk.actions.single().operation as Operation.RescheduleMany).tasks).hasSize(2)
        val many = planOf(*Array(4) { call("create_task", "title" to "کار $it") })
        assertThat(many.needsConfirmation).isTrue()
        assertThat(planOf(call("list_tasks", "range" to "today"), call("list_tasks", "range" to "week"), call("create_task", "title" to "x")).needsConfirmation).isFalse()
    }
}
