package ir.roozban.core.alarm

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import ir.roozban.core.domain.LearningRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The nightly statistics run. JobScheduler starts it about once a day, only while the phone is
 * charging and idle, so it never costs battery or slows the phone while in use.
 */
@AndroidEntryPoint
class LearningJobService : JobService() {
    @Inject lateinit var runner: LearningRunner

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                runner.run()
            } catch (e: Exception) {
                Log.w(TAG, "learning run failed", e)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.cancel()
        // Try again at the next idle window.
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RoozbanLearning"
        private const val JOB_ID = 6_001

        /** Idempotent; called at every app start. */
        fun ensureScheduled(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, LearningJobService::class.java))
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setPeriodic(TimeUnit.HOURS.toMillis(24))
                .setPersisted(true)
                .build()
            runCatching { scheduler.schedule(job) }.onFailure { Log.w(TAG, "could not schedule", it) }
        }
    }
}
