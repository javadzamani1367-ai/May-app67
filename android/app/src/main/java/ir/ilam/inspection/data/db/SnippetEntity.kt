package ir.ilam.inspection.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * A phrase the expert saved to reuse. Field work repeats itself — the same
 * description, the same officer's name, the same position — and retyping it on
 * a phone at a site is where mistakes and abbreviations creep in.
 *
 * This table is local to the phone: it is a typing convenience, not part of a
 * case, so it is never synced to the archive.
 */
@Entity(
    tableName = "snippets",
    indices = [Index(value = ["field_key", "text"], unique = true)]
)
data class SnippetEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    /** Which field the phrase belongs to, so a name never appears under a note. */
    @ColumnInfo(name = "field_key") val fieldKey: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "used_at") val usedAt: Long = System.currentTimeMillis()
)

@Dao
interface SnippetDao {

    /** Most recently used first: what was needed last is usually needed next. */
    @Query("SELECT * FROM snippets WHERE field_key = :fieldKey ORDER BY used_at DESC")
    fun observeFor(fieldKey: String): Flow<List<SnippetEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(snippet: SnippetEntity)

    @Query("UPDATE snippets SET used_at = :usedAt WHERE id = :id")
    suspend fun touch(id: String, usedAt: Long)

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun delete(id: String)
}
