package ir.ilam.inspection.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.ilam.inspection.container
import java.util.concurrent.TimeUnit

/**
 * The sync that happens without anyone asking for it.
 *
 * An expert finishing a visit in a village has no signal; by the time they do,
 * they are thinking about the next case, not about a button in settings. A
 * case that sits unsent is a case the office cannot see, and the whole reason
 * the server exists is that the manager should not have to wait for someone to
 * drive back to the office.
 *
 * **Only on an unmetered network.** The data on an expert's own SIM is theirs,
 * and a case with three photographs and a ninety second video is not a small
 * upload. Wi-Fi only is the difference between a helpful background task and
 * one that quietly spends someone's money.
 */
class ServerSyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val settings = container.settingsRepository.current()
        // Checked here as well as at scheduling time: a job already queued
        // when the switch was turned off would otherwise run once more.
        if (!settings.autoSync || settings.syncTarget.isBlank()) {
            return Result.success()
        }

        val outcome = container.serverCaseSync.run()
        return when {
            // Reached and finished: done, whatever the numbers were.
            outcome.reachedServer && outcome.failed == 0 -> Result.success()
            // Did not arrive. WorkManager backs off and tries again; there is
            // no point counting this as a failure the user should see.
            outcome.offline -> Result.retry()
            else -> Result.retry()
        }
    }

    companion object {
        private const val NAME = "server-case-sync"

        /**
         * Six hours, not one. A case is not urgent to the hour, the work only
         * runs on Wi-Fi anyway, and a frequent periodic job on a phone that is
         * mostly off Wi-Fi is spent battery for nothing.
         */
        private const val HOURS = 6L

        /**
         * Registered on every start. [ExistingPeriodicWorkPolicy.KEEP] means an
         * already scheduled job keeps its place in the queue rather than being
         * pushed back to the start of a fresh interval each time the app opens.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServerSyncWorker>(HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
