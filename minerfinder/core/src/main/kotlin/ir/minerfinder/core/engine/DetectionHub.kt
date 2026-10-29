package ir.minerfinder.core.engine

import ir.minerfinder.core.sensor.SensorSource
import ir.minerfinder.core.sensor.SourceReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs a set of sources and keeps [detection] up to date.
 *
 * Recomputes on every new reading and once a second besides, so a source that
 * goes quiet drops out after the engine's stale time even if nothing else
 * reports.
 */
class DetectionHub(
    private val engine: DetectionEngine = DetectionEngine(),
    private val weights: () -> Map<String, Float>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val latest = ConcurrentHashMap<String, SourceReading>()
    private val _detection = MutableStateFlow(Detection.EMPTY)
    val detection: StateFlow<Detection> = _detection.asStateFlow()

    private val _active = MutableStateFlow<List<SensorSource>>(emptyList())
    val active: StateFlow<List<SensorSource>> = _active.asStateFlow()

    private val jobs = mutableListOf<Job>()

    @Synchronized
    fun attach(sources: List<SensorSource>, scope: CoroutineScope) {
        detach()
        _active.value = sources
        for (source in sources) {
            source.start(scope)
            jobs += scope.launch {
                source.readings.filterNotNull().collect { reading ->
                    latest[source.id] = reading
                    recompute()
                }
            }
        }
        jobs += scope.launch {
            while (isActive) {
                delay(1_000)
                recompute()
            }
        }
    }

    @Synchronized
    fun detach() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        _active.value.forEach { it.stop() }
        _active.value = emptyList()
        latest.clear()
        _detection.value = Detection.EMPTY
    }

    fun recompute() {
        _detection.value = engine.fuse(latest.values.toList(), weights(), clock())
    }
}
