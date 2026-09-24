package ir.roozban.core.timeparser

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.toJalali
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.time.Duration
import java.time.LocalDateTime

/**
 * Table-driven tests. Reference moment unless stated otherwise:
 * پنجشنبه ۲ مهر ۱۴۰۵، ساعت ۱۰:۰۰ (Thursday 2026-09-24 10:00).
 *
 * Expected values are Jalali: `yyyy-MM-dd HH:mm` for a moment, `yyyy-MM-dd` for an all-day date,
 * `-` for no time.
 */
class PersianTimeParserTest {

    private val parser = PersianTimeParser()

    private fun at(jalali: String, hour: Int = 10, minute: Int = 0): LocalDateTime =
        JalaliDate.parse(jalali).toLocalDate().atTime(hour, minute)

    private val thursday = at("1405-07-02")
    private val sunday = at("1405-07-05")

    private fun render(t: ResolvedTime?): String = when (t) {
        null -> "-"
        is ResolvedTime.AllDay -> t.date.toJalali().toString()
        is ResolvedTime.At -> "${t.dateTime.toLocalDate().toJalali()} %02d:%02d".format(t.dateTime.hour, t.dateTime.minute)
    }

    private fun cases(now: LocalDateTime, vararg table: Pair<String, String>): List<DynamicTest> =
        table.map { (input, expected) ->
            DynamicTest.dynamicTest("$input → $expected") {
                val result = parser.parse(input, now)
                assertWithMessage("input «$input»").that(render(result.time)).isEqualTo(expected)
            }
        }

    // ------------------------------------------------------------------ days and parts of day

    @TestFactory
    fun `relative days and parts of day`() = cases(
        thursday,
        "امروز" to "1405-07-02",
        "فردا" to "1405-07-03",
        "پس‌فردا" to "1405-07-04",
        "پسفردا" to "1405-07-04",
        "پس فردا" to "1405-07-04",
        "پس پس فردا" to "1405-07-05",
        "دیروز" to "1405-07-01",
        "فردا صبح" to "1405-07-03 09:00",
        "صبح فردا" to "1405-07-03 09:00",
        "فردا ظهر" to "1405-07-03 12:00",
        "پس‌فردا عصر" to "1405-07-04 17:00",
        "فردا بعد از ظهر" to "1405-07-03 15:00",
        "فردا بعدازظهر" to "1405-07-03 15:00",
        "فردا غروب" to "1405-07-03 18:00",
        "فردا شب" to "1405-07-03 20:00",
        "امشب" to "1405-07-02 20:00",
        "امشب ساعت ۱۱" to "1405-07-02 23:00",
        "امشب ساعت ۲" to "1405-07-03 02:00",
        "فردا نیمه‌شب" to "1405-07-04 00:00",
        "فردا صبح زود" to "1405-07-03 07:00",
        "اول صبح فردا" to "1405-07-03 07:00",
        "عصر" to "1405-07-02 17:00",
        "شب" to "1405-07-02 20:00",
        "صبح" to "1405-07-03 09:00",
        "ظهر" to "1405-07-02 12:00",
    )

    // ------------------------------------------------------------------ clock times

