package ir.minerfinder.core.acoustic

import ir.minerfinder.core.dsp.HarmonicSeries
import ir.minerfinder.core.dsp.Peak
import ir.minerfinder.core.dsp.Stability

/**
 * Everything one analysis step produced, for the score and for the screens.
 *
 * @param rawDb this frame's band spectrum before averaging and subtraction
 *   (the live spectrum and the spectrogram draw this).
 * @param cleanDb averaged spectrum after the ambient profile was subtracted
 *   (what the detector looked at).
 */
class AcousticFrame(
    val timestampMs: Long,
    val firstHz: Double,
    val binHz: Double,
    val rawDb: FloatArray,
    val cleanDb: FloatArray,
    val floorDb: Double,
    val peaks: List<Peak>,
    val series: HarmonicSeries?,
    val stability: Stability,
    val tonalScore: Double,
    val harmonicScore: Double,
    val score: Double,
    val confidence: Double,
    val calibrated: Boolean,
    val levelDbfs: Double,
)
