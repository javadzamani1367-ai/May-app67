package ir.minerfinder.sources

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import ir.minerfinder.AppGraph
import ir.minerfinder.R
import ir.minerfinder.core.magnetic.MagneticAnalyzer
import ir.minerfinder.core.magnetic.MagneticFrame
import ir.minerfinder.core.sensor.BaseSource
import ir.minerfinder.core.sensor.SourceReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The phone's own magnetometer, as fast as the phone allows. */
class MagnetometerSource(private val graph: AppGraph) : BaseSource() {
    override val id = SourceIds.MAGNETOMETER
    override val label: String = graph.appContext.getString(R.string.source_magnetometer)

    private val manager = graph.appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    val analyzer = MagneticAnalyzer()

    private val _frames = MutableStateFlow<MagneticFrame?>(null)
    val frames: StateFlow<MagneticFrame?> = _frames.asStateFlow()

    override fun isAvailable() = sensor != null

    override suspend fun run() {
        val sensor = sensor ?: return
        analyzer.setBaseline(graph.settings.getVector(BASELINE_KEY))
        val thread = HandlerThread("magnetometer").apply { start() }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                analyzer.add(event.timestamp, event.values[0].toDouble(), event.values[1].toDouble(), event.values[2].toDouble())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        // SENSOR_DELAY_FASTEST; above 200 Hz Android 12+ needs HIGH_SAMPLING_RATE_SENSORS (declared).
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST, Handler(thread.looper))
        try {
            while (true) {
                delay(250)
                val frame = analyzer.frame(System.currentTimeMillis()) ?: continue
                _frames.value = frame
                emit(reading(frame))
            }
        } finally {
            manager.unregisterListener(listener)
            thread.quitSafely()
        }
    }

    override fun onStopped() {
        _frames.value = null
    }

    fun startCalibration() = analyzer.startCalibration()

    fun finishCalibration(): Boolean {
        val baseline = analyzer.finishCalibration() ?: return false
        graph.settings.putVector(BASELINE_KEY, baseline)
        return true
    }

    fun clearCalibration() {
        analyzer.setBaseline(null)
        graph.settings.putVector(BASELINE_KEY, null)
    }

    val hasCalibration: Boolean get() = graph.settings.getVector(BASELINE_KEY) != null

    private fun reading(f: MagneticFrame) = SourceReading(
        sourceId = id,
        score = f.score.toFloat(),
        confidence = f.confidence.toFloat(),
        timestampMs = f.timestampMs,
        raw = buildMap {
            put("magnitudeUt", f.magnitude)
            put("deltaUt", f.deltaUt)
            put("rateHz", f.rateHz)
            f.ac50Ut?.let { put("ac50Ut", it) }
        },
        detail = f,
    )

    companion object {
        const val BASELINE_KEY = "mag_baseline"
    }
}
