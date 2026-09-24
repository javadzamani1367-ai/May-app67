package ir.roozban.feature.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * The quick-add sheet on its own, for entry points outside the main screen: the home-screen
 * widget, Share from other apps, the Quick Settings tile and the launcher shortcut.
 * [onDone] receives true when a task was saved.
 */
@Composable
fun StandaloneQuickAdd(
    initialText: String,
    onDone: (saved: Boolean) -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.setMode(ListMode.INBOX)
        if (initialText.isNotBlank()) viewModel.onQuickAddTextChange(initialText)
    }
    val quickAdd by viewModel.quickAdd.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    QuickAddSheet(
        state = quickAdd,
        onTextChange = viewModel::onQuickAddTextChange,
        onSubmit = { scope.launch { if (viewModel.submitQuickAddAndWait()) onDone(true) } },
        onDismiss = {
            viewModel.dismissQuickAdd()
            onDone(false)
        },
    )
}