    @TestFactory
    fun `clock times`() = cases(
        thursday,
        "ساعت ۵" to "1405-07-02 17:00",
        "ساعت ۱۱" to "1405-07-02 11:00",
        "ساعت ۸" to "1405-07-02 20:00",
        "ساعت ۹" to "1405-07-02 21:00",
        "ساعت ۱۲" to "1405-07-02 12:00",
        "ساعت ۲۳" to "1405-07-02 23:00",
        "ساعت ۰" to "1405-07-03 00:00",
        "ساعت ۱۷:۳۰" to "1405-07-02 17:30",
        "۱۷:۳۰" to "1405-07-02 17:30",
        "ساعت 17:30" to "1405-07-02 17:30",
        "ساعت ۵ و نیم" to "1405-07-02 17:30",
        "ساعت ۵ و ربع" to "1405-07-02 17:15",
        "ساعت ۵ و ۲۰ دقیقه" to "1405-07-02 17:20",
        "ساعت پنج و نیم عصر" to "1405-07-02 17:30",
        "یه ربع به ۶" to "1405-07-02 17:45",
        "ساعت یه ربع به ۶" to "1405-07-02 17:45",
        "ده دقیقه به ۸ شب" to "1405-07-02 19:50",
        "یه ربع به یک" to "1405-07-02 12:45",
        "ساعت ۷ صبح" to "1405-07-03 07:00",
        "ساعت ۶ صبح" to "1405-07-03 06:00",
        "ساعت ۵ و نیم صبح" to "1405-07-03 05:30",
        "۸ صبح" to "1405-07-03 08:00",
        "۵ عصر" to "1405-07-02 17:00",
        "ساعت ۱۰ شب" to "1405-07-02 22:00",
        "فردا ساعت ۵" to "1405-07-03 17:00",
        "فردا ساعت ۸" to "1405-07-03 08:00",
        "فردا ساعت ۱۰" to "1405-07-03 10:00",
        "فردا ساعت ۱۷" to "1405-07-03 17:00",
        "فردا ۵ عصر" to "1405-07-03 17:00",
        "فردا ساعت ۱۲ ظهر" to "1405-07-03 12:00",
        "فردا ۱ ظهر" to "1405-07-03 13:00",
        "فردا ساعت ۱۲ شب" to "1405-07-04 00:00",
        "فردا ده و نیم" to "1405-07-03 10:30",
        "ساعت ۵ فردا" to "1405-07-03 17:00",
        "فردا، ساعت ۵" to "1405-07-03 17:00",
        "۵ عصر فردا" to "1405-07-03 17:00",
        "فردا ساعت 5" to "1405-07-03 17:00",
        "ساعت ٥" to "1405-07-02 17:00",
    )

    @Test
    fun `late evening rolls vague times to tomorrow`() {
        val late = at("1405-07-02", 23)
        assertThat(render(parser.parse("ساعت ۸", late).time)).isEqualTo("1405-07-03 08:00")
        assertThat(render(parser.parse("صبح", late).time)).isEqualTo("1405-07-03 09:00")
        assertThat(render(parser.parse("عصر", late).time)).isEqualTo("1405-07-03 17:00")
        assertThat(render(parser.parse("ساعت ۱۱ شب", late).time)).isEqualTo("1405-07-03 23:00")
    }

    // ------------------------------------------------------------------ relative offsets

    @TestFactory
    fun `relative offsets`() = cases(
        thursday,
        "دو ساعت دیگه" to "1405-07-02 12:00",
        "۲ ساعت دیگر" to "1405-07-02 12:00",
        "یک ساعت دیگه" to "1405-07-02 11:00",
        "نیم ساعت دیگه" to "1405-07-02 10:30",
        "یک ساعت و نیم دیگه" to "1405-07-02 11:30",
        "یک و نیم ساعت دیگه" to "1405-07-02 11:30",
        "یه ربع دیگه" to "1405-07-02 10:15",
        "ربع ساعت دیگه" to "1405-07-02 10:15",
        "سه ربع دیگه" to "1405-07-02 10:45",
        "۱۰ دقیقه دیگه" to "1405-07-02 10:10",
        "۴۵ دقیقه دیگه" to "1405-07-02 10:45",
        "بعد از ۲ ساعت" to "1405-07-02 12:00",
        "پس از دو ساعت" to "1405-07-02 12:00",
        "تا ۳ ساعت دیگه" to "1405-07-02 13:00",
        "دو ساعت و ۳۰ دقیقه دیگه" to "1405-07-02 12:30",
        "سه روز دیگه" to "1405-07-05",
        "۳ روز بعد" to "1405-07-05",
        "یک هفته دیگه" to "1405-07-09",
        "دو هفته بعد" to "1405-07-16",
        "یک ماه دیگه" to "1405-08-02",
        "یک سال دیگه" to "1406-07-02",
        "دو روز دیگه ساعت ۵" to "1405-07-04 17:00",
    )

