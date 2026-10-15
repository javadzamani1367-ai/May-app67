package ir.ilam.inspection.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.core.R

/**
 * Leaving a screen where something was entered asks first — by the back
 * arrow or by the phone's own back button. [dirty] says whether there is
 * anything to lose; nothing entered, nothing asked. Returns the action the
 * screen's back arrow should call.
 */
@Composable
fun rememberLeaveGuard(
    dirty: Boolean,
    onLeave: () -> Unit,
    message: String = stringResource(R.string.leave_message)
): () -> Unit {
    var asking by rememberSaveable { mutableStateOf(false) }
    val request = { if (dirty) asking = true else onLeave() }
    BackHandler(onBack = request)
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.leave_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { asking = false; onLeave() }) {
                    Text(stringResource(R.string.leave_yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.leave_no)) } }
        )
    }
    return request
}
