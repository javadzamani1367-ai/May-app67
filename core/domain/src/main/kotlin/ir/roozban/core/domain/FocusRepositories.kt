package ir.roozban.core.domain

import ir.roozban.core.model.FocusPhase
import ir.roozban.core.model.FocusSession
import ir.roozban.core.model.FocusState
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitLog
import ir.roozban.core.model.TimeEntry
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface FocusRepository {
    /** Stores a finished focus period together with the time it adds to its task. */
    suspend fun record(session: FocusSession, entry: TimeEntry)

    fun observeSessions(from: Instant, until: Instant): Flow<List<FocusSession>>

    /** Time entries overlapping [from, until), with their task's title and project. */
    fun observeTracked(from: Instant, until: Instant): Flow<List<TrackedTime>>

    /** Total recorded seconds per task id. */
    fun observeTaskSeconds(taskId: String): Flow<Long>

    suspend fun addTimeEntry(entry: TimeEntry)

    suspend fun deleteTimeEntry(id: String)
}

/** Persists the timer across process deaths and reboots. */
interface FocusStateStore {
    val state: Flow<FocusState>

    suspend fun get(): FocusState

    suspend fun set(state: FocusState)
}

/** The platform side of the timer: alarm, ongoing notification and Do Not Disturb. */
interface FocusSystem {
    fun scheduleEnd(atEpochMillis: Long)

    fun cancelEnd()

    /** Shows (or removes, for Idle) the ongoing notification. */
    fun show(state: FocusState, taskTitle: String?)

    /** Tells the user a phase ran out; [next] is the state that follows. */
    fun announcePhaseEnd(finished: FocusPhase, next: FocusState, taskTitle: String?)

    /** Returns false when silencing is not possible (e.g. no Do Not Disturb access). */
    fun setSilenced(on: Boolean): Boolean

    /** Whether Do Not Disturb can be controlled (the user granted access). */
    fun canSilence(): Boolean
}

interface HabitRepository {
    /** All habits, active first, in the user's order. */
    fun observeHabits(): Flow<List<Habit>>

    fun observeHabit(id: String): Flow<Habit?>

    /** Logs of all habits between two dates (inclusive). */
    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<HabitLog>>

    fun observeHabitLogs(habitId: String): Flow<List<HabitLog>>

    suspend fun get(id: String): Habit?

    suspend fun all(): List<Habit>

    suspend fun upsert(habit: Habit)

    /** Deletes the habit with its history. */
    suspend fun delete(id: String)

    suspend fun log(habitId: String, date: LocalDate): HabitLog?

    /** Writes a day's count; zero removes the log. */
    suspend fun setLog(log: HabitLog)
}

enum class ReviewKind { DAILY, WEEKLY }

/** Alarms for habit reminders and review nudges. Idempotent per id. */
interface RoutineAlarms {
    fun scheduleHabit(habitId: String, atEpochMillis: Long)

    fun cancelHabit(habitId: String)

    fun scheduleReview(kind: ReviewKind, atEpochMillis: Long)

    fun cancelReview(kind: ReviewKind)
}

/** A task (or one occurrence of a recurring task) being completed; input for the reports. */
data class CompletionEvent(
    val taskId: String,
    val title: String,
    val projectId: String?,
    val at: Instant,
    val estimateMinutes: Int?,
)

/** A time entry joined with its task (null when the task was deleted or none was chosen). */
data class TrackedTime(val entry: TimeEntry, val taskTitle: String?, val projectId: String?)
