package ir.roozban.core.domain

import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.model.FactSource
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.learning.EstimateCorrection
import ir.roozban.learning.EstimateFactors
import ir.roozban.learning.EstimateSample
import ir.roozban.learning.Factor
import ir.roozban.learning.Insights
import ir.roozban.learning.LateSample
import ir.roozban.learning.OverdueModel
import ir.roozban.learning.OverdueRisk
import ir.roozban.learning.ProductiveHours
import ir.roozban.learning.TaskFeatures
import ir.roozban.learning.WorkEvent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Everything the nightly run learned; read by the planner and the assistant. */
data class LearningSnapshot(
    val computedAt: Instant,
    val factors: EstimateFactors,
    val hours: ProductiveHours,
    val overdue: OverdueModel,
    /** projectId → late rate. */
    val categoryLate: Map<String, Double>,
    val overallLate: Double?,
    val estimateSamples: Int,
    val lateSamples: Int,
) {
    /** Chance [task] ends up late; 0 for tasks without a date. */
    fun risk(task: Task, zone: ZoneId): Double {
        val due = task.due ?: return 0.0
        return overdue.probability(features(task, due, categoryLate[task.projectId] ?: overallLate ?: DEFAULT_LATE, zone))
    }

    companion object {
        val EMPTY = LearningSnapshot(
            computedAt = Instant.EPOCH,
            factors = EstimateFactors.NONE,
            hours = ProductiveHours.EMPTY,
            overdue = OverdueRisk.fit(emptyList()),
            categoryLate = emptyMap(),
            overallLate = null,
            estimateSamples = 0,
            lateSamples = 0,
        )

        private const val DEFAULT_LATE = 0.25

        internal fun features(task: Task, due: TaskDue, categoryLate: Double, zone: ZoneId) = TaskFeatures(
            important = task.important,
            urgent = task.urgent,
            estimateMinutes = task.estimateMinutes,
            leadDays = ChronoUnit.DAYS.between(task.createdAt.atZone(zone).toLocalDate(), due.date).coerceAtLeast(0),
            dueDay = due.date.dayOfWeek,
            hasTime = due is TaskDue.At,
            recurring = task.recurrence != null,
            hasProject = task.projectId != null,
            categoryLateRate = categoryLate,
        )
    }
}

/** Keeps the latest [LearningSnapshot] (a small file on the phone). */
interface LearningStore {
    suspend fun load(): LearningSnapshot?

    suspend fun save(snapshot: LearningSnapshot)

    suspend fun clear()
}

/** Plain tab-separated text, so the file stays readable and needs no serialization library. */
object LearningSnapshotCodec {
    private const val VERSION = "roozban-learning 1"

    fun encode(s: LearningSnapshot): String = buildString {
        appendLine(VERSION)
        appendLine("at\t${s.computedAt.toEpochMilli()}")
        appendLine("factor\t*\t${s.factors.global.value}\t${s.factors.global.samples}")
        s.factors.byCategory.forEach { (id, f) -> appendLine("factor\t$id\t${f.value}\t${f.samples}") }
        appendLine("hours\t${s.hours.total}\t" + s.hours.grid.joinToString(";") { row -> row.joinToString(",") })
        when (val m = s.overdue) {
            is OverdueModel.Prior -> appendLine("overdue\tprior\t${m.lateRate}\t${m.samples}")
            is OverdueModel.Logistic -> appendLine("overdue\tlogistic\t${m.weights.joinToString(",")}\t${m.samples}")
        }
        s.categoryLate.forEach { (id, r) -> appendLine("late\t$id\t$r") }
        s.overallLate?.let { appendLine("overall\t$it") }
        appendLine("counts\t${s.estimateSamples}\t${s.lateSamples}")
    }

    /** Null for anything unreadable; the next nightly run rewrites it. */
    fun decode(text: String): LearningSnapshot? = runCatching {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.firstOrNull() != VERSION) return null
        var at = Instant.EPOCH
        var global = EstimateFactors.NONE.global
        val byCategory = HashMap<String, Factor>()
        var hours = ProductiveHours.EMPTY
        var overdue: OverdueModel = LearningSnapshot.EMPTY.overdue
        val late = HashMap<String, Double>()
        var overall: Double? = null
        var counts = 0 to 0
        for (line in lines.drop(1)) {
            val p = line.split('\t')
            when (p[0]) {
                "at" -> at = Instant.ofEpochMilli(p[1].toLong())
                "factor" -> Factor(p[2].toDouble(), p[3].toInt()).let { if (p[1] == "*") global = it else byCategory[p[1]] = it }
                "hours" -> {
                    val rows = p[2].split(';').map { r -> r.split(',').map(String::toDouble).toDoubleArray() }
                    if (rows.size == 7 && rows.all { it.size == 24 }) hours = ProductiveHours(rows.toTypedArray(), p[1].toDouble())
                }
                "overdue" -> overdue = when (p[1]) {
                    "prior" -> OverdueModel.Prior(p[2].toDouble(), p[3].toInt())
                    else -> OverdueModel.Logistic(p[2].split(',').map(String::toDouble).toDoubleArray(), p[3].toInt())
                        .takeIf { it.weights.size == OverdueRisk.vector(ZERO_FEATURES).size } ?: overdue
                }
                "late" -> late[p[1]] = p[2].toDouble()
                "overall" -> overall = p[1].toDouble()
                "counts" -> counts = p[1].toInt() to p[2].toInt()
            }
        }
        LearningSnapshot(at, EstimateFactors(global, byCategory), hours, overdue, late, overall, counts.first, counts.second)
    }.getOrNull()

    private val ZERO_FEATURES = TaskFeatures(false, false, null, 0, DayOfWeek.SATURDAY, false, false, false, 0.0)
}

