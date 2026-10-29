package ir.minerfinder.sources

import ir.minerfinder.AppGraph
import ir.minerfinder.R
import ir.minerfinder.core.acoustic.AcousticAnalyzer
import ir.minerfinder.core.acoustic.AcousticConfig
import ir.minerfinder.core.acoustic.AcousticFrame
import ir.minerfinder.core.dsp.SlidingWindow
import ir.minerfinder.core.sensor.BaseSource
import ir.minerfinder.core.sensor.SourceReading
import ir.minerfinder.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Microphone (built-in or USB): the miner fans' tonal, harmonic, steady sound. */
class AcousticSource(private val graph: AppGraph) : BaseSource() {
    override val id = SourceIds.AUDIO
    override val label: String = graph.appContext.getString(R.string.source_audio)

    val analyzer = AcousticAnalyzer(configFor(graph.settings.current, AcousticConfig().sampleRate))

    private val _frames = MutableStateFlow<AcousticFrame?>(null)
    /** Full analysis frames for the spectrum screen. */
    val frames: StateFlow<AcousticFrame?> = _frames.asStateFlow()

    override fun isAvailable() = true

    override suspend fun run() {
        analyzer.reconfigure(configFor(graph.settings.current, analyzer.config.sampleRate))
        analyzer.setNoiseProfile(graph.settings.getVector(PROFILE_KEY))
        var window = SlidingWindow(analyzer.config.fftSize, analyzer.config.hopSize)
        var samples = 0L
        val start = System.currentTimeMillis()
        graph.audioHub.chunks(graph.settings.current.audioInputId).collect { chunk ->
            chunk.error?.let { fail(it.message ?: "audio"); return@collect }
            if (chunk.sampleRate != analyzer.config.sampleRate) {
                // The input runs at another rate than asked: rebuild for it.
                analyzer.reconfigure(analyzer.config.copy(sampleRate = chunk.sampleRate))
                analyzer.setNoiseProfile(graph.settings.getVector(PROFILE_KEY))
                window = SlidingWindow(analyzer.config.fftSize, analyzer.config.hopSize)
            }
            window.push(chunk.samples) { w ->
                val t = start + (samples + chunk.samples.size) * 1000 / chunk.sampleRate
                val frame = analyzer.process(w, t)
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
        val profile = analyzer.finishCalibration() ?: return false
        graph.settings.putVector(PROFILE_KEY, profile)
        return true
    }

    fun clearCalibration() {
        analyzer.setNoiseProfile(null)
        graph.settings.putVector(PROFILE_KEY, null)
    }

    val hasCalibration: Boolean get() = graph.settings.getVector(PROFILE_KEY) != null

    private fun reading(f: AcousticFrame): SourceReading {
        val raw = buildMap {
            f.series?.let {
                put("f0Hz", it.fundamentalHz)
                put("partials", it.count.toDouble())
            }
            put("stability", f.stability.score * 100)
            put("levelDbfs", f.levelDbfs)
        }
        return SourceReading(id, f.score.toFloat(), f.confidence.toFloat(), f.timestampMs, raw, f)
    }

    companion object {
        const val PROFILE_KEY = "audio_profile"

        fun configFor(s: Settings, sampleRate: Int) = AcousticConfig(
            sampleRate = sampleRate,
            peakThresholdDb = s.peakThresholdDb.toDouble(),
            stabilityWindowMs = s.stabilityWindowS * 1000L,
        )
    }
}
