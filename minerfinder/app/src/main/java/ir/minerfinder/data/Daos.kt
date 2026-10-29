package ir.minerfinder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun end(id: Long, endedAt: Long)

    @Query("UPDATE sessions SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query(
        """
        SELECT s.id, s.startedAt, s.endedAt, s.note,
            (SELECT COUNT(*) FROM track_points p WHERE p.sessionId = s.id) AS points,
            (SELECT COUNT(*) FROM bearings b WHERE b.sessionId = s.id) AS bearings,
            (SELECT MAX(probability) FROM track_points p WHERE p.sessionId = s.id) AS maxProbability
        FROM sessions s ORDER BY s.startedAt DESC
        """,
    )
    fun summaries(): Flow<List<SessionSummary>>

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY time")
    fun points(sessionId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY time")
    suspend fun pointsOnce(sessionId: Long): List<TrackPointEntity>

    @Insert
    suspend fun insertPoint(point: TrackPointEntity)

    @Query("SELECT * FROM bearings WHERE sessionId = :sessionId ORDER BY time")
    fun bearings(sessionId: Long): Flow<List<BearingEntity>>

    @Query("SELECT * FROM bearings WHERE sessionId = :sessionId ORDER BY time")
    suspend fun bearingsOnce(sessionId: Long): List<BearingEntity>

    @Insert
    suspend fun insertBearing(bearing: BearingEntity): Long

    @Query("DELETE FROM bearings WHERE id = :id")
    suspend fun deleteBearing(id: Long)

    @Query("SELECT COUNT(*) FROM track_points WHERE sessionId = :id")
    suspend fun pointCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM bearings WHERE sessionId = :id")
    suspend fun bearingCount(id: Long): Int
}

@Dao
interface SignatureDao {
    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    fun all(): Flow<List<SignatureEntity>>

    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    suspend fun allOnce(): List<SignatureEntity>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(signature: SignatureEntity)

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun delete(id: String)
}
