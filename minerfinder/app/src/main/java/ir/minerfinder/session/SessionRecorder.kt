package ir.minerfinder.session

import ir.minerfinder.AppGraph
import ir.minerfinder.data.BearingEntity
import ir.minerfinder.data.SessionEntity
import ir.minerfinder.data.TrackPointEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Every scan is a search session. While it runs, position and result are
 * logged every few seconds (the interval is a setting); a session that ends
 * with nothing logged is dropped rather than cluttering the list.
 */
class SessionRecorder(private val graph: AppGraph) {
    private val dao = graph.database.sessions()
    private var job: Job? = null

    private val _current = MutableStateFlow<Long?>(null)
    /** Id of the session being recorded, or null. */
    val current: StateFlow<Long?> = _current.asStateFlow()

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        graph.locations.start()
        job = graph.scope.launch {
            val id = dao.insert(SessionEntity(startedAt = System.currentTimeMillis()))
            _current.value = id
            while (isActive) {
                delay(graph.settings.current.logIntervalS * 1000L)
                logPoint(id)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        graph.locations.stop()
        val id = _current.value ?: return
        _current.value = null
        graph.scope.launch {
            if (dao.pointCount(id) == 0 && dao.bearingCount(id) == 0) dao.delete(id) else dao.end(id, System.currentTimeMillis())
        }
    }

    private suspend fun logPoint(sessionId: Long) {
        val fix = graph.locations.fresh() ?: return
        val detection = graph.scan.hub.detection.value
        val sources = JSONObject()
        detection.contributions.forEach { sources.put(it.sourceId, it.score.toDouble()) }
        dao.insertPoint(
            TrackPointEntity(
                sessionId = sessionId,
                time = System.currentTimeMillis(),
                lat = fix.latitude,
                lon = fix.longitude,
                accuracyM = fix.accuracy,
                probability = detection.probability,
                confidence = detection.confidence,
                sourcesJson = sources.toString(),
            ),
        )
    }

    /**
     * Stores a sweep's direction at the current position. Returns false when
     * there is no session running or no fresh position.
     */
    suspend fun saveBearing(bearingDeg: Double, sharpness: Double, peakScore: Double): Boolean {
        val id = _current.value ?: return false
        val fix = graph.locations.fresh() ?: return false
        dao.insertBearing(
            BearingEntity(
                sessionId = id, time = System.currentTimeMillis(), lat = fix.latitude, lon = fix.longitude,
                bearingDeg = bearingDeg, sharpness = sharpness, peakScore = peakScore,
            ),
        )
        return true
    }
}
