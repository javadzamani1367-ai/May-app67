package ir.roozban.ai.tools

import ir.roozban.core.model.Habit
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Worked examples shown to the model before every message, one per tool and common case. They
 * live in one small example world; the state and guide lines are rendered by the same code as
 * the real prompt, so the examples always match the live format.
 */
internal object Examples {
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 4, 9, 15)
    private val at = Instant.parse("2026-11-01T00:00:00Z")

    private fun task(n: Int, title: String, daysFromToday: Long?, important: Boolean = false) =
        Task("x$n", title, due = daysFromToday?.let { TaskDue.AllDay(now.toLocalDate().plusDays(it)) }, important = important, createdAt = at, updatedAt = at)

    private fun habit(n: Int, name: String, perDay: Int = 1) =
        Habit("h$n", name, targetPerDay = perDay, startDate = LocalDate.of(2026, 10, 1), createdAt = at, updatedAt = at)

    val first = AssistantContext(
        now,
        listOf(task(1, "ارسال فاکتور", 0), task(2, "تعمیر دوچرخه", 1), task(3, "کلاس گیتار", 0)),
        listOf(habit(1, "پیاده‌روی"), habit(2, "مدیتیشن"), habit(3, "خوردن میوه", 3)),
    )


    /** (world, message, answer); the first example of each world shows its state. */
    val all: List<Triple<AssistantContext, String, String>> = listOf(
        Triple(first, "پنجشنبه ساعت ۸ شب تولد نیما", "{\"actions\":[{\"tool\":\"create_task\",\"args\":{\"title\":\"تولد نیما\",\"when\":\"پنجشنبه ساعت ۸ شب\",\"repeat\":null,\"important\":false,\"urgent\":false,\"minutes\":null,\"project\":null}}],\"reply\":\"تولد نیما برای پنجشنبه ساعت ۸ شب ثبت شد.\"}"),
        Triple(first, "باید کتابخونه رو پس بدم", "{\"actions\":[{\"tool\":\"create_task\",\"args\":{\"title\":\"پس دادن کتاب کتابخانه\",\"when\":null,\"repeat\":null,\"important\":false,\"urgent\":false,\"minutes\":null,\"project\":null}}],\"reply\":\"پس دادن کتاب به فهرست کارهایت اضافه شد.\"}"),
        Triple(first, "فاکتور رو فرستادم", "{\"actions\":[{\"tool\":\"complete_task\",\"args\":{\"task\":\"#1\"}}],\"reply\":\"عالی، ارسال فاکتور تیک خورد.\"}"),
        Triple(first, "کلاس گیتار رو بنداز ساعت ۶", "{\"actions\":[{\"tool\":\"reschedule\",\"args\":{\"task\":\"#3\",\"when\":\"ساعت ۶\"}}],\"reply\":\"کلاس گیتار رفت برای ساعت ۶.\"}"),
        Triple(first, "ارسال فاکتور رو مهم کن", "{\"actions\":[{\"tool\":\"update_task\",\"args\":{\"task\":\"#1\",\"title\":null,\"important\":true,\"urgent\":null,\"notes\":null}}],\"reply\":\"ارسال فاکتور مهم شد.\"}"),
        Triple(first, "امروز چه کارهایی دارم؟", "{\"actions\":[{\"tool\":\"list_tasks\",\"args\":{\"range\":\"today\"}}],\"reply\":\"این‌ها کارهای امروزت هستند.\"}"),
        Triple(first, "امروز پیاده‌روی رفتم", "{\"actions\":[{\"tool\":\"log_habit\",\"args\":{\"habit\":\"پیاده‌روی\",\"count\":null}}],\"reply\":\"آفرین! پیاده‌روی امروز ثبت شد.\"}"),
        Triple(first, "می‌خوام هر روز ساعت ۱۰ شب کتاب بخونم، برام عادتش کن", "{\"actions\":[{\"tool\":\"create_habit\",\"args\":{\"name\":\"کتاب خواندن\",\"per_week\":null,\"per_day\":1,\"reminder\":\"ساعت ۱۰ شب\"}}],\"reply\":\"عادت کتاب خواندن با یادآوری ساعت ۱۰ شب ساخته شد.\"}"),
        Triple(first, "دو تا کار بنویس: تعویض روغن ماشین و خرید لامپ", "{\"actions\":[{\"tool\":\"create_task\",\"args\":{\"title\":\"تعویض روغن ماشین\",\"when\":null,\"repeat\":null,\"important\":false,\"urgent\":false,\"minutes\":null,\"project\":null}},{\"tool\":\"create_task\",\"args\":{\"title\":\"خرید لامپ\",\"when\":null,\"repeat\":null,\"important\":false,\"urgent\":false,\"minutes\":null,\"project\":null}}],\"reply\":\"دو کار تعویض روغن ماشین و خرید لامپ اضافه شد.\"}"),
        Triple(first, "کلاس گیتار رو رفتم، فردا ساعت ۹ هم یادم بنداز به بیمه زنگ بزنم", "{\"actions\":[{\"tool\":\"complete_task\",\"args\":{\"task\":\"#3\"}},{\"tool\":\"create_task\",\"args\":{\"title\":\"زنگ به بیمه\",\"when\":\"فردا ساعت ۹\",\"repeat\":null,\"important\":false,\"urgent\":false,\"minutes\":null,\"project\":null}}],\"reply\":\"کلاس گیتار تیک خورد و تماس با بیمه برای فردا ساعت ۹ ثبت شد.\"}"),
        Triple(first, "یه کار خیلی فوری و مهم: جواب ایمیل مدیر تا ظهر", "{\"actions\":[{\"tool\":\"create_task\",\"args\":{\"title\":\"جواب ایمیل مدیر\",\"when\":\"تا ظهر\",\"repeat\":null,\"important\":true,\"urgent\":true,\"minutes\":null,\"project\":null}}],\"reply\":\"جواب ایمیل مدیر با اولویت بالا تا ظهر ثبت شد.\"}"),
        Triple(first, "سلام، خسته نباشی", "{\"actions\":[],\"reply\":\"سلام! ممنون. بگو چه کاری برات انجام بدم.\"}"),
    )
}
