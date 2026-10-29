package ir.minerfinder.sources

import ir.minerfinder.AppGraph
import ir.minerfinder.R
import ir.minerfinder.core.dsp.SlidingWindow
import ir.minerfinder.core.magnetic.CoilAnalyzer
import ir.minerfinder.core.magnetic.CoilFrame
import ir.minerfinder.core.sensor.BaseSource
import ir.minerfinder.core.sensor.SourceReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * External pickup coil on an audio input (microphone jack or USB sound card):
 * 50, 150 and 250 Hz by Goertzel, judged on heavy and steady consumption.
 */
class CoilSource(private val graph: AppGraph) : BaseSource() {
    override val id = SourceIds.COIL
    override val label: String = graph.appContext.getString(R.string.source_coil)

    @Volatile
    var analyzer = CoilAnalyzer()
        private set

    private val _frames = MutableStateFlow<CoilFrame?>(null)
    val frames: StateFlow<CoilFrame?> = _frames.asStateFlow()

    override fun isAvailable() = true

    override suspend fun run() {
        analyzer.setBaseline(baseline())
        var window = SlidingWindow(analyzer.blockSize, analyzer.blockSize)
        var samples = 0L
        val start = System.currentTimeMillis()
        graph.audioHub.chunks(graph.settings.current.coilInputId).collect { chunk ->
            chunk.error?.let { fail(it.message ?: "audio"); return@collect }
            if (chunk.sampleRate != analyzer.sampleRate) {
                analyzer = CoilAnalyzer(chunk.sampleRate).also { it.setBaseline(baseline()) }
                window = SlidingWindow(analyzer.blockSize, analyzer.blockSize)
            }
            window.push(chunk.samples) { block ->
                val t = start + (samples + chunk.samples.size) * 1000 / chunk.sampleRate
                val frame = analyzer.process(block, t)
                _frames.value = frame
                emit(reading(frame))
            }
            samples += chunk.samples.size
        }
    }

    override fun onStopped() {
        analyzer.reset()
        _frames.value = null
    }

    fun startCalibration() = analyzer.startCalibration()

    fun finishCalibration(): Boolean {
        val value = analyzer.finishCalibration() ?: return false
        graph.settings.putVector(BASELINE_KEY, doubleArrayOf(value))
        return true
    }

    fun clearCalibration() {
        analyzer.setBaseline(null)
        graph.settings.putVector(BASELINE_KEY, null)
    }

    val hasCalibration: Boolean get() = baseline() != null

    private fun baseline(): Double? = graph.settings.getVector(BASELINE_KEY)?.firstOrNull()

    private fun reading(f: CoilFrame) = SourceReading(
        sourceId = id,
        score = f.score.toFloat(),
        confidence = f.confidence.toFloat(),
        timestampMs = f.timestampMs,
        raw = mapOf(
            "levelDb" to f.levelDb,
            "variation" to f.variation * 100,
            "harmonicRatio" to f.harmonicRatio,
        ),
        detail = f,
    )

    companion object {
        const val BASELINE_KEY = "coil_baseline"
    }
}
