package ir.minerfinder.sources

/** Ids are stored in the settings (weights, switches); never rename one. */
object SourceIds {
    const val AUDIO = "audio"
    const val MAGNETOMETER = "magnetometer"
    const val COIL = "coil"

    /** Weight a source starts with until the user changes it. */
    fun defaultWeight(id: String): Float = when (id) {
        AUDIO -> 1.0f
        MAGNETOMETER -> 0.5f
        COIL -> 1.0f
        else -> 1.0f
    }
}
