package us.liyifan.things.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import us.liyifan.things.model.Item
import us.liyifan.things.model.NewTaskInit
import us.liyifan.things.model.Snapshot
import us.liyifan.things.model.TaskPatch

/**
 * Everything the app can ask of its data, whatever is behind it.
 *
 * Today the only implementation talks to the self-hosted things-cloud service, which owns the
 * conversation with Things Cloud. The interface exists so that could change — a client that
 * speaks the Things Cloud protocol itself would fit here — without the screens noticing.
 *
 * Every mutation returns as soon as the local copy is updated. The write itself is queued and
 * sent in the background, which is what makes the app usable on a phone that is often holding
 * nothing but a weak signal.
 */
interface ThingsRepository {

    val snapshot: Flow<Snapshot?>
    val syncState: StateFlow<SyncState>

    /** Messages worth showing the user once: a refused write, a server that said no. */
    val toasts: SharedFlow<String>

    /** tempId to realId, so an open card can follow the to-do it is editing. */
    val idRemaps: SharedFlow<Pair<String, String>>

    /**
     * Fetches from the server. [sync] true makes the backend pull from Things Cloud first, which
     * is the slow path; false reads its mirror as it stands. [force] applies the result even if
     * the reader is mid-page, which the quiet-screen rule would otherwise defer.
     */
    suspend fun refresh(sync: Boolean = true, force: Boolean = false): RefreshResult

    /** Applies a snapshot that was held back while the user was reading. */
    suspend fun applyStagedIfAny()

    /** Throws away the local copy, un-sent writes included, and fetches everything again. */
    suspend fun resync()

    suspend fun loadLogbook(more: Boolean = false): List<Item>
    suspend fun loadLogged(projectId: String): List<Item>
    suspend fun loadTrash(): List<Item>

    /** Returns the provisional id the row has until the server answers. */
    suspend fun createTask(init: NewTaskInit): String
    suspend fun updateTask(id: String, patch: TaskPatch)
    suspend fun completeTask(id: String, done: Boolean)

    /**
     * Removes a row ticked a moment ago. The write went out at the tick; this is only the end of
     * the pause that lets the user see which row they hit.
     */
    suspend fun forgetCompleted(id: String)
    suspend fun cancelTask(id: String)
    suspend fun trashTask(id: String)
    suspend fun untrashTask(id: String)
    suspend fun moveTask(id: String, to: String)

    suspend fun addChecklistItem(taskId: String, title: String): String
    suspend fun toggleChecklistItem(id: String, done: Boolean)
    suspend fun deleteChecklistItem(id: String)

    suspend fun createProject(title: String, areaId: String?): String
    suspend fun createHeading(title: String, projectId: String): String
    suspend fun createArea(title: String): String
    suspend fun renameArea(id: String, title: String)
    suspend fun deleteArea(id: String)
    suspend fun createTag(title: String): String
}

data class SyncState(
    val refreshing: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val pendingWrites: Int = 0,
    /** A newer snapshot is in hand but has not been drawn, so as not to redraw under a reader. */
    val stagedAvailable: Boolean = false,
    val lastError: String? = null,
)

sealed interface RefreshResult {
    data object Applied : RefreshResult

    /** Fetched, but held back until the user next does something. */
    data object Staged : RefreshResult

    /** Skipped: un-sent writes would have been overwritten by the server's older answer. */
    data object SkippedPendingWrites : RefreshResult

    data class Failed(val message: String) : RefreshResult
}