/**
 * The nightly statistics run: estimate correction, productive hours and late risk from the last
 * [WINDOW_DAYS] days, stored as a [LearningSnapshot]; what is clear enough becomes inferred facts.
 * Everything stays on the phone.
 */
class LearningRunner @Inject constructor(
    private val tasks: TaskRepository,
    private val focus: FocusRepository,
    private val projects: ProjectRepository,
    private val settings: SettingsRepository,
    private val store: LearningStore,
    private val memory: MemoryUseCases,
    private val clock: Clock,
) {
    /** Returns null when learning is turned off. */
    suspend fun run(): LearningSnapshot? {
        val prefs = settings.current()
        if (!prefs.planning.learningEnabled) return null
        val now = Instant.now(clock)
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val from = now.minus(Duration.ofDays(WINDOW_DAYS))

        val tracked = focus.observeTracked(from, now).first()
        val completions = tasks.observeCompletionEvents(from, now).first()
        val finished = tasks.observeCompletedSince(from).first().filter { it.recurrence == null }
        val open = tasks.observeOpenTasks().first()

        // Estimate vs. tracked time, for one-off tasks that were timed.
        val secondsByTask = tracked.mapNotNull { t -> t.entry.taskId?.let { it to t.entry.seconds } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val estimates = finished.mapNotNull { t ->
            val estimate = t.estimateMinutes ?: return@mapNotNull null
            val seconds = secondsByTask[t.id] ?: return@mapNotNull null
            if (seconds < 60 || estimate <= 0) return@mapNotNull null
            EstimateSample(t.projectId, estimate, seconds / 60.0, t.completedAt!!)
        }
        val factors = EstimateCorrection.fit(estimates, now)

        // When work happens: tracked time plus each completion.
        val events = tracked.map { WorkEvent(it.entry.start, it.entry.end) } + completions.map { WorkEvent(it.at) }
        val hours = ProductiveHours.fit(events, now, zone)

        // Late or on time, for tasks with a date: finished ones and open ones already past it.
        val labelled = finished.filter { it.due != null }.map { t ->
            t to (t.completedAt!!.atZone(zone).toLocalDate() > t.due!!.date)
        } + open.filter { it.recurrence == null && it.due != null && it.due!!.date < today }.map { it to true }
        val categoryLate = OverdueRisk.categoryRates(labelled.map { (t, late) -> t.projectId to late })
        val overallLate = if (labelled.isEmpty()) null else labelled.count { it.second }.toDouble() / labelled.size
        val samples = labelled.map { (t, late) ->
            LateSample(LearningSnapshot.features(t, t.due!!, categoryLate[t.projectId] ?: overallLate ?: 0.25, zone), late)
        }
        val snapshot = LearningSnapshot(
            computedAt = now,
            factors = factors,
            hours = hours,
            overdue = OverdueRisk.fit(samples),
            categoryLate = categoryLate,
            overallLate = overallLate,
            estimateSamples = estimates.size,
            lateSamples = samples.size,
        )
        store.save(snapshot)

        val names = projects.all().associate { it.id to it.name }
        memory.replace(
            FactSource.INFERRED,
            Insights.from(factors, hours, categoryLate, overallLate, names).map { DerivedFact(it.key, it.text, it.confidence.toFloat()) },
        )
        memory.replace(FactSource.SETTINGS, settingsFacts(prefs.planning.dayStart.hour, prefs.planning.dayEnd.hour))
        return snapshot
    }

    private fun settingsFacts(start: Int, end: Int) = listOf(
        DerivedFact("settings:day", "روز کاری‌ات از ساعت ${PersianDigits.format(start)} تا ${PersianDigits.format(end)} است.", 1f),
    )

    companion object {
        const val WINDOW_DAYS = 120L
    }
}
