package ir.roozban.ai.tools

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.FocusState
import ir.roozban.core.model.TaskDue
import ir.roozban.core.testing.jalali
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalTime

class ExecutorTest {
    private val f = Fixture()

    private suspend fun run(json: String): List<ActionResult> {
        val plan = ActionPlanner(f.context()).plan(ResponseParser.parse(json))
        return f.executor.execute(plan)
    }

    private suspend fun open() = f.tasks.observeOpenTasks().first()

    @Test
    fun `creates a task and undoes it`() = runTest {
        val r = run("""{"actions":[{"tool":"create_task","args":{"title":"خرید نان","when":"امروز ساعت ۶ عصر","repeat":null,"important":false,"urgent":true,"minutes":null,"project":"خانه"}}],"reply":"ok"}""").single()
        assertThat(r.ok).isTrue()
        val t = open().single()
        assertThat(t.title).isEqualTo("خرید نان")
        assertThat(t.due).isEqualTo(TaskDue.At(f.today, LocalTime.of(18, 0)))
        assertThat(t.urgent).isTrue()
        assertThat(t.projectId).isNotNull()
        r.undo!!.invoke()
        assertThat(open()).isEmpty()
    }

    @Test
    fun `complete, reschedule and delete come with undo`() = runTest {
        f.task("الف", f.at(0, 9))
        f.task("ب", f.at(0, 10))
        f.task("پ")
        val results = run("""{"actions":[{"tool":"complete_task","args":{"task":"#1"}},{"tool":"reschedule","args":{"task":"#2","when":"فردا"}},{"tool":"delete_task","args":{"task":"#3"}}],"reply":""}""")
        assertThat(results.all { it.ok }).isTrue()
        assertThat(open().map { it.title }).containsExactly("ب")
        assertThat(open().single().due).isEqualTo(TaskDue.At(f.today.plusDays(1), LocalTime.of(10, 0)))
        results.reversed().forEach { it.undo!!.invoke() }
        assertThat(open().map { it.title }).containsExactly("الف", "ب", "پ")
        assertThat(open().first { it.title == "ب" }.due).isEqualTo(TaskDue.At(f.today, LocalTime.of(10, 0)))
    }

    @Test
    fun `reschedule overdue moves all and undoes all`() = runTest {
        f.task("قدیمی ۱", f.at(-3, 9))
        f.task("قدیمی ۲", TaskDue.AllDay(f.today.minusDays(1)))
        f.task("امروزی", f.at(0, 9))
        val r = run("""{"actions":[{"tool":"reschedule_overdue","args":{"when":"شنبه"}}],"reply":""}""").single()
        val saturday = jalali("1405-07-04")
        assertThat(open().filter { it.title.startsWith("قدیمی") }.map { it.due?.date }).containsExactly(saturday, saturday)
        r.undo!!.invoke()
        assertThat(open().first { it.title == "قدیمی ۱" }.due).isEqualTo(f.at(-3, 9))
    }

    @Test
    fun `list tasks and free slots are read-only outputs`() = runTest {
        f.task("جلسه", f.at(0, 11), minutes = 60)
        f.task("ناهار", f.at(0, 13), minutes = 60)
        f.task("فردا", f.at(1, 9))
        val results = run("""{"actions":[{"tool":"list_tasks","args":{"range":"today"}},{"tool":"find_free_slot","args":{"day":"امروز","minutes":90}}],"reply":""}""")
        assertThat((results[0].output as ActionOutput.TaskList).tasks.map { it.title }).containsExactly("جلسه", "ناهار").inOrder()
        val slots = results[1].output as ActionOutput.FreeSlots
        // 10:00 now; 11–12 and 13–14 are busy: 10:00–11:00 is too short, 12–13 too, 14:00 fits.
        assertThat(slots.starts).containsExactly(LocalTime.of(14, 0))
        assertThat(results.all { it.undo == null }).isTrue()
    }

    @Test
    fun `free slots list several gaps in an empty day`() {
        val starts = FreeSlots.find(f.today.plusDays(1), 60, emptyList(), f.clock.now)
        assertThat(starts).containsExactly(LocalTime.of(8, 0))
        val now = f.clock.now.withHour(9).withMinute(7)
        assertThat(FreeSlots.find(f.today, 30, emptyList(), now)).containsExactly(LocalTime.of(9, 10))
        assertThat(FreeSlots.find(f.today, 30, emptyList(), f.clock.now.withHour(21).withMinute(50))).isEmpty()
    }

    @Test
    fun `habits are created and logged with undo`() = runTest {
        f.habit("آب", perDay = 8)
        val results = run("""{"actions":[{"tool":"log_habit","args":{"habit":"آب","count":null}},{"tool":"create_habit","args":{"name":"کتاب","per_week":null,"per_day":1,"reminder":null}}],"reply":""}""")
        assertThat(results.all { it.ok }).isTrue()
        val water = f.habits.all().first { it.name == "آب" }
        assertThat(f.habits.log(water.id, f.today)?.count).isEqualTo(1)
        assertThat(results[0].message).contains("۱/۸")
        assertThat(f.habits.all().map { it.name }).contains("کتاب")
        results.reversed().forEach { it.undo!!.invoke() }
        assertThat(f.habits.log(water.id, f.today)?.count ?: 0).isEqualTo(0)
        assertThat(f.habits.all().map { it.name }).containsExactly("آب")
    }

    @Test
    fun `focus starts on a task and undo stops it`() = runTest {
        val t = f.task("مقاله")
        val r = run("""{"actions":[{"tool":"start_focus","args":{"task":"مقاله"}}],"reply":""}""").single()
        val state = f.focusStore.state.first()
        assertThat(state).isInstanceOf(FocusState.Running::class.java)
        assertThat((state as FocusState.Running).taskId).isEqualTo(t.id)
        r.undo!!.invoke()
        assertThat(f.focusStore.state.first()).isEqualTo(FocusState.Idle)
    }

    @Test
    fun `problems are reported without running anything`() = runTest {
        val r = run("""{"actions":[{"tool":"complete_task","args":{"task":"ناموجود"}}],"reply":""}""").single()
        assertThat(r.ok).isFalse()
        assertThat(r.message).contains("ناموجود")
    }
}
