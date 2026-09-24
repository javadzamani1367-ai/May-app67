package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ProjectsAndSubtasksTest {

    @Test
    fun `quick add creates the project and labels once and reuses them`() = runTest {
        val h = Harness()
        val first = h.quickAdd("خرید نان #خانه @خرید")
        val second = h.quickAdd("شستن ظرف #خانه @خريد") // Arabic ي in the label: same label
        assertThat(h.projects.all()).hasSize(1)
        assertThat(h.labels.all()).hasSize(1)
        assertThat(first.projectId).isEqualTo(second.projectId)
        assertThat(first.labelIds).isEqualTo(second.labelIds)
        assertThat(h.projects.all().single().name).isEqualTo("خانه")
    }

    @Test
    fun `adding inside a project uses it unless the text names another`() = runTest {
        val h = Harness()
        val work = ProjectUseCases(h.projects, h.clock).create("کار", color = 1)!!
        val a = h.add(h.parser.parse("گزارش", h.clock.now, h.settings.current()), defaultProjectId = work.id)!!
        val b = h.add(h.parser.parse("نان #خانه", h.clock.now, h.settings.current()), defaultProjectId = work.id)!!
        assertThat(a.projectId).isEqualTo(work.id)
        assertThat(b.projectId).isNotEqualTo(work.id)
    }

    @Test
    fun `subtasks inherit the project and are hidden from lists`() = runTest {
        val h = Harness()
        val parent = h.quickAdd("سفر #تعطیلات فردا")
        val sub = h.addSubtask(parent, "رزرو هتل")!!
        assertThat(sub.parentId).isEqualTo(parent.id)
        assertThat(sub.projectId).isEqualTo(parent.projectId)
        assertThat(sub.due).isNull()
        assertThat(h.tasks.observeOpenTasks().first().map { it.id }).containsExactly(parent.id)
        h.complete(sub)
        assertThat(h.tasks.observeSubtaskProgress().first()[parent.id]).isEqualTo(SubtaskProgress(1, 1))
    }

    @Test
    fun `new projects get increasing sort order`() = runTest {
        val h = Harness()
        val uc = ProjectUseCases(h.projects, h.clock)
        val a = uc.create("الف", 0)!!
        val b = uc.create("ب", 0)!!
        assertThat(b.sortOrder).isGreaterThan(a.sortOrder)
        assertThat(uc.create("  ", 0)).isNull()
    }
}
