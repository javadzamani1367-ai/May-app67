package ir.roozban.ai.tools

import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.domain.QuickAddParser
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The assistant without a language model: understands the common requests from the words
 * themselves, instantly and fully offline. It reuses what the app already knows how to do —
 * the Persian time parser, the detection of listed tasks and habits a message mentions
 * ([Hints]) and quick-add parsing — and hands its tool calls to the same [ActionPlanner] and
 * executor as before (with confirmations and undo).
 *
 * Each part of a message ("…، …", "… و بعدش …") is read on its own; what is not understood gets
 * a reply with examples instead of a guess.
 */
class RuleAssistant(private val context: AssistantContext) {
    private val parser = QuickAddParser()

    fun answer(message: String): AssistantResponse {
        val text = message.trim()
        if (text.isEmpty()) return AssistantResponse(emptyList(), HELP)
        MemoryIntent.detect(context, text)?.let { return it }
        if (isSmallTalk(text)) return AssistantResponse(emptyList(), smallTalkReply(text))

        val calls = clauses(text).flatMap { understand(it) }
        if (calls.isEmpty()) return AssistantResponse(emptyList(), HELP)
        return AssistantResponse(calls.take(Tools.MAX_ACTIONS), reply(calls))
    }

    // --- Splitting ---

    /** Parts that each ask for one thing. «و» splits only when both sides ask for something themselves. */
    internal fun clauses(text: String): List<String> {
        // «…، مهمه» only qualifies what came before it.
        val parts = STRONG_SPLIT.split(text).map { it.trim() }.filter { it.isNotEmpty() }
            .fold(mutableListOf<String>()) { acc, p -> if (acc.isNotEmpty() && MODIFIER_ONLY.matches(norm(p))) acc[acc.size - 1] = acc.last() + " " + p else acc += p; acc }
        return parts.flatMap { part ->
            // «دو تا کار اضافه کن: خرید میوه و شستن ماشین»
            LIST_OF_NEW.find(part)?.let { m ->
                return@flatMap m.groupValues[1].split(Regex("\\s+و\\s+|،|,")).map { it.trim() }.filter { it.isNotEmpty() }.map { "$NEW_MARK $it" }
            }
            val pieces = part.split(Regex("\\s+و\\s+"))
            if (pieces.size < 2) return@flatMap listOf(part)
            // Rejoin pieces unless each side is a request of its own.
            val out = mutableListOf(pieces[0])
            for (p in pieces.drop(1)) {
                val prev = out.last()
                if (standsAlone(prev) && standsAlone(p)) out += p else out[out.size - 1] = "$prev و $p"
            }
            out
        }
    }

    private fun standsAlone(s: String): Boolean = kindOf(s) != Kind.CREATE || Hints.find(context, s).time != null && hasVerbOrNoun(s)

    private fun hasVerbOrNoun(s: String) = s.split(' ').count { it.isNotBlank() } >= 3

    // --- Understanding one part ---

    private enum class Kind { LIST, SLOT, FOCUS, NEW_HABIT, LOG_HABIT, BULK, DELETE, UPDATE, MOVE, COMPLETE, CREATE }

    private fun kindOf(s: String): Kind {
        val t = norm(s)
        val hints = Hints.find(context, s)
        val mentionsTask = hints.tasks.isNotEmpty()
        return when {
            s.startsWith(NEW_MARK) -> Kind.CREATE
            has(t, HABIT_WORDS) && has(t, NEW_WORDS) -> Kind.NEW_HABIT
            has(t, FOCUS_WORDS) -> Kind.FOCUS
            has(t, SLOT_WORDS) && (has(t, SLOT_ASK) || t.contains("?") || t.contains("؟")) -> Kind.SLOT
            isListRequest(t) -> Kind.LIST
            has(t, OVERDUE_WORDS) && has(t, MOVE_WORDS) && has(t, ALL_WORDS + listOf("کارهای", "کارای", "کارها")) -> Kind.BULK
            mentionsTask && has(t, DELETE_WORDS) -> Kind.DELETE
            mentionsTask && has(t, UPDATE_WORDS) -> Kind.UPDATE
            mentionsTask && has(t, MOVE_WORDS) -> Kind.MOVE
            hints.habits.isNotEmpty() && isDoneStatement(t) && !(mentionsTask && strongerTask(s, hints)) -> Kind.LOG_HABIT
            mentionsTask && isDoneStatement(t) -> Kind.COMPLETE
            else -> Kind.CREATE
        }
    }

