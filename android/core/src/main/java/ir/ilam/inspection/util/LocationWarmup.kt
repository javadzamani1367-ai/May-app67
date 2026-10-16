package ir.ilam.inspection.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The phone's position, kept warm while a form is open.
 *
 * A satellite receiver needs ten to thirty seconds to lock on from cold; that
 * wait is what made the map feel slow, not the map. So the listening starts
 * the moment a form opens — long before anyone taps "location" — and by the
 * time the map is opened the fix is already there and already tight.
 *
 * Until the first live fix arrives, the last position the phone knew (from
 * Google's fused provider where it exists, and the platform's own) stands in,
 * marked as not live, so the map can open on the right street at once. It is
 * never recorded as the measured position.
 *
 * Only the last few seconds of fixes are averaged: the user walks from pole
 * to pole, and an average over a minute would put the point half way back.
 */
object LocationWarmup {

    /** The best estimate now: [live] false while it is only the phone's last known position. */
    data class Reading(
        val fix: Fix,
        val samples: Int,
        val receivedAt: Long,
        val live: Boolean,
        /** A satellite fix within [LocationRefiner.PRECISE_METERS]: good enough to record from. */
        val precise: Boolean = false
    ) {
        fun isFresh(now: Long = SystemClock.elapsedRealtime()): Boolean = live && now - receivedAt <= STALE_MILLIS
    }

    private val state = MutableStateFlow<Reading?>(null)
    val reading: StateFlow<Reading?> = state.asStateFlow()

    private val used = MutableStateFlow(0)
    /** Satellites the receiver is using for its fix right now; 0 under a roof. */
    val satellites: StateFlow<Int> = used.asStateFlow()
    private var gnss: Pair<LocationManager, GnssStatus.Callback>? = null
    private var assisted = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val window = ArrayDeque<Pair<Long, Fix>>()
    private var holders = 0
    private var listening: Job? = null
    private var stopping: Job? = null

    /** A screen that wants the position warm. Pair every call with [release]. */
    fun acquire(context: Context) {
        holders++
        stopping?.cancel()
        stopping = null
        ensureRunning(context.applicationContext)
    }

    /**
     * The screen is gone. Listening stops a little later, not at once: moving
     * from the form to one of its pages releases and acquires again, and a
     * receiver switched off for that instant would start cold.
     */
    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders > 0) return
        stopping?.cancel()
        stopping = scope.launch {
            delay(GRACE_MILLIS)
            listening?.cancel()
            listening = null
            window.clear()
            gnss?.let { (manager, callback) -> runCatching { manager.unregisterGnssStatusCallback(callback) } }
            gnss = null
            used.value = 0
            assisted = false
        }
    }

    /** Permission was just granted: start now rather than at the next screen. */
    fun permissionGranted(context: Context) {
        if (holders > 0) ensureRunning(context.applicationContext)
    }

    private fun ensureRunning(context: Context) {
        if (listening?.isActive == true || !hasPermission(context)) return
        seed(context)
        watchSatellites(context)
        val provider = LocationProvider(context)
        if (!assisted) {
            provider.assist()
            assisted = true
        }
        listening = scope.launch {
            runCatching {
                provider.fixes().collect { fix -> add(fix) }
            }
        }
    }

    private fun add(fix: Fix) {
        val now = SystemClock.elapsedRealtime()
        // The user walked on: a satellite fix clearly away from the current
        // estimate starts a new window, instead of the old spot holding the
        // dot back for the rest of the window.
        val current = state.value
        if (fix.gps && current != null && current.live &&
            LocationRefiner.distanceMeters(fix, current.fix) > maxOf(fix.accuracy, current.fix.accuracy) * MOVED_FACTOR
        ) {
            window.clear()
        }
        window.addLast(now to fix)
        while (window.isNotEmpty() && now - window.first().first > WINDOW_MILLIS) window.removeFirst()
        val refiner = LocationRefiner()
        window.forEach { refiner.add(it.second) }
        val estimate = refiner.estimate() ?: return
        state.value = Reading(estimate, refiner.count, now, live = true, precise = refiner.precise)
    }

    @SuppressLint("MissingPermission")
    private fun watchSatellites(context: Context) {
        if (gnss != null) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                used.value = (0 until status.satelliteCount).count { status.usedInFix(it) }
            }

            override fun onStopped() {
                used.value = 0
            }
        }
        val registered = runCatching {
            manager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
        }.getOrDefault(false)
        if (registered) gnss = manager to callback
    }

    /** The last known position, only while nothing live has come in. */
    @SuppressLint("MissingPermission")
    private fun seed(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (state.value?.isFresh(now) == true) return
        val provider = LocationProvider(context)
        provider.lastKnown()?.let { offer(it, now) }
        if (provider.hasGooglePlayServices()) {
            runCatching {
                LocationServices.getFusedLocationProviderClient(context).lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null && !MockLocation.isMock(location)) {
                            offer(Fix(location.latitude, location.longitude, location.accuracy.toDouble(), true), now)
                        }
                    }
            }
        }
    }

    private fun offer(fix: Fix, now: Long) {
        val current = state.value
        if (current?.isFresh(now) == true) return
        // A stale live reading gives way to any seed; one seed to a sharper one.
        if (current == null || current.live || fix.accuracy < current.fix.accuracy) {
            state.value = Reading(fix, 0, now, live = false)
        }
    }

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** A reading older than this is no longer where the user is. */
    const val STALE_MILLIS = 15_000L
    private const val WINDOW_MILLIS = 6_000L
    /** Farther than this many accuracies from the estimate, the user has moved. */
    private const val MOVED_FACTOR = 1.5
    private const val GRACE_MILLIS = 60_000L
}

/**
 * Keeps the receiver on while the app is on screen — from the moment it opens,
 * not from the moment a form does — and lets it go when the app goes to the
 * background, so it never drains the battery unseen. By the time the user
 * reaches a map, the satellites are already locked.
 */
@androidx.compose.runtime.Composable
fun KeepLocationWarm() {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.lifecycle.compose.LifecycleStartEffect(Unit) {
        LocationWarmup.acquire(context)
        onStopOrDispose { LocationWarmup.release() }
    }
}
