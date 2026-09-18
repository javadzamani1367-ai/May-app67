package ir.ilam.inspection.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.db.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The manager's register of who may use the app, and on which installation. */
class UsersViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.userRepository

    val users: StateFlow<List<UserEntity>> = repository.users
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    /** The row being edited, or a blank one when a new expert is registered. */
    private val _editing = MutableStateFlow<UserEntity?>(null)
    val editing: StateFlow<UserEntity?> = _editing.asStateFlow()

    fun startNew() {
        _editing.value = UserEntity(userCode = "", fullName = "")
    }

    fun edit(user: UserEntity) {
        _editing.value = user
    }

    fun change(user: UserEntity) {
        _editing.value = user
    }

    fun cancel() {
        _editing.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    /**
     * A user code identifies a person in every report they file, so a blank
     * one is refused outright rather than saved and noticed later.
     */
    fun save() {
        val user = _editing.value ?: return
        if (user.userCode.isBlank() || user.fullName.isBlank()) {
            _message.value = R.string.users_incomplete
            return
        }
        viewModelScope.launch {
            repository.save(user)
            _editing.value = null
            _message.value = R.string.users_saved
        }
    }

    fun delete(user: UserEntity) {
        viewModelScope.launch {
            repository.delete(user)
            _message.value = R.string.users_deleted
        }
    }
}