    private fun understand(part: String): List<ToolCall> {
        val hints = Hints.find(context, part)
        val task = hints.tasks.firstOrNull()?.let { "#$it" }
        return when (kindOf(part)) {
            Kind.LIST -> listOf(call(Tools.listTasks, "range" to range(norm(part))))
            Kind.SLOT -> listOf(
                call(
                    Tools.findFreeSlot,
                    "day" to (hints.time ?: "امروز"),
                    "minutes" to (hints.minutes ?: minutesFromWords(part) ?: 30),
                ),
            )
            Kind.FOCUS -> listOf(call(Tools.startFocus, "task" to task))
            Kind.NEW_HABIT -> listOf(newHabit(part, hints))
            Kind.LOG_HABIT -> listOf(call(Tools.logHabit, "habit" to hints.habits.first(), "count" to count(part)))
            Kind.BULK -> listOfNotNull(hints.time?.let { call(Tools.rescheduleOverdue, "when" to it) })
            Kind.DELETE -> listOf(call(Tools.deleteTask, "task" to task))
            Kind.UPDATE -> listOf(update(part, task!!))
            Kind.MOVE -> {
                val time = hints.time ?: hints.repeat
                if (time == null) emptyList() else listOf(call(Tools.reschedule, "task" to task, "when" to time))
            }
            Kind.COMPLETE -> listOf(call(Tools.completeTask, "task" to task))
            Kind.CREATE -> listOfNotNull(create(part.removePrefix(NEW_MARK).trim(), hints))
        }
    }

    private fun create(part: String, hints: Hints): ToolCall? {
        val parsed = parser.parse(part, context.now, context.settings)
        var title = parsed.title
        CREATE_PREFIXES.forEach { title = title.replace(it, " ") }
        title = title.replace(TRAILING_NOISE, " ").replace(Regex("\\s+"), " ").trim().trim(':', '،', ',', '.', '-', '!').trim()
        // «یادم بنداز … قرص بخورم» → «قرص بخورم»; «بیدارم کن» → «بیدار شدن».
        if (title.isEmpty() && WAKE.containsMatchIn(part)) title = "بیدار شدن"
        if (title.isEmpty()) return null
        val project = PROJECT.find(part)?.groupValues?.get(1)
        if (project != null) title = title.replace(Regex("^برای\\s+پروژه(?:‌ی|ی)?\\s+\\S+\\s*[:،]?\\s*"), "").trim()
        val t = norm(part)
        return call(
            Tools.createTask,
            "title" to title,
            "when" to hints.time,
            "repeat" to hints.repeat,
            "important" to (parsed.important || has(t, listOf("مهمه", "مهم است", "مهم"))),
            "urgent" to (parsed.urgent || has(t, listOf("فوری", "فوریه"))),
            "minutes" to hints.minutes,
            "project" to project,
        )
    }

    private fun newHabit(part: String, hints: Hints): ToolCall {
        var name = " $part "
        listOfNotNull(hints.time, hints.repeat).forEach { name = name.replace(it, " ") }
        PER_WEEK.find(name)?.let { name = name.replace(it.value, " ") }
        // Whole words only: «رو» must not eat the «رو» of «روز».
        HABIT_NOISE.forEach { w -> name = name.replace(Regex("(?<=^|\\s)" + Regex.escape(w) + "(?=\\s|$|[:،,])"), " ") }
        name = name.replace(Regex("\\s+"), " ").trim().trim(':', '،', ',', '.', '-').trim()
        val perWeek = PER_WEEK.find(PersianDigits.toAscii(part))?.groupValues?.get(1)?.toIntOrNull()
        // «هر روز ساعت ۷ صبح» is a repeat; its time part is the reminder.
        val reminder = hints.time ?: MessageGrammar.variants(hints.repeat).firstOrNull { it.startsWith("ساعت") }
        return call(Tools.createHabit, "name" to name, "per_week" to perWeek, "per_day" to 1, "reminder" to reminder)
    }

    private fun update(part: String, task: String): ToolCall {
        val t = norm(part)
        val rename = RENAME.find(part)?.groupValues?.get(1)?.trim()
        return call(
            Tools.updateTask,
            "task" to task,
            "title" to rename,
            "important" to if (t.contains("مهم")) true else null,
            "urgent" to if (t.contains("فوری")) true else null,
            "notes" to null,
        )
    }

    // --- Helpers ---

