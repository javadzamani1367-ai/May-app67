package ir.ilam.inspection.data.model

/** Where a recorded position came from. Stored as [code] in `location_fix.source`. */
enum class LocationSource(val code: Int) {
    SENSOR(0), MAP(1), MANUAL(2);

    companion object {
        fun of(code: Int?): LocationSource = entries.firstOrNull { it.code == code } ?: SENSOR
    }
}
