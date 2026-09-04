package ir.ilam.inspection.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ir.ilam.inspection.data.AppContainer

/**
 * Bridges the hand-rolled container to Compose's `viewModel()` without pulling
 * in a DI framework.
 */
class ContainerViewModelFactory(
    private val container: AppContainer,
    private val build: (AppContainer) -> ViewModel
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = build(container) as T
}
