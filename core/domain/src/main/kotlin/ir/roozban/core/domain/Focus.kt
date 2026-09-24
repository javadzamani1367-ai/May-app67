package ir.roozban.core.domain

import ir.roozban.core.model.FocusPhase
import ir.roozban.core.model.FocusSession
import ir.roozban.core.model.FocusSettings
import ir.roozban.core.model.FocusState
import ir.roozban.core.model.TimeEntry
import ir.roozban.core.model.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A focus period that should be recorded (ids are assigned by the caller). */
data class WorkRecord(
    val taskId: String?,
    val startedAt: Instant,
    val endedAt: Instant,
    val plannedMinutes: Int,
    val focusedMillis: Long,
    val completed: Boolean,
)

/** Pure pomodoro state machine. */
object FocusEngine {
    /** Shorter focus periods are not worth recording (an accidental start). */
    const val MIN_RECORD_MILLIS = 60_000L

    data class Transition(val state: FocusState, val record: WorkRecord? = null)

    fun start(settings: FocusSettings, taskId: String?, now: Instant, phase: FocusPhase = FocusPhase.WORK, cycle: Int = 1) =
        FocusState.Running(
            phase = phase,
            cycle = cycle,
            taskId = taskId,
            phaseStartedAt = now,
            segmentStartedAt = now,
            endsAt = now.plus(Duration.ofMinutes(settings.minutesOf(phase).toLong())),
        )

    fun pause(s: FocusState.Running, now: Instant) = FocusState.Paused(
        phase = s.phase,
        cycle = s.cycle,
        taskId = s.taskId,
        phaseStartedAt = s.phaseStartedAt,
        remainingMillis = s.remainingMillis(now),
        elapsedMillis = s.elapsedMillis(now),
    )

    fun resume(s: FocusState.Paused, now: Instant) = FocusState.Running(
        phase = s.phase,
        cycle = s.cycle,
        taskId = s.taskId,
        phaseStartedAt = s.phaseStartedAt,
        segmentStartedAt = now,
        endsAt = now.plusMillis(s.remainingMillis),
        elapsedBeforeMillis = s.elapsedMillis,
    )

    /** The phase after [phase]: a long break after every Nth focus period, cycles restart after it. */
    fun following(phase: FocusPhase, cycle: Int, settings: FocusSettings): Pair<FocusPhase, Int> = when (phase) {
        FocusPhase.WORK ->
            if (cycle % settings.cyclesBeforeLongBreak == 0) FocusPhase.LONG_BREAK to cycle else FocusPhase.SHORT_BREAK to cycle
        FocusPhase.SHORT_BREAK -> FocusPhase.WORK to cycle + 1
        FocusPhase.LONG_BREAK -> FocusPhase.WORK to 1
    }

    /** The timer ran out. The next phase starts at [now] (not at the planned end) if auto-start is on. */
    fun finish(s: FocusState.Running, settings: FocusSettings, now: Instant): Transition {
        val record = if (s.phase == FocusPhase.WORK) {
            WorkRecord(s.taskId, s.phaseStartedAt, s.endsAt, settings.workMinutes, s.elapsedMillis(s.endsAt), completed = true)
        } else {
            null
        }
        return Transition(nextState(s.phase, s.cycle, s.taskId, settings, now), record)
    }

    /** Stop early; focus time so far is kept. */
    fun stop(s: FocusState, settings: FocusSettings, now: Instant): Transition = Transition(FocusState.Idle, partial(s, settings, now))

    /** Ends the current phase now and starts the following one. */
    fun skip(s: FocusState, settings: FocusSettings, now: Instant): Transition {
        val (phase, cycle) = when (s) {
            is FocusState.Running -> following(s.phase, s.cycle, settings)
            is FocusState.Paused -> following(s.phase, s.cycle, settings)
            is FocusState.Ready -> following(s.phase, s.cycle, settings)
            FocusState.Idle -> return Transition(s)
        }
        return Transition(start(settings, s.taskId, now, phase, cycle), partial(s, settings, now))
    }

    private fun nextState(finished: FocusPhase, cycle: Int, taskId: String?, settings: FocusSettings, now: Instant): FocusState {
        val (phase, nextCycle) = following(finished, cycle, settings)
        val auto = if (phase.isBreak) settings.autoStartBreaks else settings.autoStartWork
        return if (auto) start(settings, taskId, now, phase, nextCycle) else FocusState.Ready(phase, nextCycle, taskId)
    }

    private fun partial(s: FocusState, settings: FocusSettings, now: Instant): WorkRecord? {
        val (phase, started, elapsed) = when (s) {
            is FocusState.Running -> Triple(s.phase, s.phaseStartedAt, s.elapsedMillis(now))
            is FocusState.Paused -> Triple(s.phase, s.phaseStartedAt, s.elapsedMillis)
            else -> return null
        }
        if (phase != FocusPhase.WORK || elapsed < MIN_RECORD_MILLIS) return null
        return WorkRecord(s.taskId, started, now, settings.workMinutes, elapsed, completed = false)
    }
}

