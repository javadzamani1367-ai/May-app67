package ir.minerfinder.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import ir.minerfinder.ui.common.PermissionAsker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Position from Android's own LocationManager: satellite first, network as a
 * fallback. No Google Play Services, so it works on any phone and offline.
 */
class LocationTracker(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()
    private var active = false

    private val listener = LocationListener { fix -> accept(fix) }

    val hasPermission: Boolean get() = PermissionAsker.isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start() {
        if (active || !hasPermission) return
        active = true
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) continue
            runCatching {
                manager.getLastKnownLocation(provider)?.let { accept(it) }
                manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!active) return
        active = false
        runCatching { manager.removeUpdates(listener) }
    }

    /** A satellite fix is kept over a network one unless it has gone stale. */
    private fun accept(fix: Location) {
        val current = _location.value
        val replace = current == null ||
            fix.provider == LocationManager.GPS_PROVIDER ||
            fix.time - current.time > STALE_MS ||
            fix.accuracy <= current.accuracy
        if (replace) _location.value = fix
    }

    /** The latest fix if it is fresh enough to log. */
    fun fresh(nowMs: Long = System.currentTimeMillis()): Location? =
        _location.value?.takeIf { nowMs - it.time <= STALE_MS }

    companion object {
        const val STALE_MS = 30_000L
    }
}
