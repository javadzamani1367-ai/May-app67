package ir.roozban.feature.today

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.timeparser.PersianTimeParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val clock: Clock,
    private val parser: PersianTimeParser,
) : ViewModel() {

    private val _state = MutableStateFlow(buildState(LocalDate.now(clock)))
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private val _quickAdd = MutableStateFlow(QuickAddState())
    val quickAdd: StateFlow<QuickAddState> = _quickAdd.asStateFlow()

    private var nextId = 1L

    /** Parsing is synchronous: it takes well under a millisecond, so it runs on every keystroke. */
    fun onQuickAddTextChange(text: String) {
        _quickAdd.value = QuickAddState(text, preview(text, LocalDateTime.now(clock)))
    }

    /** Returns true when a task was added (the sheet should then close). */
    fun submitQuickAdd(): Boolean {
        val preview = _quickAdd.value.preview ?: return false
        if (preview.title.isBlank()) return false
        val task = DraftTask(
            id = nextId++,
            title = preview.title,
            whenLabel = preview.chips.firstOrNull { it.kind == ChipKind.TIME }?.label,
            recurrenceLabel = preview.chips.firstOrNull { it.kind == ChipKind.RECURRENCE }?.label,
            estimateLabel = preview.chips.firstOrNull { it.kind == ChipKind.DURATION }?.label,
        )
        _state.update { it.copy(sessionTasks = it.sessionTasks + task) }
        _quickAdd.value = QuickAddState()
        return true
    }

    fun dismissQuickAdd() {
        _quickAdd.value = QuickAddState()
    }

    private fun preview(text: String, now: LocalDateTime): QuickAddPreview? {
        if (text.isBlank()) return null
        val result = parser.parse(text, now)
        val today = now.toLocalDate()
        val chips = buildList {
            result.time?.let { add(PreviewChip(ChipKind.TIME, PreviewFormatter.time(it, today))) }
            result.recurrence?.let { add(PreviewChip(ChipKind.RECURRENCE, PreviewFormatter.recurrence(it))) }
            result.estimate?.let { add(PreviewChip(ChipKind.DURATION, PreviewFormatter.duration(it))) }
        }
        return QuickAddPreview(
            title = result.title,
            chips = chips,
            highlights = result.spans.map { Highlight(it.start, it.end, it.kind) },
            needsReview = result.time != null && result.confidence < REVIEW_THRESHOLD,
        )
    }

    private companion object {
        const val REVIEW_THRESHOLD = 0.6f

        fun buildState(today: LocalDate): TodayUiState {
            val jalali = today.toJalali()
            val secondary = listOfNotNull(
                PersianDateFormatter.gregorian(today),
                PersianDateFormatter.hijri(today),
            ).joinToString(" · ")
            val start = PersianWeek.startOfWeek(today)
            val week = (0L until 7L).map { offset ->
                val day = start.plusDays(offset)
                WeekDay(
                    label = PersianNames.WEEKDAYS_SHORT[offset.toInt()],
                    dayOfMonth = PersianDigits.format(day.toJalali().day),
                    isToday = day == today,
                    isWeekend = day.dayOfWeek == DayOfWeek.FRIDAY,
                )
            }
            return TodayUiState(
                weekday = PersianNames.weekday(today.dayOfWeek),
                date = PersianDateFormatter.dayMonthYear(jalali),
                secondaryDates = secondary,
                week = week,
            )
        }
    }
}
