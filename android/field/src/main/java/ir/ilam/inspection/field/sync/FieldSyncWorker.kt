package ir.ilam.inspection.field.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.ilam.inspection.field.field
import java.util.concurrent.TimeUnit

/**
 * Sends the queue whenever there is a connection.
 *
 * Every item finished on the phone asks for a run; WorkManager holds it until
 * the network is back, then runs it, even if the app has been closed. A
 * periodic run behind it catches anything a missed trigger left, and brings
 * back the office's statuses. Field reports are worth mobile data: unlike the
 * inspection apps' six-hourly archive sync, this one does not wait for Wi-Fi.
 */
class FieldSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (applicationContext.field.sync.run()) {
        SyncOutcome.DONE, SyncOutcome.NOT_SIGNED_IN, SyncOutcome.SIGN_IN_NEEDED -> Result.success()
        SyncOutcome.OFFLINE -> Result.retry()
    }

    companion object {
        private const val NOW = "field-sync-now"
        private const val PERIODIC = "field-sync-periodic"

        private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** A run as soon as there is a connection — after finishing an item, or from the queue's button. */
        fun runSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<FieldSyncWorker>()
                .setConstraints(connected)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<FieldSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(connected)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
