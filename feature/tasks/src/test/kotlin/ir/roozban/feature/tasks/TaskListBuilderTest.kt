package ir.roozban.feature.tasks

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.Quadrant
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.testing.jalali
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class TaskListBuilderTest {
    private val today = jalali("1405-07-02")
    private val now = today.atTime(10, 0)

    private fun task(id: String, due: TaskDue?, important: Boolean = false, urgent: Boolean = false, created: Long = 0) =
        Task(id = id, title = id, due = due, important = important, urgent = urgent, createdAt = Instant.ofEpochMilli(created), updatedAt = Instant.EPOCH)

    private val tasks = listOf(
        task("overdue", TaskDue.AllDay(today.minusDays(2))),
        task("morning", TaskDue.At(today, LocalTime.of(9, 0))),
        task("evening", TaskDue.At(today, LocalTime.of(20, 0))),
        task("tomorrow", TaskDue.At(today.plusDays(1), LocalTime.of(17, 0))),
        task("sunday", TaskDue.AllDay(today.plusDays(3))),
        task("aban", TaskDue.AllDay(jalali("1405-08-15"))),
        task("azar", TaskDue.AllDay(jalali("1405-09-01"))),
        task("inbox-low", null, created = 1),
        task("inbox-urgent-important", null, important = true, urgent = true, created = 2),
    )

    @Test
    fun today() {
        val state = TaskListBuilder.build(ListMode.TODAY, tasks, emptyList(), now)
        assertThat(state.sections.map { it.kind to it.tasks.map { t -> t.id } }).containsExactly(
            SectionKind.OVERDUE to listOf("overdue"),
            SectionKind.TODAY to listOf("morning", "evening"),
        ).inOrder()
        val morning = state.sections[1].tasks[0]
        assertThat(morning.dueLabel).isEqualTo("۰۹:۰۰")
        assertThat(morning.overdue).isTrue() // 09:00 has passed at 10:00
        assertThat(state.sections[1].tasks[1].overdue).isFalse()
        assertThat(state.sections[0].tasks[0].dueLabel).isEqualTo("۳۱ شهریور")
        assertThat(state.header?.weekday).isEqualTo("پنجشنبه")
    }

    @Test
    fun upcoming() {
        val state = TaskListBuilder.build(ListMode.UPCOMING, tasks, emptyList(), now)
        assertThat(state.sections.map { it.title to it.tasks.map { t -> t.id } }).containsExactly(
            "فردا · ۳ مهر" to listOf("tomorrow"),
            "یکشنبه · ۵ مهر" to listOf("sunday"),
            "آبان ۱۴۰۵" to listOf("aban"),
            "آذر ۱۴۰۵" to listOf("azar"),
        ).inOrder()
        assertThat(state.sections[0].tasks[0].dueLabel).isEqualTo("۱۷:۰۰")
        assertThat(state.sections[2].tasks[0].dueLabel).isEqualTo("۱۵ آبان")
        assertThat(state.header).isNull()
    }

    @Test
    fun `inbox sorts by Eisenhower quadrant`() {
        val state = TaskListBuilder.build(ListMode.INBOX, tasks, emptyList(), now)
        val items = state.sections.single().tasks
        assertThat(items.map { it.id }).containsExactly("inbox-urgent-important", "inbox-low").inOrder()
        assertThat(items[0].quadrant).isEqualTo(Quadrant.DO_FIRST)
    }

    @Test
    fun empty() {
        assertThat(TaskListBuilder.build(ListMode.INBOX, emptyList(), emptyList(), now).isEmpty).isTrue()
    }
}
