package ir.roozban.core.testing

import ir.roozban.core.domain.FocusRepository
import ir.roozban.core.domain.FocusStateStore
import ir.roozban.core.domain.FocusSystem
import ir.roozban.core.domain.HabitRepository
import ir.roozban.core.domain.ReviewKind
import ir.roozban.core.domain.RoutineAlarms
import ir.roozban.core.domain.TrackedTime
import ir.roozban.core.model.FocusPhase
import ir.roozban.core.model.FocusSession
import ir.roozban.core.model.FocusState
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitLog
import ir.roozban.core.model.TimeEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class FakeFocusRepository(private val tasks: FakeTaskRepository? = null) : FocusRepository {
    val sessions = MutableStateFlow<List<FocusSession>>(emptyList())
    val entries = MutableStateFlow<List<TimeEntry>>(emptyList())

    override suspend fun record(session: FocusSession, entry: TimeEntry) {
        sessions.value = sessions.value + session
        entries.value = entries.value + entry
    }

    override fun observeSessions(from: Instant, until: Instant): Flow<List<FocusSession>> =
        sessions.map { l -> l.filter { !it.endedAt.isBefore(from) && it.endedAt.isBefore(until) } }

    override fun observeTracked(from: Instant, until: Instant): Flow<List<TrackedTime>> {
        val taskFlow = tasks?.tasks ?: MutableStateFlow(emptyMap())
        return combine(entries, taskFlow) { l, m ->
            l.filter { it.end.isAfter(from) && it.start.isBefore(until) }
                .map { e -> TrackedTime(e, e.taskId?.let { m[it]?.title }, e.taskId?.let { m[it]?.projectId }) }
        }
    }

    override fun observeTaskSeconds(taskId: String): Flow<Long> =
        entries.map { l -> l.filter { it.taskId == taskId }.sumOf { it.seconds } }

    override suspend fun addTimeEntry(entry: TimeEntry) {
        entries.value = entries.value + entry
    }

    override suspend fun deleteTimeEntry(id: String) {
        entries.value = entries.value.filterNot { it.id == id }
    }
}

class FakeFocusStateStore : FocusStateStore {
    override val state = MutableStateFlow<FocusState>(FocusState.Idle)
    override suspend fun get(): FocusState = state.value
    override suspend fun set(state: FocusState) {
        this.state.value = state
    }
}

class FakeFocusSystem : FocusSystem {
    /** The end alarm as a local time in Tehran, or null. */
    var alarm: LocalDateTime? = null
    var silenced = false
    var shown: FocusState = FocusState.Idle
    var shownTitle: String? = null
    val announced = mutableListOf<Pair<FocusPhase, FocusState>>()
    var silenceAvailable = true

    override fun scheduleEnd(atEpochMillis: Long) {
        alarm = LocalDateTime.ofInstant(Instant.ofEpochMilli(atEpochMillis), TEHRAN)
    }

    override fun cancelEnd() {
        alarm = null
    }

    override fun show(state: FocusState, taskTitle: String?) {
        shown = state
        shownTitle = taskTitle
    }

    override fun announcePhaseEnd(finished: FocusPhase, next: FocusState, taskTitle: String?) {
        announced += finished to next
        shown = next
        shownTitle = taskTitle
    }

    override fun setSilenced(on: Boolean): Boolean {
        if (!silenceAvailable) return false
        silenced = on
        return true
    }
}

class FakeHabitRepository : HabitRepository {
    val habits = MutableStateFlow<Map<String, Habit>>(emptyMap())
    val logs = MutableStateFlow<Map<Pair<String, LocalDate>, HabitLog>>(emptyMap())

    override fun observeHabits(): Flow<List<Habit>> =
        habits.map { m -> m.values.sortedWith(compareBy({ it.archived }, { it.sortOrder })) }

    override fun observeHabit(id: String): Flow<Habit?> = habits.map { it[id] }

    override fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<HabitLog>> =
        logs.map { m -> m.values.filter { !it.date.isBefore(from) && !it.date.isAfter(to) } }

    override fun observeHabitLogs(habitId: String): Flow<List<HabitLog>> =
        logs.map { m -> m.values.filter { it.habitId == habitId }.sortedBy { it.date } }

    override suspend fun get(id: String) = habits.value[id]
    override suspend fun all() = habits.value.values.toList()
    override suspend fun upsert(habit: Habit) {
        habits.value = habits.value + (habit.id to habit)
    }

    override suspend fun delete(id: String) {
        habits.value = habits.value - id
        logs.value = logs.value.filterKeys { it.first != id }
    }

    override suspend fun log(habitId: String, date: LocalDate) = logs.value[habitId to date]

    override suspend fun setLog(log: HabitLog) {
        val key = log.habitId to log.date
        logs.value = if (log.count <= 0) logs.value - key else logs.value + (key to log)
    }
}

class FakeRoutineAlarms : RoutineAlarms {
    val habits = mutableMapOf<String, LocalDateTime>()
    val reviews = mutableMapOf<ReviewKind, LocalDateTime>()

    override fun scheduleHabit(habitId: String, atEpochMillis: Long) {
        habits[habitId] = local(atEpochMillis)
    }

    override fun cancelHabit(habitId: String) {
        habits -= habitId
    }

    override fun scheduleReview(kind: ReviewKind, atEpochMillis: Long) {
        reviews[kind] = local(atEpochMillis)
    }

    override fun cancelReview(kind: ReviewKind) {
        reviews -= kind
    }

    private fun local(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), TEHRAN)
}
