package ir.roozban.feature.today

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.timeparser.PersianTimeParser
import ir.roozban.core.timeparser.SpanKind
import org.junit.Test
import java.time.Clock
import java.time.ZoneId

class TodayViewModelTest {

    private val tehran = ZoneId.of("Asia/Tehran")

    /** پنجشنبه ۲ مهر ۱۴۰۵، ساعت ۱۰ */
    private val clock = Clock.fixed(
        JalaliDate.of(1405, 7, 2).toLocalDate().atTime(10, 0).atZone(tehran).toInstant(),
        tehran,
    )

    private fun viewModel() = TodayViewModel(clock, PersianTimeParser())

    @Test
    fun `header shows the Jalali date with secondary calendars`() {
        val state = viewModel().state.value
        assertThat(state.weekday).isEqualTo("پنجشنبه")
        assertThat(state.date).isEqualTo("۲ مهر ۱۴۰۵")
        assertThat(state.secondaryDates).isEqualTo("۲۴ سپتامبر ۲۰۲۶ · ۱۳ ربیع‌الثانی ۱۴۴۸")
    }

    @Test
    fun `week strip starts on Saturday and marks today`() {
        val week = viewModel().state.value.week
        assertThat(week.map { it.label }).containsExactly("ش", "ی", "د", "س", "چ", "پ", "ج").inOrder()
        assertThat(week.map { it.dayOfMonth }).containsExactly("۲۸", "۲۹", "۳۰", "۳۱", "۱", "۲", "۳").inOrder()
        assertThat(week.single { it.isToday }.dayOfMonth).isEqualTo("۲")
        assertThat(week.last().isWeekend).isTrue()
    }

    @Test
    fun `typing shows a live preview`() {
        val vm = viewModel()
        vm.onQuickAddTextChange("خرید نان فردا عصر ۳۰ دقیقه")
        val preview = vm.quickAdd.value.preview!!
        assertThat(preview.title).isEqualTo("خرید نان")
        assertThat(preview.chips).containsExactly(
            PreviewChip(ChipKind.TIME, "فردا، ۱۷:۰۰"),
            PreviewChip(ChipKind.DURATION, "۳۰ دقیقه"),
        ).inOrder()
        assertThat(preview.highlights.map { it.kind }).containsExactly(SpanKind.TIME, SpanKind.DURATION).inOrder()
        assertThat(preview.needsReview).isFalse()
    }

    @Test
    fun `recurrence appears as its own chip`() {
        val vm = viewModel()
        vm.onQuickAddTextChange("ورزش هر شنبه و دوشنبه ساعت ۷ صبح")
        val chips = vm.quickAdd.value.preview!!.chips
        assertThat(chips).containsExactly(
            PreviewChip(ChipKind.TIME, "پس‌فردا، ۰۷:۰۰"),
            PreviewChip(ChipKind.RECURRENCE, "هر شنبه، دوشنبه"),
        ).inOrder()
    }

    @Test
    fun `submit adds the task and resets the sheet`() {
        val vm = viewModel()
        vm.onQuickAddTextChange("جلسه با علی پس‌فردا ساعت ۱۰")
        assertThat(vm.submitQuickAdd()).isTrue()
        val task = vm.state.value.sessionTasks.single()
        assertThat(task.title).isEqualTo("جلسه با علی")
        assertThat(task.whenLabel).isEqualTo("پس‌فردا، ۱۰:۰۰")
        assertThat(vm.quickAdd.value).isEqualTo(QuickAddState())
    }

    @Test
    fun `blank input or a time without a title is not submitted`() {
        val vm = viewModel()
        assertThat(vm.submitQuickAdd()).isFalse()
        vm.onQuickAddTextChange("فردا عصر")
        assertThat(vm.submitQuickAdd()).isFalse()
        assertThat(vm.state.value.sessionTasks).isEmpty()
    }

    @Test
    fun `past dates ask for review`() {
        val vm = viewModel()
        vm.onQuickAddTextChange("گزارش دیروز")
        assertThat(vm.quickAdd.value.preview!!.needsReview).isTrue()
    }
}
