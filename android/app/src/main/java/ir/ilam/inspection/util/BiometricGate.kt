package ir.ilam.inspection.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi

/**
 * Fingerprint unlock through the platform's own prompt.
 *
 * Deliberately not androidx.biometric: that requires a FragmentActivity host,
 * and FragmentActivity rejects the request codes the AndroidX result registry
 * generates, which crashed every file picker, barcode scan and permission
 * dialog in the app. The PIN is the real gate; this is the shortcut.
 */
object BiometricGate {

    fun isAvailable(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            val manager = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            @Suppress("DEPRECATION")
            manager?.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        // Below API 28 the platform prompt does not exist; the PIN carries it.
        else -> false
    }

    fun prompt(activity: Activity, title: String, cancelLabel: String, onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !isAvailable(activity)) return
        runCatching { show(activity, title, cancelLabel, onSuccess) }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun show(activity: Activity, title: String, cancelLabel: String, onSuccess: () -> Unit) {
        val executor = activity.mainExecutor
        android.hardware.biometrics.BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setNegativeButton(cancelLabel, executor) { _, _ -> }
            .build()
            .authenticate(
                CancellationSignal(),
                executor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult
                    ) {
                        onSuccess()
                    }
                }
            )
    }
}
