package ir.ilam.inspection.field.thermal

import java.util.Calendar
import java.util.TimeZone

/**
 * HIKMICRO Viewer names every photo and video after the moment it was taken,
 * on the phone's clock, to the millisecond: `20260928112724235.mp4` is
 * 2026-09-28 11:27:24.235. That is more precise than anything the gallery
 * records, so it is the time the matching uses.
 */
object HikmicroNames {
    private val pattern = Regex("""^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(\d{3})""")

    /** The capture time in epoch milliseconds, or null if the name does not follow the pattern. */
    fun capturedAt(fileName: String, zone: TimeZone = TimeZone.getDefault()): Long? {
        val m = pattern.find(fileName) ?: return null
        val v = m.groupValues.drop(1).map { it.toInt() }
        if (v[1] !in 1..12 || v[2] !in 1..31 || v[3] > 23 || v[4] > 59 || v[5] > 59) return null
        return Calendar.getInstance(zone).apply {
            clear()
            set(v[0], v[1] - 1, v[2], v[3], v[4], v[5])
            set(Calendar.MILLISECOND, v[6])
        }.timeInMillis
    }
}

/** A photo or video found in the gallery, not yet part of an inspection. */
data class FoundMedia(
    val uri: String,
    val name: String,
    val video: Boolean,
    val mime: String,
    val size: Long,
    /** When it was taken, on the phone's clock. */
    val takenAt: Long,
    /** For a video, how long it runs. */
    val durationMillis: Long?,
    /** True when the time came from the file name, not the gallery's coarser record. */
    val timeFromName: Boolean
)
