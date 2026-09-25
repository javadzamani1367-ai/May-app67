package ir.roozban.ai.tools

/** How much an action can change; decides whether the user must confirm it first. */
enum class Risk {
    /** Only reads: runs at once. */
    READ,

    /** Adds or edits one item: runs at once, with undo. */
    WRITE,

    /** Removes something: asks first. */
    DESTRUCTIVE,

    /** Touches many items at once: asks first. */
    BULK,
}

sealed interface ArgType {
    val nullable: Boolean

    data class Text(override val nullable: Boolean = false) : ArgType

    data class Number(override val nullable: Boolean = false, val max: Int = 9999) : ArgType

    data class Flag(override val nullable: Boolean = false) : ArgType

    data class Choice(val values: List<String>, override val nullable: Boolean = false) : ArgType
}

data class Arg(val name: String, val type: ArgType, val hint: String)

/**
 * A tool the model can call. Every argument is always written, in this order (unknown ones as
 * `null`): a fixed shape keeps the grammar small and small models steady.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val risk: Risk,
    val args: List<Arg>,
)

object Tools {
    const val RANGE_TODAY = "today"
    const val RANGE_TOMORROW = "tomorrow"
    const val RANGE_WEEK = "week"
    const val RANGE_OVERDUE = "overdue"
    const val RANGE_ALL = "all"
    val RANGES = listOf(RANGE_TODAY, RANGE_TOMORROW, RANGE_WEEK, RANGE_OVERDUE, RANGE_ALL)

    private val TASK_REF = Arg("task", ArgType.Text(), "task number like \"#2\"")
    private val WHEN_HINT = "time words from the message"

    val createTask = ToolSpec(
        "create_task", "Add a new task or reminder.", Risk.WRITE,
        listOf(
            Arg("title", ArgType.Text(), "short title without the time words"),
            Arg("when", ArgType.Text(nullable = true), WHEN_HINT),
            Arg("repeat", ArgType.Text(nullable = true), "Persian repeat phrase, e.g. \"هر روز\", \"هر شنبه\""),
            Arg("important", ArgType.Flag(), "important"),
            Arg("urgent", ArgType.Flag(), "urgent"),
            Arg("minutes", ArgType.Number(nullable = true, max = 1440), "estimated minutes"),
            Arg("project", ArgType.Text(nullable = true), "project name"),
        ),
    )
    val updateTask = ToolSpec(
        "update_task", "Rename a task or change its priority. null keeps a field.", Risk.WRITE,
        listOf(
            TASK_REF,
            Arg("title", ArgType.Text(nullable = true), "new title"),
            Arg("important", ArgType.Flag(nullable = true), "important"),
            Arg("urgent", ArgType.Flag(nullable = true), "urgent"),
            Arg("notes", ArgType.Text(nullable = true), "notes"),
        ),
    )
    val reschedule = ToolSpec(
        "reschedule", "Move a task to another time.", Risk.WRITE,
        listOf(TASK_REF, Arg("when", ArgType.Text(), WHEN_HINT)),
    )
    val completeTask = ToolSpec("complete_task", "Mark a task done.", Risk.WRITE, listOf(TASK_REF))
    val deleteTask = ToolSpec("delete_task", "Delete a task.", Risk.DESTRUCTIVE, listOf(TASK_REF))
    val rescheduleOverdue = ToolSpec(
        "reschedule_overdue", "Move every overdue task to another day.", Risk.BULK,
        listOf(Arg("when", ArgType.Text(), WHEN_HINT)),
    )
    val listTasks = ToolSpec(
        "list_tasks", "Show tasks.", Risk.READ,
        listOf(Arg("range", ArgType.Choice(RANGES), "which tasks")),
    )
    val findFreeSlot = ToolSpec(
        "find_free_slot", "Find free time for something.", Risk.READ,
        listOf(
            Arg("day", ArgType.Text(), "Persian day phrase, e.g. \"فردا\""),
            Arg("minutes", ArgType.Number(max = 600), "needed minutes"),
        ),
    )
    val startFocus = ToolSpec(
        "start_focus", "Start a pomodoro focus session, optionally on a task.", Risk.WRITE,
        listOf(Arg("task", ArgType.Text(nullable = true), "task number or title, or null")),
    )
    val createHabit = ToolSpec(
        "create_habit", "Add a habit to track.", Risk.WRITE,
        listOf(
            Arg("name", ArgType.Text(), "habit name"),
            Arg("per_week", ArgType.Number(nullable = true, max = 7), "days per week; null means every day"),
            Arg("per_day", ArgType.Number(max = 20), "times per day, usually 1"),
            Arg("reminder", ArgType.Text(nullable = true), "reminder time, e.g. \"ساعت ۷ صبح\""),
        ),
    )
    val logHabit = ToolSpec(
        "log_habit", "Record that a habit was done today.", Risk.WRITE,
        listOf(
            Arg("habit", ArgType.Text(), "habit name"),
            Arg("count", ArgType.Number(nullable = true, max = 20), "how many times; null adds one"),
        ),
    )

    val all = listOf(createTask, updateTask, reschedule, completeTask, deleteTask, rescheduleOverdue, listTasks, findFreeSlot, startFocus, createHabit, logHabit)

    fun get(name: String): ToolSpec? = all.firstOrNull { it.name == name }

    /** More writes than this in one answer need confirmation too. */
    const val MAX_UNCONFIRMED_WRITES = 3

    /** Actions per answer, enforced by the grammar. */
    const val MAX_ACTIONS = 6
}
