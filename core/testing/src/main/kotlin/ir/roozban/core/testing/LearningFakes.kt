package ir.roozban.core.testing

import ir.roozban.core.domain.LearningSnapshot
import ir.roozban.core.domain.LearningStore
import ir.roozban.core.domain.MemoryRepository
import ir.roozban.core.model.FactSource
import ir.roozban.core.model.MemoryFact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMemoryRepository : MemoryRepository {
    val facts = MutableStateFlow<Map<String, MemoryFact>>(emptyMap())

    private fun sorted(m: Map<String, MemoryFact>) = m.values.sortedWith(compareByDescending<MemoryFact> { it.pinned }.thenByDescending { it.updatedAt })

    override fun observeFacts(): Flow<List<MemoryFact>> = facts.map(::sorted)
    override suspend fun all() = sorted(facts.value)
    override suspend fun get(id: String) = facts.value[id]
    override suspend fun byKey(key: String) = facts.value.values.firstOrNull { it.key == key }
    override suspend fun upsert(fact: MemoryFact) {
        facts.value = facts.value.filterValues { it.key != fact.key } + (fact.id to fact)
    }
    override suspend fun delete(id: String) {
        facts.value = facts.value - id
    }
    override suspend fun clear() {
        facts.value = emptyMap()
    }
    override suspend fun deleteSourceExcept(source: FactSource, keep: List<String>) {
        facts.value = facts.value.filterValues { it.source != source || it.pinned || it.key in keep }
    }
}

class FakeLearningStore(var snapshot: LearningSnapshot? = null) : LearningStore {
    override suspend fun load() = snapshot
    override suspend fun save(snapshot: LearningSnapshot) {
        this.snapshot = snapshot
    }
    override suspend fun clear() {
        snapshot = null
    }
}
