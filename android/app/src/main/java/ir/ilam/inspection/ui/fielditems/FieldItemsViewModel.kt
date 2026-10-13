package ir.ilam.inspection.ui.fielditems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.FieldManage
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.ServerFiles
import ir.ilam.inspection.sync.fieldItem
import ir.ilam.inspection.sync.fieldItems
import ir.ilam.inspection.sync.moveFieldItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Why the screen has nothing fresh to show: no server, or the server said no (with its reason). */
sealed interface FieldProblem {
    data object Offline : FieldProblem
    data class Refused(val message: String) : FieldProblem
}

data class FieldListState(
    val filter: FieldManage.Filter = FieldManage.Filter(),
    val items: List<FieldManage.Row> = emptyList(),
    val counts: Map<Int, Int> = emptyMap(),
    val overdue: Int = 0,
    val more: Boolean = false,
    val page: Int = 0,
    val busy: Boolean = false,
    val loaded: Boolean = false,
    val problem: FieldProblem? = null
)

data class FieldDetailState(
    val detail: FieldManage.Detail? = null,
    val busy: Boolean = false,
    val problem: FieldProblem? = null,
    /** Set once a status change went through, so the screen can say so. */
    val saved: Boolean = false
)

/**
 * The field app's items, live from the server: they are the office's queue,
 * not this phone's, so nothing is kept here but a cache of the photos looked at.
 */
class FieldItemsViewModel(private val container: AppContainer) : ViewModel() {

    private val _list = MutableStateFlow(FieldListState())
    val list: StateFlow<FieldListState> = _list.asStateFlow()

    private val _detail = MutableStateFlow(FieldDetailState())
    val detail: StateFlow<FieldDetailState> = _detail.asStateFlow()

    private var loading: Job? = null

    fun setStatus(status: Int?) = refilter { it.copy(status = status, overdue = false) }
    fun setKind(kind: Int?) = refilter { it.copy(kind = kind) }
    fun showOverdue() = refilter { it.copy(status = null, overdue = true) }

    /** Typing searches after a short pause, not on every letter. */
    fun setQuery(query: String) {
        _list.update { it.copy(filter = it.filter.copy(query = query)) }
        loading?.cancel()
        loading = viewModelScope.launch {
            delay(400)
            load(page = 0)
        }
    }

    fun reload() {
        loading?.cancel()
        loading = viewModelScope.launch { load(page = 0) }
    }

    fun loadMore() {
        val state = _list.value
        if (state.busy || !state.more) return
        loading = viewModelScope.launch { load(page = state.page + 1) }
    }

    private fun refilter(change: (FieldManage.Filter) -> FieldManage.Filter) {
        _list.update { it.copy(filter = change(it.filter)) }
        reload()
    }

    private suspend fun load(page: Int) {
        val (api, token) = client() ?: return _list.update { it.copy(problem = FieldProblem.Offline, loaded = true) }
        _list.update { it.copy(busy = true) }
        when (val result = api.fieldItems(token, _list.value.filter, page)) {
            is ApiResult.Ok -> _list.update {
                it.copy(
                    items = if (page == 0) result.value.items else it.items + result.value.items,
                    counts = result.value.counts,
                    overdue = result.value.overdue,
                    more = result.value.more,
                    page = page,
                    busy = false,
                    loaded = true,
                    problem = null
                )
            }
            is ApiResult.Refused -> _list.update { it.copy(busy = false, loaded = true, problem = FieldProblem.Refused(result.message)) }
            ApiResult.Unreachable -> _list.update { it.copy(busy = false, loaded = true, problem = FieldProblem.Offline) }
        }
    }

    fun open(id: String) {
        if (_detail.value.detail?.row?.id != id) _detail.value = FieldDetailState()
        viewModelScope.launch {
            val (api, token) = client() ?: return@launch _detail.update { it.copy(problem = FieldProblem.Offline) }
            _detail.update { it.copy(busy = true, saved = false) }
            when (val result = api.fieldItem(token, id)) {
                is ApiResult.Ok -> _detail.update { it.copy(detail = result.value, busy = false, problem = null) }
                is ApiResult.Refused -> _detail.update { it.copy(busy = false, problem = FieldProblem.Refused(result.message)) }
                ApiResult.Unreachable -> _detail.update { it.copy(busy = false, problem = FieldProblem.Offline) }
            }
        }
    }

    /** One step of the item's cycle; the list reloads by itself when it is shown again. */
    fun move(id: String, status: Int, note: String, expertId: String?, dueAt: Long?, onDone: () -> Unit) {
        viewModelScope.launch {
            val (api, token) = client() ?: return@launch _detail.update { it.copy(problem = FieldProblem.Offline) }
            _detail.update { it.copy(busy = true, saved = false) }
            when (val result = api.moveFieldItem(token, id, status, note, expertId, dueAt)) {
                is ApiResult.Ok -> {
                    _detail.update { it.copy(detail = result.value, busy = false, problem = null, saved = true) }
                    onDone()
                }
                is ApiResult.Refused -> _detail.update { it.copy(busy = false, problem = FieldProblem.Refused(result.message)) }
                ApiResult.Unreachable -> _detail.update { it.copy(busy = false, problem = FieldProblem.Offline) }
            }
        }
    }

    /** A photo of an item, fetched once into the cache; null when it cannot be had. */
    suspend fun photo(photoId: String): File? {
        val target = File(container.cacheDir, "field-items/$photoId.jpg")
        if (target.exists() && target.length() > 0) return target
        val server = container.settingsRepository.current().syncTarget
        val token = container.vault.serverToken() ?: return null
        if (server.isBlank()) return null
        target.parentFile?.mkdirs()
        return if (ServerFiles(server, token).downloadRoute("manage/field/file&id=$photoId", target)) target else null
    }

    private suspend fun client(): Pair<ServerApi, String>? {
        val api = ServerApi(container.settingsRepository.current().syncTarget)
        val token = container.vault.serverToken() ?: return null
        return if (api.configured) api to token else null
    }
}
