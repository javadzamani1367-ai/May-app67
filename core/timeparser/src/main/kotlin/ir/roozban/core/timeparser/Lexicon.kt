package ir.roozban.core.timeparser

/** Persian vocabulary used by the parser. All entries are in canonical (normalized) form. */
internal object Lexicon {

    /** Colloquial and alternative spellings → canonical spelling. */
    private val CANONICAL = mapOf(
        "دیگه" to "دیگر", "دگه" to "دیگر", "دیگ" to "دیگر",
        "یه" to "یک",
        "صب" to "صبح", "صبحی" to "صبح",
        "عصری" to "عصر", "ظهری" to "ظهر", "شبی" to "شب",
        "میون" to "میان",
        "شیش" to "شش", "پونزده" to "پانزده", "شونزده" to "شانزده",
        "هیفده" to "هفده", "هیجده" to "هجده", "هیژده" to "هجده",
        "هیجدهم" to "هجدهم",
        "اینده" to "آینده",
        "اخر" to "آخر", "آخرِ" to "آخر",
        "دیقه" to "دقیقه", "دقه" to "دقیقه",
        "روزای" to "روزهای",
        "امرداد" to "مرداد", "ابان" to "آبان", "اذر" to "آذر",
        "پسفردا" to "پسفردا", "پس‌فردا" to "پسفردا",
        "بعدازظهر" to "بعدازظهر", "بعدظهر" to "بعدازظهر", "بعدازظهری" to "بعدازظهر",
        "سیم" to "سی‌ام",
    )

    fun canonical(word: String): String = CANONICAL[word] ?: word

    val CARDINALS: Map<String, Int> = mapOf(
        "صفر" to 0, "یک" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5, "شش" to 6,
        "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10, "یازده" to 11, "دوازده" to 12,
        "سیزده" to 13, "چهارده" to 14, "پانزده" to 15, "شانزده" to 16, "هفده" to 17,
        "هجده" to 18, "نوزده" to 19, "بیست" to 20, "سی" to 30, "چهل" to 40, "پنجاه" to 50,
        "شصت" to 60,
    )

    val ORDINALS: Map<String, Int> = mapOf(
        "اول" to 1, "یکم" to 1, "دوم" to 2, "سوم" to 3, "چهارم" to 4, "پنجم" to 5,
        "ششم" to 6, "هفتم" to 7, "هشتم" to 8, "نهم" to 9, "دهم" to 10, "یازدهم" to 11,
        "دوازدهم" to 12, "سیزدهم" to 13, "چهاردهم" to 14, "پانزدهم" to 15, "شانزدهم" to 16,
        "هفدهم" to 17, "هجدهم" to 18, "نوزدهم" to 19, "بیستم" to 20, "سی‌ام" to 30,
    )

    /** Ordinal suffix tokens, e.g. «۱۵ام» → «15» «ام». */
    val ORDINAL_SUFFIXES = arrayOf("ام", "م", "امین", "مین")

    val JALALI_MONTHS: Map<String, Int> = mapOf(
        "فروردین" to 1, "اردیبهشت" to 2, "خرداد" to 3, "تیر" to 4, "مرداد" to 5, "شهریور" to 6,
        "مهر" to 7, "آبان" to 8, "آذر" to 9, "دی" to 10, "بهمن" to 11, "اسفند" to 12,
    )

    /** Single-token weekday names → Iranian index (0 = Saturday). */
    val WEEKDAYS: Map<String, Int> = mapOf(
        "شنبه" to 0, "یکشنبه" to 1, "دوشنبه" to 2, "سهشنبه" to 3,
        "چهارشنبه" to 4, "پنجشنبه" to 5, "جمعه" to 6,
    )

    /** Number word before «شنبه» in split spellings like «سه شنبه» → weekday index. */
    val WEEKDAY_PREFIXES: Map<String, Int> = mapOf(
        "یک" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5,
    )

    /** «بعد»، «آینده»، «دیگر»: mark the next period. */
    val NEXT = arrayOf("بعد", "آینده", "دیگر", "بعدی", "بعدش")

    /** Suffixes that turn an amount into a future offset: «دو ساعت دیگر». */
    val OFFSET_SUFFIXES = arrayOf("دیگر", "بعد", "بعدش", "آینده")

    /** Words allowed between components of one time expression. */
    val FILLERS = setOf("ساعت", "در", "ی", "روز", "و", "،", ",", "-", "تا", "از", "حدود", "سر", "راس", "رأس")

    /** Words that may directly precede a time expression and are removed from the title with it. */
    val PREFIX_FILLERS = setOf("تا", "برای", "در", "از", "روز", "ساعت", "حدود", "سر", "راس")
}
