@file:OptIn(ExperimentalCoroutinesApi::class)

package ir.roozban.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.FocusRepository
import ir.roozban.core.domain.HabitRepository
import ir.roozban.core.domain.Period
import ir.roozban.core.domain.ProjectRepository
import ir.roozban.core.domain.Report
import ir.roozban.core.domain.ReportBuilder
import ir.roozban.core.domain.ReportRange
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.model.Project
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Builds [Report]s from the repositories; shared by the reports and review screens. */
class ReportSource @Inject constructor(
    private val tasks: TaskRepository,
    private val focus: FocusRepository,
    private val habits: HabitRepository,
    private val clock: Clock,
) {
    fun report(period: Period): Flow<Report> {
        val zone = clock.zone
        val from = period.startInstant(zone)
        val until = period.endInstant(zone)
        return combine(
            tasks.observeCompletionEvents(from, until),
            focus.observeSessions(from, until),
            focus.observeTracked(from, until),
            habits.observeHabits(),
            habits.observeLogs(period.start, period.end),
        ) { completions, sessions, tracked, habitList, logs ->
            ReportBuilder.build(period, completions, sessions, tracked, habitList, logs, zone, LocalDate.now(clock))
        }
    }
}

data class ReportsUiState(
    val range: ReportRange = ReportRange.WEEK,
    val report: Report? = null,
    val previous: Report? = null,
    val projects: Map<String, Project> = emptyMap(),
    val isCurrent: Boolean = true,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val source: ReportSource,
    projects: ProjectRepository,
    private val clock: Clock,
) : ViewModel() {
    private val range = MutableStateFlow(ReportRange.WEEK)
    private val anchor = MutableStateFlow(LocalDate.now(clock))

    val state: StateFlow<ReportsUiState> = combine(range, anchor, ::Pair).flatMapLatest { (r, date) ->
        val period = r.periodOf(date)
        combine(source.report(period), source.report(r.previous(period)), projects.observeProjects()) { report, previous, list ->
            ReportsUiState(r, report, previous, list.associateBy { it.id }, isCurrent = LocalDate.now(clock) in period)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun setRange(r: ReportRange) {
        range.value = r
        anchor.value = LocalDate.now(clock)
    }

    fun previous() {
        anchor.value = range.value.previous(range.value.periodOf(anchor.value)).start
    }

    fun next() {
        if (state.value.isCurrent) return
        anchor.value = range.value.next(range.value.periodOf(anchor.value)).start
    }
}
