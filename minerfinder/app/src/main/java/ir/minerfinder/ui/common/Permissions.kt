package ir.minerfinder.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.minerfinder.R

/**
 * Asks for permissions the polite way: first a Persian explanation of why
 * each one is needed, then the system dialog. [ask] reports which of the
 * requested permissions are granted afterwards.
 */
class PermissionAsker internal constructor(private val context: Context) {
    internal class Ask(val missing: List<String>, val onDone: (Set<String>) -> Unit, val all: List<String>)

    internal var pending by mutableStateOf<Ask?>(null)
    internal var inFlight: Ask? = null

    fun ask(permissions: List<String>, onDone: (granted: Set<String>) -> Unit) {
        val missing = permissions.filterNot { isGranted(context, it) }
        if (missing.isEmpty()) onDone(permissions.toSet()) else pending = Ask(missing, onDone, permissions)
    }

    internal fun granted(all: List<String>) = all.filter { isGranted(context, it) }.toSet()

    companion object {
        fun isGranted(context: Context, permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        fun rationale(permission: String): Int? = when (permission) {
            Manifest.permission.RECORD_AUDIO -> R.string.permission_microphone
            Manifest.permission.ACCESS_FINE_LOCATION -> R.string.permission_location
            Manifest.permission.ACCESS_COARSE_LOCATION -> null
            "android.permission.POST_NOTIFICATIONS" -> R.string.permission_notifications
            else -> null
        }
    }
}

@Composable
fun rememberPermissionAsker(): PermissionAsker {
    val context = LocalContext.current
    val asker = remember { PermissionAsker(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val ask = asker.inFlight
        asker.inFlight = null
        ask?.onDone?.invoke(asker.granted(ask.all))
    }
    asker.pending?.let { ask ->
        val reasons = ask.missing.mapNotNull { PermissionAsker.rationale(it) }.distinct()
        AlertDialog(
            onDismissRequest = {
                asker.pending = null
                ask.onDone(asker.granted(ask.all))
            },
            title = { Text(stringResource(R.string.permission_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    reasons.forEach { Text(stringResource(it)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    asker.pending = null
                    asker.inFlight = ask
                    launcher.launch(ask.missing.toTypedArray())
                }) { Text(stringResource(R.string.permission_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    asker.pending = null
                    ask.onDone(asker.granted(ask.all))
                }) { Text(stringResource(R.string.action_not_now)) }
            },
        )
    }
    return asker
}
