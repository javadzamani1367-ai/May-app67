package ir.ilam.inspection.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Location without a hard dependency on Google. The fused provider is used
 * where Play Services exists; the platform's own GPS always listens as well,
 * because on the phones that ship without Google — common in this market — it
 * is the only answer, and where both exist the raw GNSS fix is often the
 * sharper of the two.
 */
class LocationProvider(private val context: Context) {

    fun hasGooglePlayServices(): Boolean = runCatching {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    /**
     * Every fix, from every source, for as long as it is collected.
     *
     * Collection is the switch: the receivers are released the moment the
     * collector stops, so a screen that is closed never leaves the GPS running
     * on a phone that has to last a whole day in the field.
     *
     * Fixes older than a few seconds are dropped. The fused provider likes to
     * answer at once with whatever it had cached, and a cached fix from the
     * road outside the village is exactly the twenty-metre error this exists
     * to get rid of.
     */
    @SuppressLint("MissingPermission")
    fun fixes(): Flow<Fix> = callbackFlow {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        fun fresh(location: Location): Boolean {
            val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            return ageNanos in 0..MAX_AGE_NANOS
        }

        // Every method overridden, not a lambda: before Android 11 the other
        // three were abstract in the platform's interface, and the platform
        // calling one of them on a listener that lacks it is a crash.
        val platformListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (fresh(location)) {
                    trySend(Fix(location.latitude, location.longitude, location.accuracy.toDouble(), false))
                }
            }

            @Deprecated("Required on Android 10 and older")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager?.isProviderEnabled(it) == true }.getOrDefault(false) }
        providers.forEach { provider ->
            runCatching {
                manager?.requestLocationUpdates(provider, INTERVAL_MILLIS, 0f, platformListener, Looper.getMainLooper())
            }
        }

        var fusedCallback: LocationCallback? = null
        if (hasGooglePlayServices()) {
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.filter(::fresh).forEach { location ->
                        trySend(Fix(location.latitude, location.longitude, location.accuracy.toDouble(), true))
                    }
                }
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS)
                .setMinUpdateIntervalMillis(INTERVAL_MILLIS / 2)
                .setWaitForAccurateLocation(true)
                .build()
            runCatching {
                LocationServices.getFusedLocationProviderClient(context)
                    .requestLocationUpdates(request, callback, Looper.getMainLooper())
                fusedCallback = callback
            }
        }

        if (providers.isEmpty() && fusedCallback == null) {
            // Location is switched off entirely. Closing tells the screen at
            // once, instead of leaving it waiting for a fix that cannot come.
            close()
        }

        awaitClose {
            runCatching { manager?.removeUpdates(platformListener) }
            fusedCallback?.let { callback ->
                runCatching {
                    LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(callback)
                }
            }
        }
    }

    /**
     * Where the phone was a moment ago, without waiting for anything. Only for
     * pointing the map somewhere sensible while a real fix is coming; never
     * recorded in a report.
     */
    @SuppressLint("MissingPermission")
    fun lastKnown(): Fix? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val now = System.currentTimeMillis()
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { now - it.time <= LAST_KNOWN_MAX_AGE_MILLIS }
            .minByOrNull { it.accuracy }
            ?.let { Fix(it.latitude, it.longitude, it.accuracy.toDouble(), fromGoogle = false) }
    }

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    private companion object {
        const val INTERVAL_MILLIS = 1_000L
        const val MAX_AGE_NANOS = 10_000_000_000L
        const val LAST_KNOWN_MAX_AGE_MILLIS = 10 * 60_000L
    }
}
