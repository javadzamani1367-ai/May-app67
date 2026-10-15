package ir.ilam.inspection.util

/**
 * A point recorded on the map, with what was true at that moment: when it was
 * recorded, and where the user's own receiver put them. The report keeps all
 * of it, so the office can see later that a pole was marked standing at the
 * pole — or from two streets away.
 */
data class PlacedPoint(
    val latitude: Double,
    val longitude: Double,
    /** The receiver's accuracy when the point is the user's own position; null for a point placed by hand. */
    val accuracy: Double?,
    /** Unix milliseconds of the moment it was recorded. */
    val at: Long,
    /** The user's precise satellite position at that moment, or null when there was none. */
    val observer: Fix?,
    val satellites: Int
) {
    /** Metres between the point and the user who recorded it; null without a precise position. */
    val distance: Double?
        get() = observer?.let { LocationRefiner.distanceMeters(it, Fix(latitude, longitude, 0.0)) }
}
