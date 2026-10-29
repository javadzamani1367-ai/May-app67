package ir.minerfinder.scan

import ir.minerfinder.AppGraph
import ir.minerfinder.core.engine.DetectionEngine
import ir.minerfinder.core.engine.DetectionHub
import ir.minerfinder.feedback.Alerts
import ir.minerfinder.feedback.ProximityFeedback
import ir.minerfinder.service.ScanService
import ir.minerfinder.sources.SourceIds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Starts and stops the enabled sources and holds the fused detection; with
 * them the beeps, the threshold alert and the foreground service.
 */
class ScanController(private val graph: AppGraph) {
    val hub = DetectionHub(DetectionEngine(), weights = ::weights)
    val alerts = Alerts(graph.appContext)
    private val feedback = ProximityFeedback(graph)
    private var alertJob: Job? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    @Synchronized
    fun start() {
        val settings = graph.settings.current
        val sources = graph.catalog.all.filter { settings.isEnabled(it.id) }
        hub.attach(sources, graph.scope)
        feedback.start()
        alerts.reset()
        alertJob?.cancel()
        alertJob = graph.scope.launch {
            hub.detection.collect { d ->
                alerts.onProbability(d.probability, graph.settings.current.alertThreshold, d.timestampMs)
            }
        }
        if (!_running.value) ScanService.start(graph.appContext)
        _running.value = true
    }

    @Synchronized
    fun stop() {
        alertJob?.cancel()
        alertJob = null
        feedback.stop()
        hub.detach()
        _running.value = false
        ScanService.stop(graph.appContext)
    }

    /** Restarts with the current settings, if running. */
    fun restart() {
        if (_running.value) start()
    }

    private fun weights(): Map<String, Float> {
        val s = graph.settings.current
        return graph.catalog.all.associate { it.id to s.weight(it.id, SourceIds.defaultWeight(it.id)) }
    }
}