    private fun isListRequest(t: String): Boolean {
        if (has(t, SLOT_WORDS)) return false
        val asks = has(t, LIST_ASK) || t.endsWith("؟") || t.endsWith("?")
        val about = has(t, listOf("کار", "برنامه", "دارم", "چی دارم", "عقب"))
        return asks && about
    }

    private fun range(t: String) = when {
        has(t, listOf("عقب", "مونده", "مانده")) -> Tools.RANGE_OVERDUE
        has(t, listOf("فردا")) -> Tools.RANGE_TOMORROW
        has(t, listOf("هفته")) -> Tools.RANGE_WEEK
        has(t, ALL_WORDS) -> Tools.RANGE_ALL
        else -> Tools.RANGE_TODAY
    }

    private fun isDoneStatement(t: String): Boolean = has(t, DONE_WORDS) || PAST_VERB.containsMatchIn(t)

    /** When a message mentions both a listed task and a habit, the one it names more fully wins. */
    private fun strongerTask(s: String, hints: Hints): Boolean {
        val taskScore = hints.tasks.maxOfOrNull { n -> Matcher.mentions(s, context.tasks[n - 1].title) } ?: 0.0
        val habitScore = hints.habits.maxOfOrNull { Matcher.mentions(s, it) } ?: 0.0
        return taskScore > habitScore
    }

    private fun count(part: String): Int? {
        val t = PersianDigits.toAscii(part)
        Regex("(\\d{1,2})\\s*(?:تا|لیوان|بار|عدد|ساعت|دقیقه|صفحه)").find(t)?.let { return it.groupValues[1].toInt() }
        NUMBER_WORDS.forEach { (w, n) -> if (Regex("(^|\\s)$w\\s+(?:تا|لیوان|بار|عدد|صفحه)").containsMatchIn(part)) return n }
        return null
    }

    private fun minutesFromWords(part: String): Int? {
        val t = PersianDigits.toAscii(part)
        Regex("(\\d{1,3})\\s*ساعت").find(t)?.let { return it.groupValues[1].toInt() * 60 }
        Regex("(\\d{1,3})\\s*دقیقه").find(t)?.let { return it.groupValues[1].toInt() }
        if (Regex("(^|\\s)(یک|یه)\\s+ساعت").containsMatchIn(part)) return 60
        if (Regex("(^|\\s)دو\\s+ساعت").containsMatchIn(part)) return 120
        if (part.contains("نیم ساعت")) return 30
        return null
    }

    private fun reply(calls: List<ToolCall>): String = when {
        calls.all { it.tool == Tools.listTasks.name } -> "این‌ها کارهایت هستند:"
        calls.all { it.tool == Tools.findFreeSlot.name } -> "این وقت‌ها آزادند:"
        else -> "انجام شد."
    }

    private fun isSmallTalk(text: String): Boolean {
        val t = norm(text).trim('!', '.', '؟', '?', ' ')
        return SMALL_TALK.any { t == it || t.startsWith("$it ") && t.length < it.length + 12 } || WEATHER.containsMatchIn(t)
    }

    private fun smallTalkReply(text: String): String {
        val t = norm(text)
        return when {
            WEATHER.containsMatchIn(t) -> "از هوا خبر ندارم؛ ولی می‌توانم کارهایت را برنامه‌ریزی کنم."
            has(t, listOf("ممنون", "مرسی", "متشکر", "دمت گرم", "خسته نباشی")) -> "خواهش می‌کنم! کار دیگری هست؟"
            else -> "سلام! چه کاری برایت انجام بدهم؟"
        }
    }

