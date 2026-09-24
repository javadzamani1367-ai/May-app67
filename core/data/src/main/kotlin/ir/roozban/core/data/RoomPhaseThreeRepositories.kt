package ir.roozban.core.data

import ir.roozban.core.database.FocusDao
import ir.roozban.core.database.HabitDao
import ir.roozban.core.database.toEntity
import ir.roozban.core.database.toModel
import ir.roozban.core.domain.FocusRepository
import ir.roozban.core.domain.HabitRepository
import ir.roozban.core.domain.TrackedTime
import ir.roozban.core.model.FocusSession
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitLog
import ir.roozban.core.model.TimeEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class RoomFocusRepository @Inject constructor(private val dao: FocusDao) : FocusRepository {
    override suspend fun record(session: FocusSession, entry: TimeEntry) = dao.record(session.toEntity(), entry.toEntity())

    override fun observeSessions(from: Instant, until: Instant): Flow<List<FocusSession>> =
        dao.observeSessions(from.toEpochMilli(), until.toEpochMilli()).map { list -> list.map { it.toModel() } }

    override fun observeTracked(from: Instant, until: Instant): Flow<List<TrackedTime>> =
        dao.observeTracked(from.toEpochMilli(), until.toEpochMilli()).map { rows ->
            rows.map { TrackedTime(it.entry.toModel(), it.taskTitle, it.projectId) }
        }

    override fun observeTaskSeconds(taskId: String): Flow<Long> = dao.observeTaskSeconds(taskId)

    override suspend fun addTimeEntry(entry: TimeEntry) = dao.upsertEntry(entry.toEntity())

    override suspend fun deleteTimeEntry(id: String) = dao.deleteEntry(id)
}

class RoomHabitRepository @Inject constructor(private val dao: HabitDao) : HabitRepository {
    override fun observeHabits(): Flow<List<Habit>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeHabit(id: String): Flow<Habit?> = dao.observe(id).map { it?.toModel() }

    override fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<HabitLog>> =
        dao.observeLogs(from.toEpochDay(), to.toEpochDay()).map { list -> list.map { it.toModel() } }

    override fun observeHabitLogs(habitId: String): Flow<List<HabitLog>> =
        dao.observeHabitLogs(habitId).map { list -> list.map { it.toModel() } }

    override suspend fun get(id: String): Habit? = dao.get(id)?.toModel()

    override suspend fun all(): List<Habit> = dao.all().map { it.toModel() }

    override suspend fun upsert(habit: Habit) = dao.upsert(habit.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun log(habitId: String, date: LocalDate): HabitLog? = dao.log(habitId, date.toEpochDay())?.toModel()

    override suspend fun setLog(log: HabitLog) {
        if (log.count <= 0) dao.deleteLog(log.habitId, log.date.toEpochDay()) else dao.upsertLog(log.toEntity())
    }
}
