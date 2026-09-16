package us.liyifan.things.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import us.liyifan.things.data.repo.SyncState
import us.liyifan.things.data.repo.ThingsRepository
import us.liyifan.things.data.settings.Appearance
import us.liyifan.things.data.settings.SettingsStore
import us.liyifan.things.model.Item
import us.liyifan.things.model.Model
import us.liyifan.things.model.NewTaskInit
import us.liyifan.things.model.TaskPatch
import us.liyifan.things.model.buildModel
import us.liyifan.things.sync.UiActivity
import us.liyifan.things.ui.theme.ThingsMotion

/**
 * One view model over the whole app, the way the web client has one store.
 *
 * The built-in lists are pure functions of the snapshot, so a view model per screen would be
 * several objects re-deriving the same thing from the same source. What is genuinely per screen
 * — the Logbook's pages, the Trash — is loaded on demand and dropped when the screen goes.
 */
class AppViewModel(
    private val repository: ThingsRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val model: StateFlow<Model?> = repository.snapshot
        .map { snapshot -> snapshot?.let { buildModel(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val appearance: StateFlow<Appearance> = settings.appearance
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())

    val syncState: StateFlow<SyncState> = repository.syncState

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _logbook = MutableStateFlow<List<Item>>(emptyList())
    val logbook: StateFlow<List<Item>> = _logbook.asStateFlow()
    private val _logbookComplete = MutableStateFlow(false)
    val logbookComplete: StateFlow<Boolean> = _logbookComplete.asStateFlow()

    private val _trash = MutableStateFlow<List<Item>>(emptyList())
    val trash: StateFlow<List<Item>> = _trash.asStateFlow()

    private val _loggedByProject = MutableStateFlow<Map<String, List<Item>>>(emptyMap())
    val loggedByProject: StateFlow<Map<String, List<Item>>> = _loggedByProject.asStateFlow()

    init {
        viewModelScope.launch {
            repository.toasts.collect { message -> showToast(message) }
        }
        viewModelScope.launch {
            // When a provisional id becomes a real one, anything pointing at the old id has to
            // follow — including the card the user may still be typing into.
            repository.idRemaps.collect { (temp, real) ->
                _ui.update { state ->
                    state.copy(
                        selectedId = if (state.selectedId == temp) real else state.selectedId,
                        expandedId = if (state.expandedId == temp) real else state.expandedId,
                        settling = state.settling.map { if (it == temp) real else it }.toSet(),
                        // Kept so that a screen opened on a brand new project — whose route
                        // still names the provisional id — can follow it rather than going blank.
                        idMap = state.idMap + (temp to real),
                    )
                }
            }
        }
    }

    // -- selection and the open card ----------------------------------------------------------

    fun select(id: String?) = _ui.update { it.copy(selectedId = id) }

    fun expand(id: String?) = _ui.update {
        it.copy(expandedId = id, selectedId = id ?: it.selectedId)
    }

    fun setTagFilter(tagId: String?) = _ui.update { it.copy(tagFilter = tagId) }

    /** Leaving a list closes whatever was open in it, as the web client does. */
    fun onNavigated() {
        _ui.update { it.copy(expandedId = null, selectedId = null, tagFilter = null) }
        UiActivity.onNavigate()
        viewModelScope.launch { repository.applyStagedIfAny() }
    }

    fun showToast(message: String) {
        _ui.update { it.copy(toast = message) }
        viewModelScope.launch {
            delay(TOAST_MS)
            _ui.update { if (it.toast == message) it.copy(toast = null) else it }
        }
    }

    fun dismissToast() = _ui.update { it.copy(toast = null) }

    // -- reading ------------------------------------------------------------------------------

    fun refresh(sync: Boolean = true, force: Boolean = false) = viewModelScope.launch {
        repository.refresh(sync, force)
    }

    fun loadLogbook(more: Boolean = false) = viewModelScope.launch {
        val before = _logbook.value.size
        val items = repository.loadLogbook(more)
        _logbook.value = items
        // The page size is the server's; a short page means there is nothing more to ask for.
        _logbookComplete.value = items.size - before < LOGBOOK_PAGE
    }

    fun loadTrash() = viewModelScope.launch { _trash.value = repository.loadTrash() }

    fun loadLogged(projectId: String) = viewModelScope.launch {
        _loggedByProject.update { it + (projectId to repository.loadLogged(projectId)) }
    }

    // -- writing ------------------------------------------------------------------------------

    /** Creates a to-do and opens its card at once, under the provisional id. */
    fun createTask(init: NewTaskInit, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        val id = repository.createTask(init)
        expand(id)
        onCreated(id)
    }

    fun updateTask(id: String, patch: TaskPatch) = viewModelScope.launch {
        repository.updateTask(id, patch)
    }

    /**
     * Ticking a to-do.
     *
     * The row stays on screen, ticked, for a beat before it leaves. That pause is Things' own,
     * and it is doing work: it confirms the tap landed on the row the user meant, at a moment
     * when the row is about to disappear and take the evidence with it.
     */
    fun completeTask(id: String, done: Boolean) = viewModelScope.launch {
        // Recorded first, seen second. Queueing the write before the pause means killing the app
        // mid-pause cannot lose the tick; the row stays on screen because completing it marks it
        // rather than removing it.
        repository.completeTask(id, done)
        if (!done) {
            loadLogbook()
            return@launch
        }
        _ui.update { it.copy(settling = it.settling + id) }
        delay(ThingsMotion.SETTLE_MS)
        _ui.update {
            it.copy(
                settling = it.settling - id,
                expandedId = if (it.expandedId == id) null else it.expandedId,
            )
        }
        repository.forgetCompleted(resolve(id))
    }

    /** The real id of a row that was created here, if the server has since named it. */
    fun resolve(id: String): String = _ui.value.idMap[id] ?: id

    fun cancelTask(id: String) = viewModelScope.launch {
        closeIfOpen(id)
        repository.cancelTask(id)
    }

    fun trashTask(id: String) = viewModelScope.launch {
        closeIfOpen(id)
        repository.trashTask(id)
    }

    fun untrashTask(id: String) = viewModelScope.launch {
        _trash.update { list -> list.filterNot { it.id == id } }
        repository.untrashTask(id)
    }

    fun moveTask(id: String, to: String) = viewModelScope.launch { repository.moveTask(id, to) }

    fun addChecklistItem(taskId: String, title: String) = viewModelScope.launch {
        repository.addChecklistItem(taskId, title)
    }

    fun toggleChecklistItem(id: String, done: Boolean) = viewModelScope.launch {
        repository.toggleChecklistItem(id, done)
    }

    fun deleteChecklistItem(id: String) = viewModelScope.launch { repository.deleteChecklistItem(id) }

    fun createProject(title: String, areaId: String?, onCreated: (String) -> Unit) =
        viewModelScope.launch { onCreated(repository.createProject(title, areaId)) }

    fun createHeading(title: String, projectId: String) = viewModelScope.launch {
        repository.createHeading(title, projectId)
    }

    fun createArea(title: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(repository.createArea(title))
    }

    fun renameArea(id: String, title: String) = viewModelScope.launch { repository.renameArea(id, title) }

    fun deleteArea(id: String) = viewModelScope.launch { repository.deleteArea(id) }

    fun createTag(title: String, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        onCreated(repository.createTag(title))
    }

    fun resync() = viewModelScope.launch { repository.resync() }

    fun saveAppearance(appearance: Appearance) = viewModelScope.launch {
        settings.saveAppearance(appearance)
    }

    private fun closeIfOpen(id: String) = _ui.update {
        it.copy(
            expandedId = if (it.expandedId == id) null else it.expandedId,
            selectedId = if (it.selectedId == id) null else it.selectedId,
        )
    }

    companion object {
        const val LOGBOOK_PAGE = 100
        const val TOAST_MS = 3_500L

        fun factory(repository: ThingsRepository, settings: SettingsStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(repository, settings) as T
        }
    }
}