    @Test
    fun `offsets drop seconds`() {
        val now = at("1405-07-02").withSecond(45)
        assertThat(render(parser.parse("۱۰ دقیقه دیگه", now).time)).isEqualTo("1405-07-02 10:10")
    }

    @Test
    fun `month offset clamps to month length`() {
        // ۳۱ شهریور + یک ماه = ۳۰ مهر
        assertThat(render(parser.parse("یک ماه دیگه", at("1405-06-31")).time)).isEqualTo("1405-07-30")
    }

    // ------------------------------------------------------------------ weekdays

    @TestFactory
    fun `weekdays from thursday`() = cases(
        thursday,
        "شنبه" to "1405-07-04",
        "یکشنبه" to "1405-07-05",
        "یک‌شنبه" to "1405-07-05",
        "دوشنبه" to "1405-07-06",
        "سه‌شنبه" to "1405-07-07",
        "سه شنبه" to "1405-07-07",
        "چهارشنبه" to "1405-07-08",
        "جمعه" to "1405-07-03",
        "پنجشنبه" to "1405-07-09",
        "پنج‌شنبه ساعت ۸ شب" to "1405-07-02 20:00",
        "پنجشنبه ساعت ۸ صبح" to "1405-07-09 08:00",
        "شنبه‌ی بعد" to "1405-07-04",
        "شنبه‌ی بعد ساعت ۵" to "1405-07-04 17:00",
        "شنبه ساعت ۵" to "1405-07-04 17:00",
        "شنبه صبح" to "1405-07-04 09:00",
        "صبح شنبه" to "1405-07-04 09:00",
        "دوشنبه ساعت ۹" to "1405-07-06 09:00",
        "جمعه‌ی این هفته" to "1405-07-03",
        "همین شنبه" to "1405-07-04",
        "این شنبه" to "1405-07-04",
        "روز شنبه" to "1405-07-04",
    )

    @TestFactory
    fun `next week versus nearest from sunday`() = cases(
        sunday,
        "سه‌شنبه" to "1405-07-07",
        "سه‌شنبه‌ی بعد" to "1405-07-14",
        "سه‌شنبه‌ی آینده" to "1405-07-14",
        "سه‌شنبه دیگه" to "1405-07-14",
        "سه‌شنبه هفته بعد" to "1405-07-14",
        "سه شنبه‌ی هفته‌ی آینده" to "1405-07-14",
        "هفته‌ی بعد سه‌شنبه" to "1405-07-14",
        "هفته‌ی بعد سه‌شنبه ساعت ۴ بعد از ظهر" to "1405-07-14 16:00",
        "شنبه" to "1405-07-11",
        "شنبه‌ی بعد" to "1405-07-11",
    )

    @Test
    fun `a past day of this week has low confidence`() {
        val r = parser.parse("دوشنبه‌ی این هفته", thursday)
        assertThat(render(r.time)).isEqualTo("1405-06-30")
        assertThat(r.confidence).isAtMost(0.5f)
    }

    // ------------------------------------------------------------------ period anchors

    @TestFactory
    fun `week month and year anchors`() = cases(
        thursday,
        "آخر هفته" to "1405-07-03",
        "آخر هفته‌ی بعد" to "1405-07-10",
        "این هفته" to "1405-07-03",
        "هفته بعد" to "1405-07-04",
        "هفته‌ی آینده" to "1405-07-04",
        "اول هفته‌ی بعد" to "1405-07-04",
        "هفته بعد ساعت ۵" to "1405-07-04 17:00",
        "آخر ماه" to "1405-07-30",
        "آخر این ماه" to "1405-07-30",
        "آخر ماه بعد" to "1405-08-30",
        "آخر ماه آینده" to "1405-08-30",
        "اول ماه" to "1405-08-01",
        "اول ماه بعد" to "1405-08-01",
        "ماه بعد" to "1405-08-01",
        "ماه آینده" to "1405-08-01",
        "وسط ماه" to "1405-07-15",
        "نیمه‌ی ماه" to "1405-07-15",
        "دهم ماه" to "1405-07-10",
        "دهم ماه بعد" to "1405-08-10",
        "آخر سال" to "1405-12-29",
        "سال بعد" to "1406-01-01",
        "سال دیگه" to "1406-01-01",
        "اول سال" to "1406-01-01",
    )

