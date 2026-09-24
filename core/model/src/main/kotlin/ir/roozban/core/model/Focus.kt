package ir.roozban.core.model

import java.time.Duration
import java.time.Instant

enum class FocusPhase {
    WORK, SHORT_BREAK, LONG_BREAK;

    val isBreak: Boolean get() = this != WORK
}

data class FocusSettings(
    val workMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    /** A long break follows every Nth focus period. */
    val cyclesBeforeLongBreak: Int = 4,
    val autoStartBreaks: Boolean = true,
    val autoStartWork: Boolean = false,
    /** Turn on Do Not Disturb while focusing. */
    val silence: Boolean = true,
) {
    fun minutesOf(phase: FocusPhase): Int = when (phase) {
        FocusPhase.WORK -> workMinutes
        FocusPhase.SHORT_BREAK -> shortBreakMinutes
        FocusPhase.LONG_BREAK -> longBreakMinutes
    }

    companion object {
        val WORK_RANGE = 5..120
        val BREAK_RANGE = 1..60
        val CYCLES_RANGE = 2..8
    }
}

/**
 * The pomodoro timer. It is a plain value persisted between process deaths: no service keeps it
 * alive, an exact alarm wakes the app when [Running.endsAt] arrives.
 */
sealed interface FocusState {
    val taskId: String? get() = null

    data object Idle : FocusState

    data class Running(
        val phase: FocusPhase,
        val cycle: Int,
        override val taskId: String?,
        /** When this phase first started (before any pause). */
        val phaseStartedAt: Instant,
        /** Start of the current uninterrupted segment. */
        val segmentStartedAt: Instant,
        val endsAt: Instant,
        /** Time spent in earlier segments of this phase. */
        val elapsedBeforeMillis: Long = 0,
    ) : FocusState {
        fun elapsedMillis(now: Instant): Long =
            elapsedBeforeMillis + Duration.between(segmentStartedAt, minOf(now, endsAt)).toMillis().coerceAtLeast(0)

        fun remainingMillis(now: Instant): Long = Duration.between(now, endsAt).toMillis().coerceAtLeast(0)
    }

    data class Paused(
        val phase: FocusPhase,
        val cycle: Int,
        override val taskId: String?,
        val phaseStartedAt: Instant,
        val remainingMillis: Long,
        val elapsedMillis: Long,
    ) : FocusState

    /** A phase finished and the next one waits for the user to start it. */
    data class Ready(val phase: FocusPhase, val cycle: Int, override val taskId: String?) : FocusState
}

/** One focus (work) period, finished or stopped early. Breaks are not recorded. */
data class FocusSession(
    val id: String,
    val taskId: String?,
    val startedAt: Instant,
    val endedAt: Instant,
    val plannedMinutes: Int,
    val focusedSeconds: Long,
    /** False when the user stopped or skipped before the timer ran out. */
    val completed: Boolean,
)

enum class TimeSource { FOCUS, MANUAL }

/** Time spent on a task, used by the reports. */
data class TimeEntry(
    val id: String,
    val taskId: String?,
    val start: Instant,
    val end: Instant,
    val source: TimeSource,
) {
    val seconds: Long get() = Duration.between(start, end).seconds.coerceAtLeast(0)
}