    private fun call(spec: ToolSpec, vararg args: Pair<String, Any?>): ToolCall {
        val given = args.toMap()
        // Every argument in the tool's order, unknown ones as null (as the model would write them).
        return ToolCall(
            spec.name,
            spec.args.associate { a ->
                a.name to when (val v = given[a.name]) {
                    null -> JsonNull
                    is Boolean -> JsonPrimitive(v)
                    is Number -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString())
                }
            },
        )
    }

    private fun has(t: String, words: List<String>) = words.any { norm(it) in t }

    private fun norm(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace('‌', ' ').replace(Regex("\\s+"), " ")

    companion object {
        const val HELP = "این را متوجه نشدم. می‌توانی مثلاً بگویی:\n" +
            "• «فردا ساعت ۵ عصر زنگ به مامان»\n" +
            "• «خرید نان را انجام دادم»\n" +
            "• «جلسه با علی را بگذار برای شنبه»\n" +
            "• «فردا چی دارم؟»"

        private const val NEW_MARK = "⁣new⁣"
        private val STRONG_SPLIT = Regex("\\s*[،؛;]\\s*|\\s+و\\s+بعد(?:ش)?\\s+|\\s+بعدش\\s+|\\n+")
        private val LIST_OF_NEW = Regex("(?:چند|دو|سه|چهار|\\d)\\s*تا\\s+کار\\s+(?:اضافه\\s+کن|بساز|بنویس)\\s*[:：]\\s*(.+)")
        private val HABIT_WORDS = listOf("عادت", "روال")
        private val NEW_WORDS = listOf("جدید", "بساز", "اضافه", "تازه", "درست کن")
        private val FOCUS_WORDS = listOf("تمرکز", "پومودورو", "فوکوس")
        private val SLOT_WORDS = listOf("وقت آزاد", "وقت خالی", "وقت پیدا", "فرصت خالی", "زمان آزاد")
        private val SLOT_ASK = listOf("دارم", "پیدا کن", "هست", "کی ")
        private val LIST_ASK = listOf("چی دارم", "چیه", "چی هست", "نشون بده", "نشان بده", "بگو", "کدوم", "کدام", "لیست", "فهرست", "چه کارهایی", "چه کاری", "چیا", "کدوما")
        private val OVERDUE_WORDS = listOf("عقب", "مونده", "مانده", "گذشته")
        private val ALL_WORDS = listOf("همه", "کل", "تمام")
        private val MOVE_WORDS = listOf("بذار", "بگذار", "بنداز", "بینداز", "ببر", "منتقل", "جابجا", "جابه جا", "عوض کن", "موکول")
        private val DELETE_WORDS = listOf("حذف", "پاک کن", "پاکش", "دور بریز", "نمی خوام", "نمی‌خوام")
        private val UPDATE_WORDS = listOf("مهم کن", "فوری کن", "مهمش کن", "فوریش کن", "اسم", "عنوان")
        private val DONE_WORDS = listOf("انجام شد", "انجام دادم", "انجامش دادم", "تموم شد", "تمام شد", "تموم کردم", "تمام کردم", "تیک بزن", "تیکش بزن", "زدم")
        /** «خریدم»، «کردم»، «خوندم»، «رفتم»، «خوردم»… */
        private val PAST_VERB = Regex("(^|\\s)(\\S+)(یدم|ردم|دم|ندم|تم|فتم|ستم|وردم|وندم|اندم)(\\s|$|[.!،])")
        private val TRAILING_NOISE = Regex("(^|\\s)(رو|را|هم|لطفا|لطفاً|یه|یک|که|بزنم|کنم)(?=\\s|$)")
        private val CREATE_PREFIXES = listOf(
            "یادم بنداز", "یادم بیار", "یادآوری کن", "یه یادآور بذار برای", "یه یادآور بذار", "یادآور بذار", "یادآوری بذار",
            "اضافه کن", "بنویس", "یه کار", "یک کار", "کار جدید", "برام", "برای امشب", "بیدارم کن",
        )
        private val WAKE = Regex("بیدارم\\s+کن")
        private val PROJECT = Regex("پروژه(?:‌ی|ی)?\\s+([\\p{L}\\p{N}‌_-]+)")
        private val PER_WEEK = Regex("(?:هفته‌ای|هفته ای|هفته)\\s*(\\d)\\s*(?:بار|روز)")
        private val RENAME = Regex("(?:اسم|عنوان)[^\\n]*?(?:رو|را)\\s+(?:بکن|بذار|عوض کن به|تغییر بده به)\\s+(.+)$")
        private val HABIT_NOISE = listOf(
            "یه عادت جدید", "یک عادت جدید", "عادت جدید", "به عادت‌هام", "به عادتهام", "به عادت‌ها", "عادت",
            "یه روال جدید", "یک روال جدید", "روال جدید", "به روال‌هام", "به روالهام", "به روال‌ها", "روال", "بساز", "اضافه کن", "رو", "را", "هر روز",
        )
        private val MODIFIER_ONLY = Regex("(مهم|مهمه|مهم است|فوری|فوریه|خیلی مهمه|مهم و فوری)[.!]?")
        private val SMALL_TALK = listOf("سلام", "ممنون", "مرسی", "متشکرم", "متشکر", "دمت گرم", "خسته نباشی", "خداحافظ", "چطوری", "خوبی")
        private val WEATHER = Regex("هوا\\s*(امروز)?\\s*(چطور|چه\\s*طور|چجور)")
        private val NUMBER_WORDS = listOf("یک" to 1, "یه" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5, "شش" to 6, "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10)
    }
}
