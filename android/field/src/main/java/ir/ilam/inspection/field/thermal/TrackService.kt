package ir.ilam.inspection.field.thermal

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FieldDrafts
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.ui.MainActivity
import ir.ilam.inspection.util.PersianNumbers
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Records the route while the user works with the thermal camera.
 *
 * A foreground service, so the phone keeps it alive with the screen off and
 * the HIKMICRO app in front; the notification that has to come with it says
 * how many points are in and how precise the last one was. GPS only, from the
 * platform's own LocationManager — no Google services — about once a second.
 *
 * Every point is appended to a CSV file and flushed at once, so a killed
 * process loses at most the point in flight. If the system restarts the
 * service, it carries on with the same item.
 */
class TrackService : Service() {

    private var writer: BufferedWriter? = null
    private var itemId: String? = null
    private var points = 0
    private var skews = mutableListOf<Long>()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = record(location)
        override fun onProviderEnabled(provider: String) = TrackRecorder.update { it.copy(gpsOff = false) }
        override fun onProviderDisabled(provider: String) = TrackRecorder.update { it.copy(gpsOff = true) }
        @Deprecated("Required on API < 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = applicationContext.field.prefs
        if (intent?.action == ACTION_STOP) {
            finish()
            prefs.activeTrackItem = null
            stopSelf()
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_ITEM) ?: prefs.activeTrackItem
        val permitted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (id == null || !permitted) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        // A restart by the system while the app is in the background may be
        // refused on newer Android (no foreground start, no location while
        // unseen). The route then waits for the screen to start it again
        // instead of taking the app down with it.
        val started = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (itemId != id) begin(id)
        prefs.activeTrackItem = id
        return START_STICKY
    }

    @Suppress("MissingPermission")
    private fun begin(id: String) {
        finish()
        itemId = id
        val file = TrackRecorder.file(applicationContext.field.files.resolve(FieldDrafts.FOLDER), id)
        val fresh = !file.exists()
        points = TrackRecorder.read(file).size
        writer = BufferedWriter(FileWriter(file, true)).also { if (fresh) { it.write(TrackPoint.HEADER); it.newLine(); it.flush() } }
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val gpsOn = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        TrackRecorder.update {
            TrackState(itemId = id, running = true, points = points, startedAt = it.startedAt ?: System.currentTimeMillis(), gpsOff = !gpsOn)
        }
        runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, listener, Looper.getMainLooper()) }
    }

    private fun record(location: Location) {
        val now = System.currentTimeMillis()
        val point = TrackPoint(now, location.time, location.latitude, location.longitude,
            location.accuracy.toDouble(), if (location.hasAltitude()) location.altitude else null)
        runCatching { writer?.apply { write(point.toLine()); newLine(); flush() } }
        points++
        skews += now - location.time
        if (skews.size > MAX_SKEWS) skews.removeAt(0)
        val skew = skews.sorted()[skews.size / 2] / 1000
        TrackRecorder.update { it.copy(points = points, accuracy = point.accuracy, lastFixAt = now, clockSkewSeconds = skew, gpsOff = false) }
        if (points % NOTIFY_EVERY == 1) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(point.accuracy))
        }
    }

    private fun finish() {
        runCatching { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(listener) }
        runCatching { writer?.close() }
        writer = null
        itemId = null
        TrackRecorder.update { it.copy(running = false) }
    }

    override fun onDestroy() {
        finish()
        super.onDestroy()
    }

    private fun notification(accuracy: Double? = null): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = getString(R.string.track_notification_text, PersianNumbers.toPersian(points),
            accuracy?.let { PersianNumbers.toPersian(it.toInt()) } ?: "—")
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.track_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.track_channel), NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val EXTRA_ITEM = "item_id"
        const val ACTION_STOP = "ir.ilam.inspection.field.STOP_TRACK"
        private const val CHANNEL = "track"
        private const val NOTIFICATION_ID = 7001
        private const val INTERVAL_MS = 1_000L
        private const val NOTIFY_EVERY = 5
        private const val MAX_SKEWS = 61
    }
}
