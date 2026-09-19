package ir.ilam.inspection.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * When this phone last sent each case to the central server.
 *
 * Deliberately **not** the `synced_at` column on the case itself. That column
 * means "last successful send to the Windows archive", and two things depend on
 * it: the archive's incremental pull, and the rule that an archived case may
 * only be deleted once the archive really has it. If sending to the server set
 * it too, the archive would skip the case for ever — and a case could be
 * deleted while the only other copy of it had never arrived anywhere.
 *
 * So this is a table of its own, and a phone-only one: the archive has no
 * business knowing which phone talked to the server when. That is exactly why
 * Room's [DATABASE_VERSION] is allowed to move ahead of the shared
 * [SCHEMA_VERSION].
 */
@Entity(tableName = "server_sync")
data class ServerSyncEntity(
    @PrimaryKey @ColumnInfo(name = "report_id") val reportId: String,
    @ColumnInfo(name = "sent_at") val sentAt: Long
)

@Dao
interface ServerSyncDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun mark(row: ServerSyncEntity)

    /**
     * Cases changed since this phone last sent them to the server, oldest
     * change first so a queue that cannot be finished in one go still makes
     * progress from the front.
     */
    @Query(
        """
        SELECT r.* FROM reports r
        LEFT JOIN server_sync s ON s.report_id = r.id
        WHERE s.sent_at IS NULL OR r.updated_at > s.sent_at
        ORDER BY r.updated_at ASC
        """
    )
    suspend fun pending(): List<ReportEntity>

    @Query(
        """
        SELECT COUNT(*) FROM reports r
        LEFT JOIN server_sync s ON s.report_id = r.id
        WHERE s.sent_at IS NULL OR r.updated_at > s.sent_at
        """
    )
    fun pendingCount(): Flow<Int>

    @Query("DELETE FROM server_sync WHERE report_id = :reportId")
    suspend fun forget(reportId: String)
}
