package ir.roozban.feature.tasks.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.calendar.IranCities
import ir.roozban.core.calendar.IranOccasions
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PrayerTimes
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.domain.EventDates
import ir.roozban.core.domain.EventRepository
import ir.roozban.core.domain.EventUseCases
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.domain.UpdateTaskUseCase
import ir.roozban.core.model.TaskDue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val events: EventRepository,
    private val eventUseCases: EventUseCases,
    private val updateTask: UpdateTaskUseCase,
    private val clock: Clock,
) : ViewModel() {

    private data class Position(val mode: CalendarMode, val anchor: LocalDate, val selected: LocalDate)

    private val position = MutableStateFlow(LocalDate.now(clock).let { Position(CalendarMode.MONTH, it, it) })

    val state: StateFlow<CalendarUiState> = combine(
        position,
        tasks.observeOpenTasks(),
        settings.settings,
        events.observeEvents(),
    ) { pos, open, s, personal ->
        val today = LocalDate.now(clock)
        val j = pos.anchor.toJalali()
        val monthStart = JalaliDate.of(j.year, j.month, 1).toLocalDate()
        val monthEnd = JalaliDate.of(j.year, j.month, 1).lastDayOfMonth().toLocalDate()
        val city = IranCities.byId(s.prayerCity)
        CalendarUiState(
            mode = pos.mode,
            anchor = pos.anchor,
            title = CalendarBuilder.title(pos.mode, pos.anchor),
            days = CalendarBuilder.build(pos.mode, pos.anchor, today, open, s.showGregorian, s.showHijri, s.hijriOffset, personal),
            selected = pos.selected,
            today = today,
            monthOccasions = IranOccasions.forJalaliMonth(j.year, j.month, s.hijriOffset),
            monthEvents = personal.flatMap { EventDates.between(it, monthStart, monthEnd, s.hijriOffset) }.sortedBy { it.date },
            upcoming = personal.mapNotNull { EventDates.next(it, today, s.hijriOffset) }.sortedBy { it.date },
            city = city,
            prayer = city?.let { PrayerTimes.compute(pos.selected, it, clock.zone) },
            hijriOffset = s.hijriOffset,
            showGregorian = s.showGregorian,
            showHijri = s.showHijri,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState(anchor = LocalDate.now(clock)))

    fun setMode(mode: CalendarMode) {
        position.value = position.value.let { it.copy(mode = mode, anchor = it.selected) }
    }

    fun next() = shift(1)

    fun previous() = shift(-1)

    private fun shift(steps: Long) {
        position.value = position.value.let { p ->
            val anchor = CalendarBuilder.shift(p.mode, p.anchor, steps)
            p.copy(anchor = anchor, selected = anchor)
        }
    }

    fun goToToday() {
        val today = LocalDate.now(clock)
        position.value = position.value.copy(anchor = today, selected = today)
    }

    fun select(date: LocalDate) {
        position.value = position.value.copy(selected = date)
    }

    /** Jumps to any date (month view keeps the month grid). */
    fun goTo(date: LocalDate) {
        position.value = position.value.copy(anchor = date, selected = date)
    }

    fun setPrayerCity(id: String?) {
        viewModelScope.launch { settings.update { it.copy(prayerCity = id) } }
    }

    fun saveEvent(draft: EventDraft) {
        if (draft.title.isBlank()) return
        viewModelScope.launch {
            val offset = settings.current().hijriOffset
            val existing = draft.id?.let { events.get(it) }
            if (existing == null) {
                eventUseCases.create(
                    draft.title, draft.kind, draft.color, draft.date, draft.calendar, draft.knownYear,
                    draft.remindDaysBefore, draft.reminderTime, draft.notes, offset,
                )
            } else {
                val (y, m, d) = EventUseCases.parts(draft.date, draft.calendar, offset) ?: return@launch
                eventUseCases.update(
                    existing.copy(
                        title = draft.title, kind = draft.kind, color = draft.color, calendar = draft.calendar,
                        month = m, day = d, year = if (draft.knownYear) y else null,
                        remindDaysBefore = draft.remindDaysBefore, reminderTime = draft.reminderTime, notes = draft.notes,
                    ),
                )
            }
        }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch { events.get(id)?.let { eventUseCases.delete(it) } }
    }

    /** Opens a day from the month grid. */
    fun openDay(date: LocalDate) {
        position.value = Position(CalendarMode.DAY, date, date)
    }

    /** Drag-and-drop result: puts the task on [date] at [time] (a time slot on the grid). */
    fun moveTask(taskId: String, date: LocalDate, time: LocalTime) {
        viewModelScope.launch {
            val task = tasks.get(taskId) ?: return@launch
            val due = TaskDue.At(date, time)
            if (task.due == due) return@launch
            // A recurring series keeps its phase; only this occurrence moves.
            updateTask(task.copy(due = due))
        }
    }
}

/** What the event editor produces; [id] null = new. */
data class EventDraft(
    val id: String? = null,
    val title: String = "",
    val kind: ir.roozban.core.model.EventKind = ir.roozban.core.model.EventKind.BIRTHDAY,
    val color: Int = 6,
    val date: LocalDate,
    val calendar: ir.roozban.core.model.EventCalendar = ir.roozban.core.model.EventCalendar.JALALI,
    val knownYear: Boolean = true,
    val remindDaysBefore: Set<Int> = setOf(0, 1),
    val reminderTime: LocalTime = LocalTime.of(9, 0),
    val notes: String = "",
) {
    companion object
}
