package ir.ilam.inspection.field.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * The phone's copy of the area's inspection history, so the comparison opens
 * without signal: one row per panel or pole and part, its last readings as JSON.
 */
@Entity(tableName = "history_key")
data class HistoryKeyEntity(
    /** area|asset|plate_key|part, as the server pages them. */
    @PrimaryKey val id: String,
    val area: String?,
    val asset: Int,
    @ColumnInfo(name = "plate_key") val plateKey: String,
    val plate: String,
    val part: String,
    @ColumnInfo(name = "rows_json") val rowsJson: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long
)

/** Small values of the history copy: the server time it is complete up to. */
@Entity(tableName = "history_meta")
data class HistoryMetaEntity(@PrimaryKey val k: String, val v: Long)

@Dao
interface HistoryDao {
    @Upsert
    suspend fun save(key: HistoryKeyEntity)

    @Query("DELETE FROM history_key WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM history_key WHERE asset = :asset AND plate_key = :plateKey AND part = :part ORDER BY fetched_at DESC LIMIT 1")
    suspend fun find(asset: Int, plateKey: String, part: String): HistoryKeyEntity?

    @Query("SELECT * FROM history_key WHERE part = 'thermal'")
    suspend fun thermal(): List<HistoryKeyEntity>

    @Query("SELECT v FROM history_meta WHERE k = :k")
    suspend fun meta(k: String): Long?

    @Upsert
    suspend fun setMeta(meta: HistoryMetaEntity)
}
