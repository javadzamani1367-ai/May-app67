package ir.roozban.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.core.database.AttendanceDao
import ir.roozban.core.database.MemoryDao
import ir.roozban.core.database.NoteDao
import ir.roozban.core.database.toEntity
import ir.roozban.core.database.toFloatingSeconds
import ir.roozban.core.database.toModel
import ir.roozban.core.domain.AttendanceRepository
import ir.roozban.core.domain.LearningSnapshot
import ir.roozban.core.domain.LearningSnapshotCodec
import ir.roozban.core.domain.LearningStore
import ir.roozban.core.domain.MemoryRepository
import ir.roozban.core.domain.NoteRepository
import ir.roozban.core.model.AttendanceEntry
import ir.roozban.core.model.FactSource
import ir.roozban.core.model.MemoryFact
import ir.roozban.core.model.Note
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomMemoryRepository @Inject constructor(private val dao: MemoryDao) : MemoryRepository {
    override fun observeFacts(): Flow<List<MemoryFact>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun all(): List<MemoryFact> = dao.all().map { it.toModel() }

    override suspend fun get(id: String): MemoryFact? = dao.get(id)?.toModel()

    override suspend fun byKey(key: String): MemoryFact? = dao.byKey(key)?.toModel()

    override suspend fun upsert(fact: MemoryFact) {
        // The key is unique: a fact re-stated under another id replaces the old row.
        dao.byKey(fact.key)?.let { if (it.id != fact.id) dao.delete(it.id) }
        dao.upsert(fact.toEntity())
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun clear() = dao.clear()

    override suspend fun deleteSourceExcept(source: FactSource, keep: List<String>) = dao.deleteSourceExcept(source.name, keep)
}

class RoomNoteRepository @Inject constructor(private val dao: NoteDao) : NoteRepository {
    override fun observeNotes(): Flow<List<Note>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeNote(id: String): Flow<Note?> = dao.observe(id).map { it?.toModel() }

    override suspend fun get(id: String): Note? = dao.get(id)?.toModel()

    override suspend fun upsert(note: Note) = dao.upsert(note.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}

/** The learning snapshot as a small text file in the app's private storage. */
@Singleton
class FileLearningStore @Inject constructor(@ApplicationContext context: Context) : LearningStore {
    private val file = File(context.filesDir, "learning.txt")

    @Volatile
    private var cached: LearningSnapshot? = null

    override suspend fun load(): LearningSnapshot? = cached ?: withContext(Dispatchers.IO) {
        if (!file.exists()) null else LearningSnapshotCodec.decode(file.readText())
    }?.also { cached = it }

    override suspend fun save(snapshot: LearningSnapshot) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "learning.txt.tmp")
        tmp.writeText(LearningSnapshotCodec.encode(snapshot))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        cached = snapshot
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        cached = null
    }
}

class RoomAttendanceRepository @Inject constructor(private val dao: AttendanceDao) : AttendanceRepository {
    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<AttendanceEntry>> =
        dao.observeBetween(from.atStartOfDay().toFloatingSeconds(), to.plusDays(1).atStartOfDay().toFloatingSeconds())
            .map { list -> list.map { it.toModel() } }

    override fun observeOpen(): Flow<AttendanceEntry?> = dao.observeOpen().map { it?.toModel() }

    override suspend fun open(): AttendanceEntry? = dao.open()?.toModel()

    override suspend fun upsert(entry: AttendanceEntry) = dao.upsert(entry.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}
