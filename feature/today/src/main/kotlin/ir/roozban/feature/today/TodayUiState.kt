package ir.roozban.feature.today

import ir.roozban.core.timeparser.SpanKind

data class TodayUiState(
    /** «پنجشنبه» */
    val weekday: String,
    /** «۲ مهر ۱۴۰۵» */
    val date: String,
    /** «۲۴ سپتامبر ۲۰۲۶ · ۱۳ ربیع‌الثانی ۱۴۴۸» */
    val secondaryDates: String,
    val week: List<WeekDay>,
    /** Tasks captured in this session. Persistence arrives in phase 1b. */
    val sessionTasks: List<DraftTask> = emptyList(),
)

data class WeekDay(val label: String, val dayOfMonth: String, val isToday: Boolean, val isWeekend: Boolean)

data class DraftTask(val id: Long, val title: String, val whenLabel: String?, val recurrenceLabel: String?, val estimateLabel: String?)

data class QuickAddState(
    val text: String = "",
    val preview: QuickAddPreview? = null,
)

data class QuickAddPreview(
    val title: String,
    val chips: List<PreviewChip>,
    val highlights: List<Highlight>,
    /** The parser was unsure; the user should double-check the time. */
    val needsReview: Boolean,
)

enum class ChipKind { TIME, RECURRENCE, DURATION }

data class PreviewChip(val kind: ChipKind, val label: String)

data class Highlight(val start: Int, val end: Int, val kind: SpanKind)
