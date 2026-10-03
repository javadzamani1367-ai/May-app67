package ir.ilam.inspection.field.thermal

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** What the route recorder is doing, for the screen and the notification. */
data class TrackState(
    val itemId: String? = null,
    val running: Boolean = false,
    val points: Int = 0,
    val accuracy: Double? = null,
    val lastFixAt: Long? = null,
    val startedAt: Long? = null,
    val clockSkewSeconds: Long? = null,
    /** The GPS receiver is switched off: nothing will be recorded until it is on. */
    val gpsOff: Boolean = false
)

/**
 * The one place the screen and the service meet. The service writes the
 * state; the screen reads it and asks for start and stop.
 */
object TrackRecorder {
    private val _state = MutableStateFlow(TrackState())
    val state: StateFlow<TrackState> = _state.asStateFlow()

    internal fun update(transform: (TrackState) -> TrackState) {
        _state.value = transform(_state.value)
    }

    fun start(context: Context, itemId: String) {
        val intent = Intent(context, TrackService::class.java).putExtra(TrackService.EXTRA_ITEM, itemId)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, TrackService::class.java).setAction(TrackService.ACTION_STOP))
    }

    /** The route of an item, one CSV file next to its other files. */
    fun file(fieldRoot: File, itemId: String): File = File(File(fieldRoot, itemId).apply { mkdirs() }, "track.csv")

    fun read(file: File): List<TrackPoint> =
        if (!file.exists()) emptyList() else file.readLines().mapNotNull { TrackPoint.parse(it) }
}
