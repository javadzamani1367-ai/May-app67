package ir.roozban.ai.tools

/**
 * Narrows the tools for one message using what the app found in it ([Hints]):
 * - tools about an existing task exist only when the message mentions listed tasks, and their
 *   `task` argument can only be one of those;
 * - `log_habit` only for habits the message mentions;
 * - time arguments can only be the message's own time words (or null);
 * - removing, bulk moves, focus, new habits and free-time search need their key words.
 * The model then only chooses among sensible actions, which small models do far better.
 */
object MessageGrammar {
    fun tools(hints: Hints, message: String, hasOverdue: Boolean): List<ToolSpec> {
        val text = Matcher.normalize(message)
        fun says(words: List<String>) = words.any { Matcher.normalize(it) in text }
        val taskRefs = hints.tasks.map { "#$it" }
        val repeat = variants(hints.repeat)
        val time = (variants(hints.time) + repeat).distinct()
        val hasTask = taskRefs.isNotEmpty()

        fun ToolSpec.pin(vararg pins: Pair<String, ArgType>): ToolSpec {
            val map = pins.toMap()
            return copy(args = args.map { a -> map[a.name]?.let { a.copy(type = it) } ?: a })
        }
        val taskChoice = ArgType.Choice(taskRefs)
        val whenOptional = ArgType.Choice(time, nullable = true)
        val whenRequired = ArgType.Choice(time)

        val project = PROJECT.find(message)?.groupValues?.get(1)?.let { listOf(it) }.orEmpty()
        val ranges = RANGE_WORDS.filter { (_, words) -> says(words) }.map { it.first }.ifEmpty { listOf(Tools.RANGE_TODAY, Tools.RANGE_ALL) }
        // An edit or a removal is not a completion unless the message also says it was done.
        val canComplete = !(says(UPDATE_WORDS) || says(DELETE_WORDS)) || says(DONE_WORDS)
        return buildList {
            add(Tools.createTask.pin("when" to whenOptional, "repeat" to ArgType.Choice(repeat, nullable = true), "project" to ArgType.Choice(project, nullable = project.isEmpty())))
            if (hasTask) {
                if (canComplete) add(Tools.completeTask.pin("task" to taskChoice))
                if (time.isNotEmpty()) add(Tools.reschedule.pin("task" to taskChoice, "when" to whenRequired))
                if (says(UPDATE_WORDS)) add(Tools.updateTask.pin("task" to taskChoice))
                if (says(DELETE_WORDS)) add(Tools.deleteTask.pin("task" to taskChoice))
            }
            if (hasOverdue && time.isNotEmpty() && says(OVERDUE_WORDS)) add(Tools.rescheduleOverdue.pin("when" to whenRequired))
            if (says(LIST_WORDS)) add(Tools.listTasks.pin("range" to ArgType.Choice(ranges)))
            if (says(SLOT_WORDS)) add(Tools.findFreeSlot.pin("day" to ArgType.Choice(time.ifEmpty { listOf("امروز") })))
            if (says(FOCUS_WORDS)) add(Tools.startFocus.pin("task" to ArgType.Choice(taskRefs, nullable = true)))
            if (says(HABIT_WORDS)) add(Tools.createHabit.pin("reminder" to whenOptional))
            if (hints.habits.isNotEmpty()) add(Tools.logHabit.pin("habit" to ArgType.Choice(hints.habits)))
        }
    }

    /** The phrase as written plus shorter forms without a leading «برای», «تا», «هر» … */
    fun variants(phrase: String?): List<String> {
        phrase ?: return emptyList()
        val out = mutableListOf(phrase.trim())
        var p = phrase.trim()
        while (true) {
            val first = p.substringBefore(' ')
            if (first == p || first !in LEADING) break
            p = p.substringAfter(' ').trim()
            out += p
        }
        // «هر شنبه ساعت ۹» → also «هر شنبه» for the repeat alone
        // and «ساعت ۹» for the time alone
        out.toList().forEach { v ->
            TIME_WORD.find(v)?.let { m ->
                v.substring(0, m.range.first).trim().takeIf { it.isNotEmpty() }?.let(out::add)
                v.substring(m.range.first).trim().takeIf { it.isNotEmpty() }?.let(out::add)
            }
        }
        return out.distinct()
    }

    private val TIME_WORD = Regex("\\s(ساعت|صبح|ظهر|عصر|شب)(\\s|$)")

    private val LEADING = setOf("برای", "تا", "از", "واسه", "هر", "در", "توی")

    fun grammar(context: AssistantContext, message: String): String {
        val hints = Hints.find(context, message)
        val overdue = context.tasks.any { t -> t.due?.let { it.date < context.today } == true }
        return Gbnf.forTools(tools(hints, message, overdue))
    }

    private val UPDATE_WORDS = listOf("مهم", "فوری", "اسم", "عنوان", "یادداشت", "توضیح")
    private val DELETE_WORDS = listOf("حذف", "پاک", "بردار", "دور بریز", "نمی‌خوام")
    private val OVERDUE_WORDS = listOf("عقب", "مونده", "مانده", "گذشته")
    private val SLOT_WORDS = listOf("وقت", "آزاد", "خالی", "فرصت")
    private val FOCUS_WORDS = listOf("تمرکز", "پومودورو", "فوکوس")
    private val HABIT_WORDS = listOf("عادت")
    private val DONE_WORDS = listOf("انجام", "تموم", "تمام", "تیک", "کردم", "دادم", "خریدم", "رفتم", "فرستادم")
    private val LIST_WORDS = listOf("چی", "چه", "کدوم", "کدام", "نشون", "نشان", "لیست", "فهرست", "کارهام", "کارام", "برنامه")
    private val RANGE_WORDS = listOf(
        Tools.RANGE_OVERDUE to listOf("عقب", "مونده", "مانده"),
        Tools.RANGE_TOMORROW to listOf("فردا"),
        Tools.RANGE_WEEK to listOf("هفته"),
        Tools.RANGE_TODAY to listOf("امروز"),
        Tools.RANGE_ALL to listOf("همه", "کل"),
    )
    private val PROJECT = Regex("پروژه(?:‌ی|ی)?\\s+([\\p{L}\\p{N}‌_-]+)")
}
