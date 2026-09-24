package ir.roozban.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.domain.ProjectRepository
import ir.roozban.core.domain.ProjectUseCases
import ir.roozban.core.model.Project
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectItem(val project: Project, val openCount: String)

data class ProjectsUiState(
    val loading: Boolean = true,
    val active: List<ProjectItem> = emptyList(),
    val archived: List<ProjectItem> = emptyList(),
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    repository: ProjectRepository,
    private val useCases: ProjectUseCases,
) : ViewModel() {

    val state: StateFlow<ProjectsUiState> = combine(repository.observeProjects(), repository.observeOpenCounts()) { projects, counts ->
        val items = projects.map { ProjectItem(it, PersianDigits.format(counts[it.id] ?: 0)) }
        ProjectsUiState(
            loading = false,
            active = items.filter { !it.project.archived },
            archived = items.filter { it.project.archived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    fun create(name: String, color: Int) {
        viewModelScope.launch { useCases.create(name, color) }
    }

    fun update(project: Project, name: String, color: Int) {
        if (name.isBlank()) return
        viewModelScope.launch { useCases.update(project.copy(name = name.trim(), color = color)) }
    }

    fun setArchived(project: Project, archived: Boolean) {
        viewModelScope.launch { useCases.setArchived(project, archived) }
    }

    fun delete(project: Project) {
        viewModelScope.launch { useCases.delete(project) }
    }
}
