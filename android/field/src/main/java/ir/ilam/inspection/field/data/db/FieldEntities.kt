package ir.ilam.inspection.field.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One inspection or report. The columns are the ones the server filters on;
 * everything particular to a kind (checklists, feeder readings, the thermal
 * track summary) is [payload], a JSON object — the same split as the server's
 * `field_items`, so the item travels as it is stored.
 */
@Entity(tableName = "field_item")
data class FieldItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kind: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: Int,
    @ColumnInfo(name = "sync_error") val syncError: String? = null,
    @ColumnInfo(name = "sent_at") val sentAt: Long? = null,
    @ColumnInfo(name = "tracking_code") val trackingCode: String? = null,
    @ColumnInfo(name = "server_status") val serverStatus: Int? = null,
    val priority: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Double? = null,
    val address: String? = null,
    val plate: String? = null,
    val description: String? = null,
    val payload: String? = null
)

/**
 * A file belonging to an item. [path] is relative to the app's private files,
 * never absolute. [uploaded] is how much of it the server has confirmed, so a
 * dropped connection resumes from there.
 */
@Entity(
    tableName = "field_file",
    foreignKeys = [ForeignKey(
        entity = FieldItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["item_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("item_id")]
)
data class FieldFileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "item_id") val itemId: String,
    val role: Int,
    val mime: String,
    val size: Long,
    val sha256: String,
    val path: String?,
    @ColumnInfo(name = "captured_at") val capturedAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Double? = null,
    @ColumnInfo(name = "location_uncertain") val locationUncertain: Boolean = false,
    @ColumnInfo(name = "asset_type") val assetType: Int? = null,
    val plate: String? = null,
    val note: String? = null,
    val uploaded: Long = 0,
    val complete: Boolean = false
)
