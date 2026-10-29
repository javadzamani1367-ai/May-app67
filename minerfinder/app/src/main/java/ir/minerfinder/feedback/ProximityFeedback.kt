package ir.minerfinder.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import ir.minerfinder.AppGraph
import ir.minerfinder.core.feedback.ProximityMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Geiger-style clicks that speed up as the probability rises, and a short
 * vibration with each click above the alert threshold. Runs while a scan runs.
 */
class ProximityFeedback(private val graph: AppGraph) {
    private var job: Job? = null
    private var player: BeepPlayer? = null

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (graph.appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            graph.appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        player = BeepPlayer()
        job = graph.scope.launch {
            while (isActive) {
                val s = graph.settings.current
                val p = graph.scan.hub.detection.value.probability
                val interval = ProximityMapper.beepIntervalMs(p)
                if (interval == null || (!s.beep && !s.vibrate)) {
                    delay(250)
                    continue
                }
                if (s.beep) player?.beep()
                val buzz = ProximityMapper.vibrationMs(p, s.alertThreshold)
                if (s.vibrate && buzz > 0) vibrate(buzz)
                delay(interval)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        player?.release()
        player = null
    }

    private fun vibrate(ms: Long) {
        val v = vibrator ?: return
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
}
