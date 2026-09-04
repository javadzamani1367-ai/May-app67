package ir.ilam.inspection.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val DEBOUNCE_MILLIS = 700L

/**
 * Writes a field back to the database shortly after typing stops. The expert
 * never has to remember to press save, and an interrupted visit loses nothing.
 *
 * The first composition is not a save: opening a step would otherwise rewrite
 * the row and mark an untouched case as needing sync. Once anything has been
 * edited the field stays dirty, so typing a value and undoing it still saves.
 */
@Composable
fun <T> AutoSave(value: T, onSave: (T) -> Unit) {
    val save by rememberUpdatedState(onSave)
    val loaded = remember { value }
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!edited) {
            if (value == loaded) return@LaunchedEffect
            edited = true
        }
        delay(DEBOUNCE_MILLIS)
        save(value)
    }
}