    @Test
    fun `month anchors at the end of the year`() {
        val lastDay = at("1405-12-29")
        assertThat(render(parser.parse("فردا", lastDay).time)).isEqualTo("1406-01-01")
        assertThat(render(parser.parse("آخر ماه", lastDay).time)).isEqualTo("1405-12-29")
        assertThat(render(parser.parse("ماه بعد", lastDay).time)).isEqualTo("1406-01-01")
        assertThat(render(parser.parse("آخر سال", lastDay).time)).isEqualTo("1405-12-29")
    }

    @Test
    fun `end of esfand in a leap year`() {
        // ۱۴۰۳ is a leap year: Esfand has 30 days.
        assertThat(render(parser.parse("آخر ماه", at("1403-12-01")).time)).isEqualTo("1403-12-30")
        assertThat(render(parser.parse("۳۰ اسفند", at("1403-11-01")).time)).isEqualTo("1403-12-30")
    }

    // ------------------------------------------------------------------ explicit dates

    @TestFactory
    fun `explicit dates`() = cases(
        thursday,
        "۱۵ آبان" to "1405-08-15",
        "پانزدهم آبان" to "1405-08-15",
        "۱۵ام آبان" to "1405-08-15",
        "۱۵ اردیبهشت" to "1406-02-15",
        "اول دی" to "1405-10-01",
        "پنجم فروردین" to "1406-01-05",
        "بیست و پنجم آذر" to "1405-09-25",
        "آخر اسفند" to "1405-12-29",
        "آخر آبان" to "1405-08-30",
        "اواخر آذر" to "1405-09-30",
        "وسط آذر" to "1405-09-15",
        "۲۰ مهر ۱۴۰۶" to "1406-07-20",
        "۲۵ اسفند ۱۴۰۵" to "1405-12-25",
        "1405/8/1" to "1405-08-01",
        "۱۴۰۵/۰۸/۰۱" to "1405-08-01",
        "8/15" to "1405-08-15",
        "۱ مهر" to "1406-07-01",
        "۲ مهر" to "1405-07-02",
        "۱۵ آبان ساعت ۱۰" to "1405-08-15 10:00",
        "۱۵ آبان ساعت ۵ عصر" to "1405-08-15 17:00",
        // Invalid days are rejected rather than silently shifted.
        "۳۱ مهر" to "-",
        "۳۰ اسفند" to "-",
    )

    // ------------------------------------------------------------------ recurrence

    private fun recurrenceCases(vararg table: Triple<String, String, String>): List<DynamicTest> =
        table.map { (input, rrule, first) ->
            DynamicTest.dynamicTest("$input → $rrule @ $first") {
                val r = parser.parse(input, thursday)
                assertWithMessage("rrule for «$input»").that(r.recurrence?.toRRule()).isEqualTo(rrule)
                assertWithMessage("first occurrence for «$input»").that(render(r.time)).isEqualTo(first)
                assertThat(r.spans.single().kind).isEqualTo(SpanKind.RECURRENCE)
            }
        }

