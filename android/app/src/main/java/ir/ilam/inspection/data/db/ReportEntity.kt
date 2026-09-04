package ir.ilam.inspection.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A visit case. `id` is a UUID because several experts write into the same
 * central archive and a sequential number would collide there; `tracking_code`
 * is the human-facing identifier.
 */
@Entity(
    tableName = "reports",
    indices = [
        Index(value = ["tracking_code"], unique = true),
        Index(value = ["status"]),
        Index(value = ["updated_at"]),
        Index(value = ["county"])
    ]
)
data class ReportEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "tracking_code") val trackingCode: String? = null,
    @ColumnInfo(name = "temp_code") val tempCode: String? = null,
    @ColumnInfo(name = "report_type") val reportType: Int,
    @ColumnInfo(name = "status") val status: Int = 0,
    @ColumnInfo(name = "expert_code") val expertCode: String? = null,
    @ColumnInfo(name = "report_date") val reportDate: Long,
    @ColumnInfo(name = "visit_date") val visitDate: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,

    // location
    @ColumnInfo(name = "county") val county: String? = null,
    @ColumnInfo(name = "district") val district: String? = null,
    @ColumnInfo(name = "address") val address: String? = null,
    @ColumnInfo(name = "postal_code") val postalCode: String? = null,
    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null,
    @ColumnInfo(name = "gps_accuracy") val gpsAccuracy: Double? = null,
    @ColumnInfo(name = "file_number") val fileNumber: String? = null,
    @ColumnInfo(name = "bill_number") val billNumber: String? = null,
    @ColumnInfo(name = "subscription_number") val subscriptionNumber: String? = null,
    @ColumnInfo(name = "usage_type") val usageType: String? = null,

    // owner
    @ColumnInfo(name = "owner_name") val ownerName: String? = null,
    @ColumnInfo(name = "owner_national_id") val ownerNationalId: String? = null,
    @ColumnInfo(name = "owner_phone") val ownerPhone: String? = null,
    @ColumnInfo(name = "owner_relation") val ownerRelation: String? = null,

    // technical
    @ColumnInfo(name = "meter_amperage") val meterAmperage: Double? = null,
    @ColumnInfo(name = "measured_amperage") val measuredAmperage: Double? = null,
    @ColumnInfo(name = "connection_type") val connectionType: String? = null,
    @ColumnInfo(name = "seal_status") val sealStatus: String? = null,

    // narrative
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "actions_taken") val actionsTaken: String? = null
) {
    /** What the user sees on a card: the final code, else the temporary one. */
    val displayCode: String? get() = trackingCode ?: tempCode
}
