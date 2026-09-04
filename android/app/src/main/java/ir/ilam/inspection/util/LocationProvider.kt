package ir.ilam.inspection.util

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** A single GPS fix with the accuracy the report has to record. */
data class Fix(val latitude: Double, val longitude: Double, val accuracy: Double)

/**
 * Fused location, high accuracy, no network dependency. The caller is
 * responsible for having the runtime permission before asking.
 */
class LocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun currentFix(): Fix? = suspendCancellableCoroutine { continuation ->
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(TIMEOUT_MILLIS)
            .setMaxUpdateAgeMillis(MAX_AGE_MILLIS)
            .build()
        runCatching {
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    continuation.resume(
                        location?.let { Fix(it.latitude, it.longitude, it.accuracy.toDouble()) }
                    )
                }
                .addOnFailureListener { continuation.resume(null) }
        }.onFailure { continuation.resume(null) }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val MAX_AGE_MILLIS = 30_000L
    }
}
