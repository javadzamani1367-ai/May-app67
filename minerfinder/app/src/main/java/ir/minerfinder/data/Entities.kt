package ir.minerfinder.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One search: from "start" to "stop". */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val note: String = "",
)

/**
 * Position and result every few seconds. [sourcesJson] holds each source's
 * score as {"audio":71.2,...}, so a new source is logged without a schema change.
 */
@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val probability: Float,
    val confidence: Float,
    val sourcesJson: String,
)

/** A direction found by a sweep, drawn on the map as a line. */
@Entity(
    tableName = "bearings",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class BearingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val bearingDeg: Double,
    val sharpness: Double,
    val peakScore: Double,
)

/** A recorded miner sound (signature library). [bandsCsv] and [harmonicsCsv] are comma-separated numbers. */
@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey val id: String,
    val label: String,
    val model: String,
    val note: String,
    val createdAt: Long,
    val fundamentalHz: Double?,
    val harmonicsCsv: String,
    val bandsCsv: String,
)

/** Summary row for the sessions list. */
data class SessionSummary(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val note: String,
    val points: Int,
    val bearings: Int,
    val maxProbability: Float?,
)
