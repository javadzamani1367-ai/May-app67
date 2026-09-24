package ir.roozban.feature.tasks

import ir.roozban.core.domain.Highlight
import ir.roozban.core.model.Quadrant
import ir.roozban.core.model.ReminderKind

enum class ListMode { TODAY, UPCOMING, INBOX }

data class TaskListUiState(
    val mode: ListMode,
    val loading: Boolean = true,
    /** Only for [ListMode.TODAY]. */
    val header: TodayHeader? = null,
    val sections: List<TaskSection> = emptyList(),
    val completed: List<TaskItem> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && sections.all { it.tasks.isEmpty() } && completed.isEmpty()
}

data class TodayHeader(
    /** «پنجشنبه» */
    val weekday: String,
    /** «۲ مهر ۱۴۰۵» */
    val date: String,
    /** «۲۴ سپتامبر ۲۰۲۶ · ۱۳ ربیع‌الثانی ۱۴۴۸» */
    val secondaryDates: String,
    val week: List<WeekDay>,
)

data class WeekDay(val label: String, val dayOfMonth: String, val isToday: Boolean, val isWeekend: Boolean)

enum class SectionKind { OVERDUE, TODAY, DAY, MONTH, NO_DATE }

data class TaskSection(val kind: SectionKind, val title: String?, val tasks: List<TaskItem>)

data class TaskItem(
    val id: String,
    val title: String,
    val dueLabel: String?,
    val overdue: Boolean,
    val recurrenceLabel: String?,
    val estimateLabel: String?,
    val quadrant: Quadrant,
    val reminder: ReminderKind?,
    val completed: Boolean,
)

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

enum class ChipKind { TIME, RECURRENCE, DURATION, PRIORITY }

data class PreviewChip(val kind: ChipKind, val label: String)