    @TestFactory
    fun recurrence() = recurrenceCases(
        Triple("هر روز", "FREQ=DAILY", "1405-07-02"),
        Triple("روزانه", "FREQ=DAILY", "1405-07-02"),
        Triple("هر روز ساعت ۸ صبح", "FREQ=DAILY", "1405-07-03 08:00"),
        Triple("هر روز ساعت ۸", "FREQ=DAILY", "1405-07-03 08:00"),
        Triple("هر روز صبح", "FREQ=DAILY", "1405-07-03 09:00"),
        Triple("صبح‌ها", "FREQ=DAILY", "1405-07-03 09:00"),
        Triple("هر شب ساعت ۱۰", "FREQ=DAILY", "1405-07-02 22:00"),
        Triple("یک روز در میان", "FREQ=DAILY;INTERVAL=2", "1405-07-02"),
        Triple("یه روز در میون", "FREQ=DAILY;INTERVAL=2", "1405-07-02"),
        Triple("هر دو روز", "FREQ=DAILY;INTERVAL=2", "1405-07-02"),
        Triple("هر شنبه", "FREQ=WEEKLY;BYDAY=SA", "1405-07-04"),
        Triple("هر دوشنبه", "FREQ=WEEKLY;BYDAY=MO", "1405-07-06"),
        Triple("شنبه‌ها", "FREQ=WEEKLY;BYDAY=SA", "1405-07-04"),
        Triple("هر شنبه ساعت ۸", "FREQ=WEEKLY;BYDAY=SA", "1405-07-04 08:00"),
        Triple("هر شنبه و دوشنبه", "FREQ=WEEKLY;BYDAY=SA,MO", "1405-07-04"),
        Triple("شنبه‌ها و دوشنبه‌ها", "FREQ=WEEKLY;BYDAY=SA,MO", "1405-07-04"),
        Triple("هر هفته شنبه", "FREQ=WEEKLY;BYDAY=SA", "1405-07-04"),
        Triple("هر هفته", "FREQ=WEEKLY", "1405-07-02"),
        Triple("هفتگی", "FREQ=WEEKLY", "1405-07-02"),
        Triple("هر ۳ هفته", "FREQ=WEEKLY;INTERVAL=3", "1405-07-02"),
        Triple("روزهای زوج", "FREQ=WEEKLY;BYDAY=SA,MO,WE", "1405-07-04"),
        Triple("روزای زوج ساعت ۶ عصر", "FREQ=WEEKLY;BYDAY=SA,MO,WE", "1405-07-04 18:00"),
        Triple("روزهای فرد", "FREQ=WEEKLY;BYDAY=SU,TU,TH", "1405-07-02"),
        Triple("هر روز کاری", "FREQ=WEEKLY;BYDAY=SA,SU,MO,TU,WE", "1405-07-04"),
        Triple("هر ماه", "RSCALE=PERSIAN;FREQ=MONTHLY", "1405-07-02"),
        Triple("ماهانه", "RSCALE=PERSIAN;FREQ=MONTHLY", "1405-07-02"),
        Triple("آخر هر ماه", "RSCALE=PERSIAN;FREQ=MONTHLY;BYMONTHDAY=-1", "1405-07-30"),
        Triple("اول هر ماه", "RSCALE=PERSIAN;FREQ=MONTHLY;BYMONTHDAY=1", "1405-08-01"),
        Triple("پانزدهم هر ماه", "RSCALE=PERSIAN;FREQ=MONTHLY;BYMONTHDAY=15", "1405-07-15"),
        Triple("هر سال", "RSCALE=PERSIAN;FREQ=YEARLY", "1405-07-02"),
    )

    // ------------------------------------------------------------------ titles, spans, estimates

    private fun title(input: String) = parser.parse(input, thursday).title

    @Test
    fun `time expressions are removed from the title`() {
        assertThat(title("خرید نان فردا عصر")).isEqualTo("خرید نان")
        assertThat(title("جلسه با علی پس‌فردا ساعت ۱۰")).isEqualTo("جلسه با علی")
        assertThat(title("تماس با مادر تا فردا")).isEqualTo("تماس با مادر")
        assertThat(title("ارسال ایمیل ساعت ۱۷:۳۰ امروز")).isEqualTo("ارسال ایمیل")
        assertThat(title("خرید، فردا")).isEqualTo("خرید")
        assertThat(title("نماز ظهر")).isEqualTo("نماز")
        assertThat(title("روز شنبه")).isEqualTo("")
        assertThat(title("ورزش هر روز صبح")).isEqualTo("ورزش")
    }

    @Test
    fun `dangling ezafe before a removed time is dropped, other ezafes are kept`() {
        assertThat(title("جلسه‌ی فردا")).isEqualTo("جلسه")
        assertThat(title("کلید خانه‌ی علی فردا")).isEqualTo("کلید خانه‌ی علی")
    }

    @Test
    fun `span covers the expression in the original text`() {
        val input = "خرید نان فردا عصر"
        val span = parser.parse(input, thursday).spans.single()
        assertThat(span.kind).isEqualTo(SpanKind.TIME)
        assertThat(input.substring(span.start, span.end)).isEqualTo("فردا عصر")
    }

