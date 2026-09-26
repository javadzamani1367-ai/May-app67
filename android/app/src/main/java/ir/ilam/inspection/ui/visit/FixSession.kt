package ir.ilam.inspection.ui.visit

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.LocationProvider
import ir.ilam.inspection.util.LocationRefiner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How a capture ended, for the message under the card. */
sealed class FixOutcome {
    data class Recorded(val fix: Fix) : FixOutcome()

    /** Time ran out before the target; the best there was was recorded anyway. */
    data class Coarse(val fix: Fix) : FixOutcome()
    data object NoFix : FixOutcome()
    data object LocationOff : FixOutcome()
}

/**
 * One precise capture: listen to every receiver, keep refining, and record the
 * refined point when it settles, when time runs out, or when the expert taps
 * "record this" because what they see is good enough.
 *
 * Lives as long as the card that owns it. Leaving the screen cancels the
 * listening, and with it the GPS.
 */
@Stable
class FixSession(
    private val provider: LocationProvider,
    private val scope: CoroutineScope,
    private val onRecord: (Fix, Int) -> Unit
) {
    var running by mutableStateOf(false)
        private set
    var estimate by mutableStateOf<Fix?>(null)
        private set
    var samples by mutableIntStateOf(0)
        private set
    var seconds by mutableIntStateOf(0)
        private set
    var outcome by mutableStateOf<FixOutcome?>(null)
        private set

    private var job: Job? = null

    fun start() {
        if (running) return
        running = true
        estimate = null
        samples = 0
        seconds = 0
        outcome = null
        if (!provider.isLocationEnabled()) {
            running = false
            outcome = FixOutcome.LocationOff
            return
        }
        job = scope.launch {
            val refiner = LocationRefiner()
            val started = SystemClock.elapsedRealtime()
            val clock = launch {
                while (true) {
                    delay(TICK_MILLIS)
                    seconds = ((SystemClock.elapsedRealtime() - started) / 1000).toInt()
                }
            }
            // Cancellation is rethrown, not swallowed: "record this now" cancels
            // this job after recording, and a swallowed cancellation would
            // carry on and record a second time.
            val settled = try {
                withTimeoutOrNull(MAX_MILLIS) {
                    provider.fixes()
                        .onEach { fix ->
                            refiner.add(fix)
                            estimate = refiner.estimate()
                            samples = refiner.count
                        }
                        .first { refiner.settled }
                }
            } catch (cancelled: CancellationException) {
                clock.cancel()
                throw cancelled
            } catch (ended: Exception) {
                // The receivers went away — location switched off mid-capture.
                null
            }
            clock.cancel()
            val best = refiner.estimate()
            when {
                best == null -> finish(FixOutcome.NoFix)
                settled != null -> record(best, refiner.count, FixOutcome.Recorded(best))
                else -> record(best, refiner.count, FixOutcome.Coarse(best))
            }
        }
    }

    /** "Good enough": record what is on screen now and stop listening. */
    fun acceptNow() {
        val current = estimate ?: return
        val count = samples
        job?.cancel()
        record(current, count, FixOutcome.Recorded(current))
    }

    fun stop() {
        job?.cancel()
        running = false
    }

    private fun record(fix: Fix, count: Int, result: FixOutcome) {
        onRecord(fix, count)
        finish(result)
    }

    private fun finish(result: FixOutcome) {
        running = false
        outcome = result
    }

    private companion object {
        const val MAX_MILLIS = 45_000L
        const val TICK_MILLIS = 1_000L
    }
}

@Composable
fun rememberFixSession(onRecord: (Fix, Int) -> Unit): FixSession {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { FixSession(LocationProvider(context), scope, onRecord) }
}
