package ir.roozban.ai.tools

import kotlinx.serialization.json.JsonPrimitive

/**
 * Requests about personal memory, recognized by the app without the model:
 * - «یادت باشه (که) من صبح‌ها باشگاه می‌رم» — a statement about the user (first person or a
 *   habit word, and no time words) → remember_preference;
 * - «فراموش کن (که) …» → forget_preference.
 * «یادت باشه فردا نون بخرم» has a time or no personal marker, so it stays a task for the model.
 */
object MemoryIntent {
    fun detect(context: AssistantContext, message: String): AssistantResponse? {
        val text = message.trim()
        FORGET.matchEntire(text)?.let { m ->
            val body = clean(m.groupValues[2])
            if (body.isEmpty()) return null
            return AssistantResponse(listOf(call(Tools.forgetPreference, body)), "باشه، دیگر یادم نمی‌ماند.")
        }
        val m = REMEMBER.matchEntire(text) ?: return null
        val body = clean(m.groupValues[2])
        if (body.length < MIN_CHARS) return null
        // «صبح‌ها», «شنبه‌ها» say "usually", not when: only other time words make it a reminder.
        val hints = Hints.find(context, HABITUAL.replace(body, " "))
        if (hints.time != null || hints.repeat != null) return null
        val normalized = " " + Matcher.normalizeSpaced(body) + " "
        // Whole words; longer markers may also carry a suffix («همیشه‌ام»).
        val personal = MARKERS.any { marker ->
            val w = Matcher.normalizeSpaced(marker)
            " $w " in normalized || (w.length >= 4 && " $w" in normalized)
        }
        if (!personal) return null
        return AssistantResponse(listOf(call(Tools.rememberPreference, body)), "به خاطر سپردم.")
    }

    private fun call(spec: ToolSpec, fact: String) = ToolCall(spec.name, mapOf("fact" to JsonPrimitive(fact)))

    private fun clean(s: String) = s.trim().trimEnd('.', '،', '!', '؟', '?').trim()

    private const val MIN_CHARS = 4

    private val HABITUAL = Regex("(?:صبح|ظهر|بعدازظهر|عصر|شب|روز|آخر\\s*هفته|یک\\s*شنبه|یکشنبه|دو\\s*شنبه|دوشنبه|سه\\s*شنبه|سه‌شنبه|چهارشنبه|پنج\\s*شنبه|پنجشنبه|شنبه|جمعه)(?:\u200c|\\s)?(?:ها|ا)(?=\\s|$|[،.!؟])")

    private val REMEMBER = Regex(
        "^(?:لطفا\\s+|لطفاً\\s+)?(یادت\\s*(?:باشه|بمونه|باشد|بماند)|به\\s*خاطر\\s*بسپار|اینو\\s*بدون|این\\s*را\\s*بدان|بدون)\\s*(?:که\\s+)?[:،,]?\\s*(.+)$",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val FORGET = Regex(
        "^(?:لطفا\\s+|لطفاً\\s+)?(فراموش\\s*کن|یادت\\s*بره|دیگه\\s*یادت\\s*نباشه|از\\s*یادت\\s*ببر)\\s*(?:که\\s+)?[:،,]?\\s*(.+)$",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** Signs of a statement about the person rather than a to-do. */
    private val MARKERS = listOf(
        "من", "ترجیح", "دوست دارم", "دوست ندارم", "معمولا", "معمولاً", "همیشه", "هیچ وقت", "هیچ‌وقت", "عادت دارم",
        "نمی‌تونم", "نمیتونم", "نمی‌توانم", "حساسیت", "آلرژی", "رژیم", "صبح‌ها", "صبحا", "عصرها", "عصرا", "شب‌ها", "شبا",
        "ظهرها", "روزها", "آخر هفته‌ها", "شنبه‌ها", "جمعه‌ها", "پنجشنبه‌ها", "کارم", "شغلم", "خونه‌م", "خانه‌ام", "بچه‌م", "همسرم",
    )
}
