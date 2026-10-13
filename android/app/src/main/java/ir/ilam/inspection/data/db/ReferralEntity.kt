package ir.ilam.inspection.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The field referrals sent to this expert, as the server last described them,
 * so they can be read and acted on with no signal — at the site is exactly
 * where there is none. The server's description is kept whole as JSON; the
 * columns beside it are what the list sorts and counts by, and what only this
 * phone knows: the case it built from the referral, and a result it could not
 * send yet.
 *
 * Phone only, like `server_sync`: the archive never sees it, which is why
 * [DATABASE_VERSION] moved and [SCHEMA_VERSION] did not.
 */
@Entity(tableName = "referrals")
data class ReferralEntity(
    @PrimaryKey val id: String,
    val json: String,
    val status: Int,
    @ColumnInfo(name = "due_at") val dueAt: Long?,
    @ColumnInfo(name = "report_id") val reportId: String? = null,
    @ColumnInfo(name = "pending_result") val pendingResult: String? = null,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long
)

@Dao
abstract class ReferralDao {

    @Query("SELECT * FROM referrals ORDER BY status, due_at IS NULL, due_at, fetched_at")
    abstract fun observeAll(): Flow<List<ReferralEntity>>

    @Query("SELECT * FROM referrals WHERE id = :id")
    abstract fun observe(id: String): Flow<ReferralEntity?>

    @Query("SELECT * FROM referrals WHERE id = :id")
    abstract suspend fun byId(id: String): ReferralEntity?

    /** Open and not yet answered from this phone: what the badge counts. */
    @Query("SELECT COUNT(*) FROM referrals WHERE status = :open AND pending_result IS NULL AND report_id IS NULL")
    abstract fun openCount(open: Int): Flow<Int>

    @Query("SELECT * FROM referrals WHERE pending_result IS NOT NULL")
    abstract suspend fun withPendingResult(): List<ReferralEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(row: ReferralEntity)

    @Query("UPDATE referrals SET report_id = :reportId WHERE id = :id")
    abstract suspend fun setReportId(id: String, reportId: String)

    @Query("UPDATE referrals SET pending_result = :result WHERE id = :id")
    abstract suspend fun setPendingResult(id: String, result: String?)

    @Query("DELETE FROM referrals WHERE id NOT IN (:keep)")
    abstract suspend fun deleteOthers(keep: List<String>)

    @Query("DELETE FROM referrals")
    abstract suspend fun deleteAll()

    /**
     * The server's list replaces the cache — a referral moved to another expert
     * must leave this phone — but what only this phone knows is carried over:
     * the case built from a referral, and a result still waiting to be sent.
     * A row with a result still waiting is kept even if the server dropped it.
     */
    @Transaction
    open suspend fun replace(rows: List<ReferralEntity>) {
        val waiting = withPendingResult().associateBy { it.id }
        rows.forEach { fresh ->
            val old = byId(fresh.id)
            upsert(fresh.copy(reportId = old?.reportId ?: fresh.reportId, pendingResult = old?.pendingResult))
        }
        val keep = rows.map { it.id } + waiting.keys
        if (keep.isEmpty()) deleteAll() else deleteOthers(keep)
    }
}
