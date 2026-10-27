package ir.ilam.inspection.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * People the expert proposes for a centre's rewards: the company's collection
 * crew, police and IRGC officers, and the holders of the administrative posts.
 * They go up with the case (`reward_people`) and the manager confirms, adds or
 * removes them in the centre's reward form; nobody types a name twice.
 *
 * Phone only as far as the Windows archive is concerned, like `referrals`:
 * [DATABASE_VERSION] moved and [SCHEMA_VERSION] did not.
 */
@Entity(
    tableName = "reward_people",
    foreignKeys = [ForeignKey(
        entity = ReportEntity::class,
        parentColumns = ["id"],
        childColumns = ["report_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["report_id"])]
)
data class RewardPersonEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "report_id") val reportId: String,
    /** «collection», «enforcement» or «staff». */
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "post") val post: String? = null,
    /** «company», «faraja», «sepah» or «other». */
    @ColumnInfo(name = "org") val org: String? = null,
    @ColumnInfo(name = "action") val action: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface RewardPersonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(person: RewardPersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(people: List<RewardPersonEntity>)

    @Delete
    suspend fun delete(person: RewardPersonEntity)

    @Query("SELECT * FROM reward_people WHERE report_id = :reportId ORDER BY kind, created_at")
    fun observeFor(reportId: String): Flow<List<RewardPersonEntity>>

    @Query("SELECT * FROM reward_people WHERE report_id = :reportId ORDER BY kind, created_at")
    suspend fun listFor(reportId: String): List<RewardPersonEntity>

    @Query("DELETE FROM reward_people WHERE report_id = :reportId")
    suspend fun deleteFor(reportId: String)

    /** Every name entered before on this phone, with the attendees', so the next case picks instead of typing. */
    @Query("SELECT full_name FROM reward_people UNION SELECT full_name FROM attendees WHERE full_name IS NOT NULL ORDER BY 1")
    fun observeNames(): Flow<List<String>>
}
