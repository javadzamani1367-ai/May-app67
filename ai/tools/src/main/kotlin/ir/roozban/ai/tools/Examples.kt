package ir.roozban.ai.tools

/** Worked examples shown to the model before every message; one per tool and common case. */
internal object Examples {
    val all = listOf(
        Example(
            "امروز: سه‌شنبه ۱۴ آبان، ساعت ۰۹:۱۵\nکارها:\n#1 ارسال فاکتور — امروز ۱۱:۰۰\n#2 تعمیر دوچرخه — فردا\n#3 کلاس گیتار — امروز ۱۷:۰۰\nعادت‌ها: پیاده‌روی، مدیتیشن، خوردن میوه (۳ بار در روز)\nپیام: پنجشنبه ساعت ۸ شب تولد نیما",
            "{\"actions\":[{\"tool\":\"create_task\",\"args\":{\"title\":\"تولد نیما\",\"when\":\"پنجشنبه ساعت ۸ شب\",\"repeat\":null,\"important\":false,\"urgent\":false,\"minutes\":null,\"project\":null}}],\"reply\":\"تولد نیما برای پنجشنبه ساعت ۸ شب ثبت شد.\"}",
        ),
        Example(
            "پیام: باید کتابخونه رو پس بدم",
            "{\"actions\":[{\"tool\":\"create_task\",\"args\":{\"title\":\"پس دادن کتاب کتابخانه\",\"when\":null,\"repeat\":null,\"important\":false,\"urgent\":false,\"minutes\":null,\"project\":null}}],\"reply\":\"پس دادن کتاب به فهرست کارهایت اضافه شد.\"}",
        ),
        Example(
            "پیام: فاکتور رو فرستادم",
            "{\"actions\":[{\"tool\":\"complete_task\",\"args\":{\"task\":\"#1\"}}],\"reply\":\"عالی، ارسال فاکتور تیک خورد.\"}",
        ),
        Example(
            "پیام: کلاس گیتار رو بنداز ساعت ۶",
            "{\"actions\":[{\"tool\":\"reschedule\",\"args\":{\"task\":\"#3\",\"when\":\"ساعت ۶\"}}],\"reply\":\"کلاس گیتار رفت برای ساعت ۶.\"}",
        ),
        Example(
            "پیام: تعمیر دوچرخه رو پاک کن",
            "{\"actions\":[{\"tool\":\"delete_task\",\"args\":{\"task\":\"#2\"}}],\"reply\":\"تعمیر دوچرخه حذف شود؟\"}",
        ),
        Example(
            "پیام: ارسال فاکتور رو مهم کن",
            "{\"actions\":[{\"tool\":\"update_task\",\"args\":{\"task\":\"#1\",\"title\":null,\"important\":true,\"urgent\":null,\"notes\":null}}],\"reply\":\"ارسال فاکتور مهم شد.\"}",
        ),
        Example(
            "پیام: امروز چه کارهایی دارم؟",
            "{\"actions\":[{\"tool\":\"list_tasks\",\"args\":{\"range\":\"today\"}}],\"reply\":\"این‌ها کارهای امروزت هستند.\"}",
        ),
        Example(
            "پیام: پس‌فردا کی دو ساعت وقت آزاد دارم؟",
            "{\"actions\":[{\"tool\":\"find_free_slot\",\"args\":{\"day\":\"پس‌فردا\",\"minutes\":120}}],\"reply\":\"این‌ها وقت‌های آزاد پس‌فردا هستند.\"}",
        ),
        Example(
            "پیام: بریم روی کلاس گیتار تمرکز کنیم",
            "{\"actions\":[{\"tool\":\"start_focus\",\"args\":{\"task\":\"#3\"}}],\"reply\":\"تمرکز روی کلاس گیتار شروع شد.\"}",
        ),
        Example(
            "پیام: امروز پیاده‌روی رفتم",
            "{\"actions\":[{\"tool\":\"log_habit\",\"args\":{\"habit\":\"پیاده‌روی\",\"count\":null}}],\"reply\":\"آفرین! پیاده‌روی امروز ثبت شد.\"}",
        ),
        Example(
            "پیام: می‌خوام هر شب ساعت ۱۰ کتاب بخونم، برام عادتش کن",
            "{\"actions\":[{\"tool\":\"create_habit\",\"args\":{\"name\":\"کتاب خواندن\",\"per_week\":null,\"per_day\":1,\"reminder\":\"ساعت ۱۰ شب\"}}],\"reply\":\"عادت کتاب خواندن با یادآوری ساعت ۱۰ شب ساخته شد.\"}",
        ),
        Example(
            "امروز: سه‌شنبه ۱۴ آبان، ساعت ۰۹:۱۵\nکارها:\n#1 تحویل پروژه — دیروز [عقب‌افتاده] [مهم]\n#2 خرید دارو — امروز\n#3 شستن فرش — دیروز [عقب‌افتاده]\nعادت‌ها: ندارد\nپیام: کارهای عقب‌افتاده رو ببر برای فردا",
            "{\"actions\":[{\"tool\":\"reschedule_overdue\",\"args\":{\"when\":\"فردا\"}}],\"reply\":\"دو کار عقب‌افتاده برای فردا جابه‌جا شوند؟\"}",
        ),
        Example(
            "پیام: دارو رو خریدم، فردا ساعت ۹ هم یادم بنداز به بیمه زنگ بزنم",
            "{\"actions\":[{\"tool\":\"complete_task\",\"args\":{\"task\":\"#2\"}},{\"tool\":\"create_task\",\"args\":{\"title\":\"زنگ به بیمه\",\"when\":\"فردا ساعت ۹\",\"repeat\":null,\"important\":false,\"urgent\":false,\"minutes\":null,\"project\":null}}],\"reply\":\"خرید دارو تیک خورد و تماس با بیمه برای فردا ساعت ۹ ثبت شد.\"}",
        ),
        Example(
            "پیام: یه کار خیلی فوری و مهم: جواب ایمیل مدیر تا ظهر",
            "{\"actions\":[{\"tool\":\"create_task\",\"args\":{\"title\":\"جواب ایمیل مدیر\",\"when\":\"امروز ظهر\",\"repeat\":null,\"important\":true,\"urgent\":true,\"minutes\":null,\"project\":null}}],\"reply\":\"جواب ایمیل مدیر با اولویت بالا برای امروز ظهر ثبت شد.\"}",
        ),
        Example(
            "پیام: هر جمعه صبح خرید هفتگی",
            "{\"actions\":[{\"tool\":\"create_task\",\"args\":{\"title\":\"خرید هفتگی\",\"when\":\"جمعه صبح\",\"repeat\":\"هر جمعه\",\"important\":false,\"urgent\":false,\"minutes\":null,\"project\":null}}],\"reply\":\"خرید هفتگی برای هر جمعه صبح ثبت شد.\"}",
        ),
        Example(
            "پیام: سلام، خسته نباشی",
            "{\"actions\":[],\"reply\":\"سلام! ممنون. بگو چه کاری برات انجام بدم.\"}",
        ),
    )
}
