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
 * How and when a case's position was recorded: by the phone's receivers, by a
 * point picked on the map, or typed in — and how many fixes went into it.
 *
 * The coordinate itself and its accuracy live on the case, in the shared
 * schema. This is the story behind them, which the location card shows the
 * expert and which matters when a position is questioned: a point typed in at
 * the office is not a point measured on site, and the screen should say so.
 *
 * Phone only, like `server_sync` and `snippets`: nothing downstream depends on
 * it, so the shared [SCHEMA_VERSION] — and with it the Windows archive and the
 * server — does not move.
 */
@Entity(tableName = "location_fix")
data class LocationFixEntity(
    @PrimaryKey @ColumnInfo(name = "report_id") val reportId: String,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
    /** 0 the phone's receivers, 1 the map, 2 typed in. See [ir.ilam.inspection.data.model.LocationSource]. */
    @ColumnInfo(name = "source") val source: Int,
    /** Fixes averaged into the recorded point; 0 when it was not measured. */
    @ColumnInfo(name = "samples") val samples: Int
)

@Dao
interface LocationFixDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: LocationFixEntity)

    @Query("SELECT * FROM location_fix WHERE report_id = :reportId")
    fun observe(reportId: String): Flow<LocationFixEntity?>

    @Query("DELETE FROM location_fix WHERE report_id = :reportId")
    suspend fun forget(reportId: String)
}
