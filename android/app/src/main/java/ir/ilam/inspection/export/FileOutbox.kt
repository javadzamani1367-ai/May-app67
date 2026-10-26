package ir.ilam.inspection.export

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * A file the app has just built (Excel, Word, PDF, package) waiting for the user
 * to choose what to do with it: keep it on the phone, or send it through any
 * messenger. View models offer files here instead of opening the share sheet
 * themselves; [ir.ilam.inspection.ui.FileReadyHost] asks the question.
 */
object FileOutbox {
    private val _ready = MutableStateFlow<File?>(null)
    val ready: StateFlow<File?> = _ready.asStateFlow()

    fun offer(file: File) {
        _ready.value = file
    }

    fun done() {
        _ready.value = null
    }
}
