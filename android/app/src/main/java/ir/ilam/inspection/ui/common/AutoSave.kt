package ir.ilam.inspection.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

private const val DEBOUNCE_MILLIS = 700L

/**
 * Writes a field back to the database shortly after typing stops, and again
 * when the step is left. The expert never has to remember to press save.
 */
@Composable
fun <T> AutoSave(value: T, onSave: (T) -> Unit) {
    val save by rememberUpdatedState(onSave)
    LaunchedEffect(value) {
        delay(DEBOUNCE_MILLIS)
        save(value)
    }
}
