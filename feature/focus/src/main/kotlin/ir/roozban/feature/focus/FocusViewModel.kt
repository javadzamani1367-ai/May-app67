@file:OptIn(ExperimentalCoroutinesApi::class)

package ir.roozban.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.Access
import ir.roozban.core.domain.Entitlements
import ir.roozban.core.domain.FocusRepository
import ir.roozban.core.domain.FocusService
import ir.roozban.core.domain.ProFeature
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.model.FocusPhase
import ir.roozban.core.model.FocusSettings
import ir.roozban.core.model.FocusState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class TaskOption(val id: String, val title: String)

data class FocusUiState(
    val timer: FocusState = FocusState.Idle,
    val phase: FocusPhase = FocusPhase.WORK,
    val cycle: Int = 1,
    val settings: FocusSettings = FocusSettings(),
    /** The task the next or current period counts towards. */
    val taskId: String? = null,
    val taskTitle: String? = null,
    val remainingMillis: Long = FocusSettings().workMinutes * 60_000L,
    val totalMillis: Long = FocusSettings().workMinutes * 60_000L,
    val todayMinutes: Int = 0,
    val todaySessions: Int = 0,
    val tasks: List<TaskOption> = emptyList(),
    val canSilence: Boolean = true,
    val access: Access = Access.FULL,
) {
    val progress: Float get() = if (totalMillis <= 0) 0f else (1f - remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
    val isRunning: Boolean get() = timer is FocusState.Running
}

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val focus: FocusService,
    private val settings: SettingsRepository,
    tasks: TaskRepository,
    repository: FocusRepository,
    private val entitlements: Entitlements,
    private val clock: Clock,
) : ViewModel() {

    /** Task picked while idle, before a period starts. */
    private val chosenTask = MutableStateFlow<String?>(null)
    private val canSilence = MutableStateFlow(focus.canSilence())

    private val today: LocalDate = LocalDate.now(clock)
    private val todaySessions = repository.observeSessions(
        today.atStartOfDay(clock.zone).toInstant(),
        today.plusDays(1).atStartOfDay(clock.zone).toInstant(),
    )

    /** Ticks every second while a period runs, otherwise stays put. */
    private val now: Flow<Instant> = focus.state.map { it is FocusState.Running }.distinctUntilChanged().flatMapLatest { running ->
        if (running) {
            flow {
                while (true) {
                    emit(Instant.now(clock))
                    delay(TICK_MS)
                }
            }
        } else {
            flowOf(Instant.now(clock))
        }
    }

    val state: StateFlow<FocusUiState> = combine(
        combine(focus.state, settings.settings, chosenTask, ::Triple),
        now,
        tasks.observeOpenTasks(),
        todaySessions,
        canSilence,
    ) { (timer, userSettings, chosen), instant, open, sessions, silence ->
        val fs = userSettings.focus
        val (phase, cycle) = when (timer) {
            is FocusState.Running -> timer.phase to timer.cycle
            is FocusState.Paused -> timer.phase to timer.cycle
            is FocusState.Ready -> timer.phase to timer.cycle
            FocusState.Idle -> FocusPhase.WORK to 1
        }
        val total = when (timer) {
            is FocusState.Running -> timer.elapsedBeforeMillis + (timer.endsAt.toEpochMilli() - timer.segmentStartedAt.toEpochMilli())
            is FocusState.Paused -> timer.elapsedMillis + timer.remainingMillis
            else -> fs.minutesOf(phase) * 60_000L
        }
        val remaining = when (timer) {
            is FocusState.Running -> timer.remainingMillis(instant)
            is FocusState.Paused -> timer.remainingMillis
            else -> total
        }
        val taskId = if (timer == FocusState.Idle) chosen else timer.taskId
        FocusUiState(
            timer = timer,
            phase = phase,
            cycle = cycle,
            settings = fs,
            taskId = taskId,
            taskTitle = taskId?.let { id -> open.firstOrNull { it.id == id }?.title },
            remainingMillis = remaining,
            totalMillis = total,
            todayMinutes = ((sessions.sumOf { it.focusedSeconds } + 30) / 60).toInt(),
            todaySessions = sessions.size,
            tasks = open.map { TaskOption(it.id, it.title) },
            canSilence = silence,
            access = entitlements.access(ProFeature.FOCUS),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    /** Preselects a task, e.g. when opened from a task. */
    fun preselect(taskId: String?) {
        if (taskId != null && state.value.timer == FocusState.Idle) chosenTask.value = taskId
    }

    fun selectTask(taskId: String?) {
        chosenTask.value = taskId
        viewModelScope.launch { focus.setTask(taskId) }
    }

    fun start() {
        if (state.value.access != Access.FULL) return
        viewModelScope.launch {
            if (state.value.timer is FocusState.Ready) focus.startNext() else focus.start(chosenTask.value)
        }
    }

    fun pause() = launch { focus.pause() }

    fun resume() = launch { focus.resume() }

    fun stop() = launch { focus.stop() }

    fun skip() = launch { focus.skip() }

    fun updateSettings(transform: (FocusSettings) -> FocusSettings) = launch {
        focus.updateSettings { s ->
            transform(s).let {
                it.copy(
                    workMinutes = it.workMinutes.coerceIn(FocusSettings.WORK_RANGE),
                    shortBreakMinutes = it.shortBreakMinutes.coerceIn(FocusSettings.BREAK_RANGE),
                    longBreakMinutes = it.longBreakMinutes.coerceIn(FocusSettings.BREAK_RANGE),
                    cyclesBeforeLongBreak = it.cyclesBeforeLongBreak.coerceIn(FocusSettings.CYCLES_RANGE),
                )
            }
        }
    }

    /** Called when returning from the Do Not Disturb access screen. */
    fun refreshSilenceAccess() {
        canSilence.value = focus.canSilence()
        launch { focus.updateSettings { it } }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}

/** Status for the small bar shown above the navigation while a timer is active. */
data class FocusBarState(val visible: Boolean = false, val phase: FocusPhase = FocusPhase.WORK, val running: Boolean = false, val remainingMillis: Long = 0)

@HiltViewModel
class FocusBarViewModel @Inject constructor(private val focus: FocusService, private val clock: Clock) : ViewModel() {
    val state: StateFlow<FocusBarState> = focus.state.flatMapLatest { timer ->
        when (timer) {
            is FocusState.Running -> flow {
                while (true) {
                    emit(FocusBarState(true, timer.phase, true, timer.remainingMillis(Instant.now(clock))))
                    delay(1_000)
                }
            }
            is FocusState.Paused -> flowOf(FocusBarState(true, timer.phase, false, timer.remainingMillis))
            is FocusState.Ready -> flowOf(FocusBarState(true, timer.phase, false, 0))
            FocusState.Idle -> flowOf(FocusBarState())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusBarState())

    fun toggle() {
        val s = state.value
        viewModelScope.launch {
            when {
                s.running -> focus.pause()
                s.remainingMillis > 0 -> focus.resume()
                else -> focus.startNext()
            }
        }
    }
}
