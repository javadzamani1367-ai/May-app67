package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.FocusPhase
import ir.roozban.core.model.FocusSettings
import ir.roozban.core.model.FocusState
import ir.roozban.core.model.TimeSource
import ir.roozban.core.model.UserSettings
import ir.roozban.core.testing.FakeFocusRepository
import ir.roozban.core.testing.FakeFocusStateStore
import ir.roozban.core.testing.FakeFocusSystem
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FocusServiceTest {

    private class F(focus: FocusSettings = FocusSettings()) {
        val h = Harness(UserSettings(focus = focus))
        val store = FakeFocusStateStore()
        val repo = FakeFocusRepository(h.tasks)
        val system = FakeFocusSystem()
        val service = FocusService(store, repo, system, h.tasks, h.settings, h.clock)

        fun advance(minutes: Long, seconds: Long = 0) {
            h.clock.now = h.clock.now.plusMinutes(minutes).plusSeconds(seconds)
        }
    }

    @Test
    fun `starting schedules the end, silences and shows the task`() = runTest {
        val f = F()
        val task = f.h.quickAdd("نوشتن گزارش")
        f.service.start(task.id)
        val s = f.store.state.value as FocusState.Running
        assertThat(s.phase).isEqualTo(FocusPhase.WORK)
        assertThat(s.cycle).isEqualTo(1)
        assertThat(f.system.alarm).isEqualTo(f.h.clock.now.plusMinutes(25))
        assertThat(f.system.silenced).isTrue()
        assertThat(f.system.shownTitle).isEqualTo("نوشتن گزارش")
    }

    @Test
    fun `no silencing when turned off`() = runTest {
        val f = F(FocusSettings(silence = false))
        f.service.start(null)
        assertThat(f.system.silenced).isFalse()
    }

    @Test
    fun `pausing moves the end by the paused time`() = runTest {
        val f = F()
        f.service.start(null)
        val started = f.h.clock.now
        f.advance(10)
        f.service.pause()
        assertThat(f.system.alarm).isNull()
        assertThat((f.store.state.value as FocusState.Paused).remainingMillis).isEqualTo(15 * 60_000L)
        f.advance(7)
        f.service.resume()
        assertThat(f.system.alarm).isEqualTo(started.plusMinutes(32))
    }

    @Test
    fun `a finished focus period is recorded and the break starts on its own`() = runTest {
        val f = F()
        val task = f.h.quickAdd("مطالعه")
        f.service.start(task.id)
        f.advance(10)
        f.service.pause()
        f.advance(5)
        f.service.resume()
        f.advance(15)
        f.service.onAlarm()

        val session = f.repo.sessions.value.single()
        assertThat(session.completed).isTrue()
        assertThat(session.focusedSeconds).isEqualTo(25 * 60L)
        assertThat(session.taskId).isEqualTo(task.id)
        val entry = f.repo.entries.value.single()
        assertThat(entry.source).isEqualTo(TimeSource.FOCUS)
        assertThat(entry.seconds).isEqualTo(25 * 60L)

        val next = f.store.state.value as FocusState.Running
        assertThat(next.phase).isEqualTo(FocusPhase.SHORT_BREAK)
        assertThat(f.system.alarm).isEqualTo(f.h.clock.now.plusMinutes(5))
        assertThat(f.system.silenced).isFalse()
        assertThat(f.system.announced.single().first).isEqualTo(FocusPhase.WORK)
    }

    @Test
    fun `every fourth focus period is followed by a long break, then cycles restart`() = runTest {
        val f = F(FocusSettings(autoStartWork = true))
        f.service.start(null)
        val phases = mutableListOf<Pair<FocusPhase, Int>>()
        repeat(9) {
            val s = f.store.state.value as FocusState.Running
            phases += s.phase to s.cycle
            f.h.clock.now = java.time.LocalDateTime.ofInstant(s.endsAt, ir.roozban.core.testing.TEHRAN)
            f.service.onAlarm()
        }
        assertThat(phases).containsExactly(
            FocusPhase.WORK to 1, FocusPhase.SHORT_BREAK to 1,
            FocusPhase.WORK to 2, FocusPhase.SHORT_BREAK to 2,
            FocusPhase.WORK to 3, FocusPhase.SHORT_BREAK to 3,
            FocusPhase.WORK to 4, FocusPhase.LONG_BREAK to 4,
            FocusPhase.WORK to 1,
        ).inOrder()
        assertThat(f.repo.sessions.value).hasSize(5)
    }

    @Test
    fun `without auto start the next phase waits`() = runTest {
        val f = F(FocusSettings(autoStartBreaks = false))
        f.service.start(null)
        f.advance(25)
        f.service.onAlarm()
        assertThat(f.store.state.value).isEqualTo(FocusState.Ready(FocusPhase.SHORT_BREAK, 1, null))
        assertThat(f.system.alarm).isNull()
        f.service.startNext()
        assertThat((f.store.state.value as FocusState.Running).phase).isEqualTo(FocusPhase.SHORT_BREAK)
    }

    @Test
    fun `an early alarm only re-arms`() = runTest {
        val f = F()
        f.service.start(null)
        f.advance(20)
        f.service.onAlarm()
        assertThat((f.store.state.value as FocusState.Running).phase).isEqualTo(FocusPhase.WORK)
        assertThat(f.system.alarm).isEqualTo(f.h.clock.now.plusMinutes(5))
        assertThat(f.repo.sessions.value).isEmpty()
    }

    @Test
    fun `stopping keeps the time focused, but not an accidental start`() = runTest {
        val f = F()
        f.service.start(null)
        f.advance(0, 40)
        f.service.stop()
        assertThat(f.repo.sessions.value).isEmpty()
        assertThat(f.store.state.value).isEqualTo(FocusState.Idle)
        assertThat(f.system.silenced).isFalse()

        f.service.start(null)
        f.advance(12)
        f.service.stop()
        val s = f.repo.sessions.value.single()
        assertThat(s.completed).isFalse()
        assertThat(s.focusedSeconds).isEqualTo(12 * 60L)
        assertThat(f.system.shown).isEqualTo(FocusState.Idle)
    }

    @Test
    fun `skipping a break starts the next focus period`() = runTest {
        val f = F()
        f.service.start(null)
        f.advance(25)
        f.service.onAlarm()
        f.advance(1)
        f.service.skip()
        val s = f.store.state.value as FocusState.Running
        assertThat(s.phase).isEqualTo(FocusPhase.WORK)
        assertThat(s.cycle).isEqualTo(2)
        assertThat(f.system.silenced).isTrue()
    }

    @Test
    fun `after a reboot a period that ended meanwhile is finished`() = runTest {
        val f = F()
        f.service.start(null)
        f.advance(40)
        f.service.onAlarm() // reconcile path
        assertThat(f.repo.sessions.value.single().focusedSeconds).isEqualTo(25 * 60L)
        assertThat((f.store.state.value as FocusState.Running).phase).isEqualTo(FocusPhase.SHORT_BREAK)
    }

    @Test
    fun `the task can be changed mid-period`() = runTest {
        val f = F()
        val a = f.h.quickAdd("الف")
        f.service.start(null)
        f.service.setTask(a.id)
        f.advance(25)
        f.service.onAlarm()
        assertThat(f.repo.sessions.value.single().taskId).isEqualTo(a.id)
    }

    @Test
    fun `starting again records the interrupted period`() = runTest {
        val f = F()
        f.service.start(null)
        f.advance(5)
        f.service.start(null)
        assertThat(f.repo.sessions.value.single().completed).isFalse()
        assertThat(f.system.alarm).isEqualTo(f.h.clock.now.plusMinutes(25))
    }
}
