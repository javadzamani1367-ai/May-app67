package ir.minerfinder.core.acoustic

/** Tunables of the acoustic detector; the defaults follow the specification. */
data class AcousticConfig(
    val sampleRate: Int = 48_000,
    val fftSize: Int = 8192,
    val hopSize: Int = 4096,
    val minHz: Double = 100.0,
    val maxHz: Double = 5000.0,
    val minF0: Double = 300.0,
    val maxF0: Double = 1500.0,
    val minPartials: Int = 3,
    /** How far above the median floor a peak must stand. */
    val peakThresholdDb: Double = 12.0,
    /** Window over which the series must stay put, 10..30 s. */
    val stabilityWindowMs: Long = 20_000L,
    /** Power spectra are averaged over about this many frames before peak picking. */
    val averagingFrames: Int = 4,
    /** Spectral subtraction strength for the calibrated ambient profile. */
    val subtractionFactor: Double = 1.5,
)
