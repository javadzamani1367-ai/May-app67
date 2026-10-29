package ir.minerfinder.scan

import ir.minerfinder.AppGraph
import ir.minerfinder.core.engine.DetectionEngine
import ir.minerfinder.core.engine.DetectionHub
import ir.minerfinder.sources.SourceIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Starts and stops the enabled sources and holds the fused detection. */
class ScanController(private val graph: AppGraph) {
    val hub = DetectionHub(DetectionEngine(), weights = ::weights)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    @Synchronized
    fun start() {
        val settings = graph.settings.current
        val sources = graph.catalog.all.filter { settings.isEnabled(it.id) }
        hub.attach(sources, graph.scope)
        _running.value = true
    }

    @Synchronized
    fun stop() {
        hub.detach()
        _running.value = false
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
