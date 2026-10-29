package ir.minerfinder.core.sensor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Convenience base for sources that run one coroutine: implement [run], call
 * [emit] for each reading and [fail] when the hardware goes away. A new source
 * (RTL-SDR, ESP32...) usually only needs this.
 */
abstract class BaseSource : SensorSource {
    private val _readings = MutableStateFlow<SourceReading?>(null)
    override val readings: StateFlow<SourceReading?> = _readings.asStateFlow()

    private val _status = MutableStateFlow(SourceStatus.IDLE)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Short reason for [SourceStatus.ERROR] or [SourceStatus.UNAVAILABLE], for the user. */
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null

    /** Produces readings until cancelled. */
    protected abstract suspend fun run()

    /** Called on stop, after the job is cancelled. */
    protected open fun onStopped() {}

    @Synchronized
    override fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        if (!isAvailable()) {
            _status.value = SourceStatus.UNAVAILABLE
            return
        }
        _error.value = null
        _status.value = SourceStatus.RUNNING
        job = scope.launch {
            try {
                run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
        _readings.value = null
        if (_status.value == SourceStatus.RUNNING) _status.value = SourceStatus.IDLE
        onStopped()
    }

    protected fun emit(reading: SourceReading) {
        _readings.value = reading
    }

    protected fun fail(message: String) {
        _error.value = message
        _status.value = SourceStatus.ERROR
    }
}
