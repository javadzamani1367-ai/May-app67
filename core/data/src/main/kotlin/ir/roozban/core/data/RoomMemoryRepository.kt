package ir.roozban.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.core.database.MemoryDao
import ir.roozban.core.database.toEntity
import ir.roozban.core.database.toModel
import ir.roozban.core.domain.LearningSnapshot
import ir.roozban.core.domain.LearningSnapshotCodec
import ir.roozban.core.domain.LearningStore
import ir.roozban.core.domain.MemoryRepository
import ir.roozban.core.model.FactSource
import ir.roozban.core.model.MemoryFact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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
