package ir.ilam.inspection.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

private const val PARENT = "report_id"

@Entity(
    tableName = "devices",
    foreignKeys = [ForeignKey(
        entity = ReportEntity::class,
        parentColumns = ["id"],
        childColumns = [PARENT],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = [PARENT])]
)
data class DeviceEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = PARENT) val reportId: String,
    @ColumnInfo(name = "row_number") val rowNumber: Int,
    @ColumnInfo(name = "model") val model: String? = null,
    @ColumnInfo(name = "serial_number") val serialNumber: String? = null,
    @ColumnInfo(name = "power_watt") val powerWatt: Double? = null,
    @ColumnInfo(name = "entry_method") val entryMethod: Int = 1,
    @ColumnInfo(name = "note") val note: String? = null
)

@Entity(
    tableName = "attendees",
    foreignKeys = [ForeignKey(
        entity = ReportEntity::class,
        parentColumns = ["id"],
        childColumns = [PARENT],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = [PARENT])]
)
data class AttendeeEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = PARENT) val reportId: String,
    @ColumnInfo(name = "organization") val organization: Int,
    @ColumnInfo(name = "full_name") val fullName: String? = null,
    @ColumnInfo(name = "position") val position: String? = null,
    @ColumnInfo(name = "org_name") val orgName: String? = null
)

/**
 * `file_path` is always relative to the app's media root so a package built on
 * one device can be unpacked anywhere, phone or Windows archive.
 */
@Entity(
    tableName = "media",
    foreignKeys = [ForeignKey(
        entity = ReportEntity::class,
        parentColumns = ["id"],
        childColumns = [PARENT],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = [PARENT])]
)
data class MediaEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = PARENT) val reportId: String,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "caption") val caption: String? = null,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long = 0
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = ReportEntity::class,
        parentColumns = ["id"],
        childColumns = [PARENT],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = [PARENT])]
)
data class AttachmentEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = PARENT) val reportId: String,
    @ColumnInfo(name = "category") val category: Int,
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "mime_type") val mimeType: String? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "note") val note: String? = null
)

/** One row per hand-off of documents to an organisational unit. */
@Entity(
    tableName = "dispatches",
    foreignKeys = [ForeignKey(
        entity = ReportEntity::class,
        parentColumns = ["id"],
        childColumns = [PARENT],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = [PARENT])]
)
data class DispatchEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = PARENT) val reportId: String,
    @ColumnInfo(name = "unit") val unit: Int,
    @ColumnInfo(name = "included_items") val includedItems: String,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "output_format") val outputFormat: Int,
    @ColumnInfo(name = "dispatched_at") val dispatchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String
)
