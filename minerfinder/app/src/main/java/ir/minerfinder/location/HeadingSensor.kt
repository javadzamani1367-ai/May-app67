package ir.minerfinder.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Compass heading from the Rotation Vector sensor (fused gyroscope, accelerometer
 * and magnetometer: steady while the user turns). Corrected from magnetic to
 * true north when a position is known, so bearings line up with the map.
 */
class HeadingSensor(context: Context, private val locations: LocationTracker) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val _heading = MutableStateFlow<Double?>(null)
    /** Degrees clockwise from true north, 0..360. */
    val heading: StateFlow<Double?> = _heading.asStateFlow()
    private var users = 0
    private val matrix = FloatArray(9)
    private val angles = FloatArray(3)

    val isAvailable: Boolean get() = sensor != null

    @Synchronized
    fun acquire() {
        if (users++ == 0 && sensor != null) manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    @Synchronized
    fun release() {
        if (users > 0 && --users == 0) {
            manager.unregisterListener(this)
            _heading.value = null
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        SensorManager.getOrientation(matrix, angles)
        var deg = Math.toDegrees(angles[0].toDouble())
        locations.location.value?.let { fix ->
            deg += GeomagneticField(fix.latitude.toFloat(), fix.longitude.toFloat(), fix.altitude.toFloat(), fix.time).declination
        }
        _heading.value = ((deg % 360) + 360) % 360
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