    @Test
    fun `span offsets survive diacritics, tatweel and Arabic letters`() {
        val input = "کار فـردا ساعتِ ۵ عصري"
        val r = parser.parse(input, thursday)
        assertThat(render(r.time)).isEqualTo("1405-07-03 17:00")
        assertThat(input.substring(r.spans.single().start, r.spans.single().end)).isEqualTo("فـردا ساعتِ ۵ عصري")
        assertThat(r.title).isEqualTo("کار")
    }

    @Test
    fun `durations become estimates`() {
        parser.parse("ورزش ۳۰ دقیقه", thursday).let {
            assertThat(it.estimate).isEqualTo(Duration.ofMinutes(30))
            assertThat(it.time).isNull()
            assertThat(it.title).isEqualTo("ورزش")
            assertThat(it.spans.single().kind).isEqualTo(SpanKind.DURATION)
        }
        parser.parse("مطالعه یک ساعت و نیم فردا صبح", thursday).let {
            assertThat(it.estimate).isEqualTo(Duration.ofMinutes(90))
            assertThat(render(it.time)).isEqualTo("1405-07-03 09:00")
            assertThat(it.title).isEqualTo("مطالعه")
        }
        parser.parse("گزارش ۲ ساعت شنبه", thursday).let {
            assertThat(it.estimate).isEqualTo(Duration.ofHours(2))
            assertThat(render(it.time)).isEqualTo("1405-07-04")
            assertThat(it.title).isEqualTo("گزارش")
        }
        assertThat(parser.parse("نیم ساعت", thursday).estimate).isEqualTo(Duration.ofMinutes(30))
    }

    // ------------------------------------------------------------------ false positives

    @Nested
    inner class BareNumbers {
        @Test
        fun `a number in the title is not a time`() {
            val r = parser.parse("خرید ۳ نان فردا", thursday)
            assertThat(render(r.time)).isEqualTo("1405-07-03")
            assertThat(r.title).isEqualTo("خرید ۳ نان")
        }

        @Test
        fun `a number after a date followed by more words is not a time`() {
            val r = parser.parse("فردا ۳ نان بخر", thursday)
            assertThat(render(r.time)).isEqualTo("1405-07-03")
            assertThat(r.title).isEqualTo("۳ نان بخر")
        }

        @Test
        fun `a number ending the text after a date is a time`() {
            val r = parser.parse("جلسه فردا ۵", thursday)
            assertThat(render(r.time)).isEqualTo("1405-07-03 17:00")
            assertThat(r.title).isEqualTo("جلسه")
        }

        @Test
        fun `text without time`() {
            listOf("کتاب بخوان", "۵ کیلو سیب", "ساعت", "جلسه ده و نیم", "").forEach {
                val r = parser.parse(it, thursday)
                assertWithMessage(it).that(r.time).isNull()
                assertWithMessage(it).that(r.confidence).isEqualTo(0f)
                assertWithMessage(it).that(r.title).isEqualTo(it)
            }
        }
    }

    // ------------------------------------------------------------------ confidence

    @Test
    fun confidence() {
        assertThat(parser.parse("فردا ساعت ۱۷", thursday).confidence).isEqualTo(1f)
        assertThat(parser.parse("فردا عصر", thursday).confidence).isEqualTo(1f)
        // 08:00 or 20:00: a guess.
        assertThat(parser.parse("فردا ساعت ۸", thursday).confidence).isLessThan(1f)
        // Past dates are suspicious.
        assertThat(parser.parse("دیروز", thursday).confidence).isAtMost(0.5f)
    }

    @Test
    fun `preferences change defaults`() {
        val custom = PersianTimeParser(TimeParserPrefs(eveningHour = 16, morningHour = 8))
        assertThat(render(custom.parse("فردا عصر", thursday).time)).isEqualTo("1405-07-03 16:00")
        assertThat(render(custom.parse("فردا صبح", thursday).time)).isEqualTo("1405-07-03 08:00")
    }
}
