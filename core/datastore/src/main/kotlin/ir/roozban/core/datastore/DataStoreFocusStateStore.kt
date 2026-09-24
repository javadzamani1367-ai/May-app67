package ir.roozban.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ir.roozban.core.domain.FocusStateStore
import ir.roozban.core.model.FocusPhase
import ir.roozban.core.model.FocusState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/** The pomodoro timer's state, in its own DataStore file so it survives process death and reboots. */
class DataStoreFocusStateStore(private val store: DataStore<Preferences>) : FocusStateStore {

    override val state: Flow<FocusState> = store.data.map { it.toState() }.distinctUntilChanged()

    override suspend fun get(): FocusState = state.first()

    override suspend fun set(state: FocusState) {
        store.edit { p ->
            p.clear()
            when (state) {
                FocusState.Idle -> p[Keys.KIND] = IDLE
                is FocusState.Running -> {
                    p[Keys.KIND] = RUNNING
                    p[Keys.PHASE] = state.phase.name
                    p[Keys.CYCLE] = state.cycle
                    state.taskId?.let { p[Keys.TASK] = it }
                    p[Keys.PHASE_START] = state.phaseStartedAt.toEpochMilli()
                    p[Keys.SEGMENT_START] = state.segmentStartedAt.toEpochMilli()
                    p[Keys.ENDS_AT] = state.endsAt.toEpochMilli()
                    p[Keys.ELAPSED] = state.elapsedBeforeMillis
                }
                is FocusState.Paused -> {
                    p[Keys.KIND] = PAUSED
                    p[Keys.PHASE] = state.phase.name
                    p[Keys.CYCLE] = state.cycle
                    state.taskId?.let { p[Keys.TASK] = it }
                    p[Keys.PHASE_START] = state.phaseStartedAt.toEpochMilli()
                    p[Keys.REMAINING] = state.remainingMillis
                    p[Keys.ELAPSED] = state.elapsedMillis
                }
                is FocusState.Ready -> {
                    p[Keys.KIND] = READY
                    p[Keys.PHASE] = state.phase.name
                    p[Keys.CYCLE] = state.cycle
                    state.taskId?.let { p[Keys.TASK] = it }
                }
            }
        }
    }

    private object Keys {
        val KIND = stringPreferencesKey("kind")
        val PHASE = stringPreferencesKey("phase")
        val CYCLE = intPreferencesKey("cycle")
        val TASK = stringPreferencesKey("task_id")
        val PHASE_START = longPreferencesKey("phase_start")
        val SEGMENT_START = longPreferencesKey("segment_start")
        val ENDS_AT = longPreferencesKey("ends_at")
        val ELAPSED = longPreferencesKey("elapsed")
        val REMAINING = longPreferencesKey("remaining")
    }

    private companion object {
        const val IDLE = "idle"
        const val RUNNING = "running"
        const val PAUSED = "paused"
        const val READY = "ready"

        /** Anything unreadable (e.g. from a future version) falls back to Idle. */
        fun Preferences.toState(): FocusState = runCatching {
            val phase = this[Keys.PHASE]?.let(FocusPhase::valueOf)
            val cycle = this[Keys.CYCLE] ?: 1
            val task = this[Keys.TASK]
            when (this[Keys.KIND]) {
                RUNNING -> FocusState.Running(
                    phase = phase!!,
                    cycle = cycle,
                    taskId = task,
                    phaseStartedAt = Instant.ofEpochMilli(this[Keys.PHASE_START]!!),
                    segmentStartedAt = Instant.ofEpochMilli(this[Keys.SEGMENT_START]!!),
                    endsAt = Instant.ofEpochMilli(this[Keys.ENDS_AT]!!),
                    elapsedBeforeMillis = this[Keys.ELAPSED] ?: 0,
                )
                PAUSED -> FocusState.Paused(
                    phase = phase!!,
                    cycle = cycle,
                    taskId = task,
                    phaseStartedAt = Instant.ofEpochMilli(this[Keys.PHASE_START]!!),
                    remainingMillis = this[Keys.REMAINING]!!,
                    elapsedMillis = this[Keys.ELAPSED] ?: 0,
                )
                READY -> FocusState.Ready(phase!!, cycle, task)
                else -> FocusState.Idle
            }
        }.getOrDefault(FocusState.Idle)
    }
}
