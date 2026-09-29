package ir.roozban.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.domain.AttendanceMonth
import ir.roozban.core.domain.AttendanceReport
import ir.roozban.core.domain.AttendanceRepository
import ir.roozban.core.domain.AttendanceUseCases
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.domain.Undo
import ir.roozban.core.model.AttendanceEntry
import ir.roozban.core.model.AttendanceKind
import ir.roozban.core.model.AttendanceSettings
import ir.roozban.core.model.LeaveType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class AttendanceUiState(
    val year: Int,
    val month: Int,
    val report: AttendanceMonth? = null,
    val entries: List<AttendanceEntry> = emptyList(),
    val open: AttendanceEntry? = null,
    val settings: AttendanceSettings = AttendanceSettings(),
    val today: LocalDate,
    val now: LocalDateTime,
)

data class AttendanceMessage(val text: String, val undo: Undo? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val repository: AttendanceRepository,
    private val useCases: AttendanceUseCases,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val today0 = LocalDate.now(clock).toJalali()
    private val month = MutableStateFlow(today0.year to today0.month)
    private val messages = Channel<AttendanceMessage>(Channel.BUFFERED)
    val events: Flow<AttendanceMessage> = messages.receiveAsFlow()

    /** Ticks every minute so an open check-in's time keeps growing on screen. */
    private val ticks = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(60_000)
        }
    }

    private val entries = month.flatMapLatest { (y, m) ->
        val first = JalaliDate.of(y, m, 1)
        // A day earlier, so a night shift from the previous month is split correctly.
        repository.observeBetween(first.toLocalDate().minusDays(1), first.lastDayOfMonth().toLocalDate())
    }

    val state: StateFlow<AttendanceUiState> = combine(month, entries, repository.observeOpen(), settings.settings, ticks) { (y, m), list, open, s, now ->
        AttendanceUiState(
            year = y,
            month = m,
            report = AttendanceReport.month(list, y, m, s.attendance, now, s.hijriOffset),
            entries = list,
            open = open,
            settings = s.attendance,
            today = now.toLocalDate(),
            now = now,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AttendanceUiState(today0.year, today0.month, today = LocalDate.now(clock), now = LocalDateTime.now(clock)),
    )

    fun shiftMonth(by: Int) {
        val (y, m) = month.value
        val d = JalaliDate.of(y, m, 1).plusMonths(by.toLong())
        month.value = d.year to d.month
    }

    fun checkIn() = viewModelScope.launch {
        useCases.checkIn()
        messages.send(AttendanceMessage("ورود ثبت شد"))
    }

    fun checkOut() = viewModelScope.launch {
        if (useCases.checkOut() != null) messages.send(AttendanceMessage("خروج ثبت شد"))
    }

    fun save(entry: AttendanceEntry) = viewModelScope.launch { useCases.save(entry) }

    fun create(kind: AttendanceKind, start: LocalDateTime, end: LocalDateTime?, allDay: Boolean, leaveType: LeaveType?, note: String) =
        viewModelScope.launch { useCases.save(useCases.new(kind, start, end, allDay, leaveType, note)) }

    fun delete(entry: AttendanceEntry) = viewModelScope.launch {
        messages.send(AttendanceMessage("حذف شد", useCases.delete(entry)))
    }

    fun undo(undo: Undo) = viewModelScope.launch { undo() }

    fun setSettings(value: AttendanceSettings) = viewModelScope.launch { settings.update { it.copy(attendance = value) } }

    /** The month as CSV with a byte-order mark, so spreadsheet apps read the Persian text. */
    fun csv(): ByteArray {
        val s = state.value
        val report = s.report ?: return ByteArray(0)
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + AttendanceReport.csv(report, s.today).toByteArray()
    }
}