/**
 * Runs the pomodoro timer: persists its state, records focus time, keeps the alarm, the ongoing
 * notification and Do Not Disturb in step with the state. Every entry point is serialized.
 */
@Singleton
class FocusService @Inject constructor(
    private val store: FocusStateStore,
    private val repository: FocusRepository,
    private val system: FocusSystem,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    val state: Flow<FocusState> = store.state

    fun canSilence(): Boolean = system.canSilence()

    /** Applies new settings; a running phase keeps its length, silencing follows at once. */
    suspend fun updateSettings(transform: (FocusSettings) -> FocusSettings) {
        settings.update { it.copy(focus = transform(it.focus)) }
        transition { s, _, _ -> FocusEngine.Transition(s) }
    }

    /** Starts a new focus period (replacing whatever was going on). */
    suspend fun start(taskId: String?) = transition { s, fs, now ->
        val stopped = FocusEngine.stop(s, fs, now)
        FocusEngine.Transition(FocusEngine.start(fs, taskId, now), stopped.record)
    }

    /** Starts the phase a [FocusState.Ready] is waiting for. */
    suspend fun startNext() = transition { s, fs, now ->
        if (s is FocusState.Ready) FocusEngine.Transition(FocusEngine.start(fs, s.taskId, now, s.phase, s.cycle)) else null
    }

    suspend fun pause() = transition { s, _, now ->
        if (s is FocusState.Running) FocusEngine.Transition(FocusEngine.pause(s, now)) else null
    }

    suspend fun resume() = transition { s, _, now ->
        if (s is FocusState.Paused) FocusEngine.Transition(FocusEngine.resume(s, now)) else null
    }

    suspend fun stop() = transition { s, fs, now -> if (s == FocusState.Idle) null else FocusEngine.stop(s, fs, now) }

    suspend fun skip() = transition { s, fs, now -> if (s == FocusState.Idle) null else FocusEngine.skip(s, fs, now) }

    /** Changes the task the current period counts towards. */
    suspend fun setTask(taskId: String?) = transition { s, _, _ ->
        when (s) {
            is FocusState.Running -> FocusEngine.Transition(s.copy(taskId = taskId))
            is FocusState.Paused -> FocusEngine.Transition(s.copy(taskId = taskId))
            is FocusState.Ready -> FocusEngine.Transition(s.copy(taskId = taskId))
            FocusState.Idle -> null
        }
    }

    /**
     * The end alarm fired, or the app is re-syncing after a reboot / time change. Finishes a phase
     * whose time is up, otherwise just restores the alarm and notification.
     */
    suspend fun onAlarm() = mutex.withLock {
        val s = store.get()
        val fs = settings.current().focus
        val now = Instant.now(clock)
        if (s is FocusState.Running && !now.isBefore(s.endsAt.minusSeconds(EARLY_TOLERANCE_SECONDS))) {
            apply(s, FocusEngine.finish(s, fs, now), fs, announce = true)
        } else {
            apply(s, FocusEngine.Transition(s), fs, announce = false)
        }
    }

    private suspend fun transition(block: (FocusState, FocusSettings, Instant) -> FocusEngine.Transition?) = mutex.withLock {
        val s = store.get()
        val fs = settings.current().focus
        val t = block(s, fs, Instant.now(clock)) ?: return@withLock
        apply(s, t, fs, announce = false)
    }

    private suspend fun apply(old: FocusState, t: FocusEngine.Transition, fs: FocusSettings, announce: Boolean) {
        val new = t.state
        if (new != old) store.set(new)
        t.record?.let { record(it) }
        when (new) {
            is FocusState.Running -> system.scheduleEnd(new.endsAt.toEpochMilli())
            else -> system.cancelEnd()
        }
        val focusing = (new as? FocusState.Running)?.phase == FocusPhase.WORK || (new as? FocusState.Paused)?.phase == FocusPhase.WORK
        system.setSilenced(fs.silence && focusing)
        val title = new.taskId?.let { tasks.get(it)?.title }
        val finished = (old as? FocusState.Running)?.phase
        if (announce && finished != null) system.announcePhaseEnd(finished, new, title) else system.show(new, title)
    }

    private suspend fun record(r: WorkRecord) {
        val session = FocusSession(
            id = UUID.randomUUID().toString(),
            taskId = r.taskId,
            startedAt = r.startedAt,
            endedAt = r.endedAt,
            plannedMinutes = r.plannedMinutes,
            focusedSeconds = r.focusedMillis / 1000,
            completed = r.completed,
        )
        val entry = TimeEntry(
            id = UUID.randomUUID().toString(),
            taskId = r.taskId,
            start = r.endedAt.minusMillis(r.focusedMillis),
            end = r.endedAt,
            source = TimeSource.FOCUS,
        )
        repository.record(session, entry)
    }

    companion object {
        /** An alarm may fire a moment early; treat it as on time. */
        const val EARLY_TOLERANCE_SECONDS = 2L
    }
}
