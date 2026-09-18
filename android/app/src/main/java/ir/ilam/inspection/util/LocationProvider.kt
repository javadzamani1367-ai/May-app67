package ir.ilam.inspection.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** A single fix with the accuracy the report has to record, and its source. */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val fromGoogle: Boolean = true
)

/**
 * Location without a hard dependency on Google. The fused provider is used
 * where Play Services exists, because it is faster and more accurate; on the
 * phones that ship without it — common in this market — the platform's own
 * GPS and network providers answer instead, so a field expert is never left
 * unable to record a coordinate.
 */
class LocationProvider(private val context: Context) {

    fun hasGooglePlayServices(): Boolean = runCatching {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    suspend fun currentFix(): Fix? =
        (if (hasGooglePlayServices()) fusedFix() else null) ?: platformFix()

    @SuppressLint("MissingPermission")
    private suspend fun fusedFix(): Fix? = suspendCancellableCoroutine { continuation ->
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(TIMEOUT_MILLIS)
            .setMaxUpdateAgeMillis(MAX_AGE_MILLIS)
            .build()
        var resumed = false
        fun finish(fix: Fix?) {
            if (!resumed && continuation.isActive) {
                resumed = true
                continuation.resume(fix)
            }
        }
        runCatching {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    finish(location?.let { Fix(it.latitude, it.longitude, it.accuracy.toDouble()) })
                }
                .addOnFailureListener { finish(null) }
        }.onFailure { finish(null) }
    }

    /**
     * The platform path: the last known fix if it is recent, otherwise a single
     * update from whichever provider answers first.
     */
    @SuppressLint("MissingPermission")
    private suspend fun platformFix(): Fix? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        lastKnown(manager)?.let { return it }

        return withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                var resumed = false
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: android.location.Location) {
                        if (!resumed && continuation.isActive) {
                            resumed = true
                            // Stop the GPS the moment the fix arrives: leaving
                            // the listener attached would drain the battery of
                            // a phone that spends the whole day in the field.
                            runCatching { manager.removeUpdates(this) }
                            continuation.resume(
                                Fix(
                                    location.latitude,
                                    location.longitude,
                                    location.accuracy.toDouble(),
                                    fromGoogle = false
                                )
                            )
                        }
                    }
                }

                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
                if (providers.isEmpty()) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                providers.forEach { provider ->
                    runCatching {
                        manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    }
                }
                continuation.invokeOnCancellation {
                    runCatching { manager.removeUpdates(listener) }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Fix? {
        val now = System.currentTimeMillis()
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { now - it.time <= MAX_AGE_MILLIS }
            .maxByOrNull { it.time }
            ?.let { Fix(it.latitude, it.longitude, it.accuracy.toDouble(), fromGoogle = false) }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val MAX_AGE_MILLIS = 30_000L
    }
}
