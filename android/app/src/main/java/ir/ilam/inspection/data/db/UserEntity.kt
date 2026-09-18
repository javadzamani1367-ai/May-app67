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
 * A field expert as the manager registered them. The expert sends the code
 * their installation shows, together with their mobile number; the manager
 * records it here against a name, a county and a user code.
 *
 * The device code is what ties an account to one installation: it is destroyed
 * when the app is removed, so a reinstall or a new phone has to come back to
 * the manager rather than carrying the old account along.
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["user_code"], unique = true)]
)
data class UserEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_code") val userCode: String,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "county") val county: String? = null,
    @ColumnInfo(name = "phone") val phone: String? = null,
    @ColumnInfo(name = "device_code") val deviceCode: String? = null,
    @ColumnInfo(name = "active") val active: Int = 1,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "note") val note: String? = null
)

@Dao
interface UserDao {

    @Query("SELECT * FROM users ORDER BY full_name")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE user_code = :code LIMIT 1")
    suspend fun byCode(code: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: String)
}
