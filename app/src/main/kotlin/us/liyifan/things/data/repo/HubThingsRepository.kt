package us.liyifan.things.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import us.liyifan.things.data.api.ApiException
import us.liyifan.things.data.api.SnapshotDto
import us.liyifan.things.data.api.ThingsApi
import us.liyifan.things.data.db.AppDatabase
import us.liyifan.things.data.db.AreaEntity
import us.liyifan.things.data.db.ChecklistEntity
import us.liyifan.things.data.db.SnapshotStore
import us.liyifan.things.data.db.TagEntity
import us.liyifan.things.data.db.TaskEntity
import us.liyifan.things.data.db.TaskTagEntity
import us.liyifan.things.data.outbox.OutboxOp
import us.liyifan.things.data.outbox.OutboxProcessor
import us.liyifan.things.model.Area
import us.liyifan.things.model.ChecklistItem
import us.liyifan.things.model.Item
import us.liyifan.things.model.ItemType
import us.liyifan.things.model.NewTaskInit
import us.liyifan.things.model.Opt
import us.liyifan.things.model.Schedule
import us.liyifan.things.model.Snapshot
import us.liyifan.things.model.Status
import us.liyifan.things.model.Tag
import us.liyifan.things.model.TaskPatch
import us.liyifan.things.model.applyTaskPatch
import us.liyifan.things.model.applyWhen
import us.liyifan.things.model.newProvisionalTask
import us.liyifan.things.model.newTempId
import us.liyifan.things.model.reclassify
import us.liyifan.things.model.remove
import us.liyifan.things.model.toEditFields
import java.time.Instant

/**
 * The repository over the self-hosted things-cloud service.
 *
 * Every mutation follows the same three steps, which are the web client's `write()` made durable:
 * patch the local copy inside one transaction, append the write to the outbox, and ask for the
 * queue to be drained. The screen has already moved on by the time the network is involved.
 *
 * A read is only allowed to land when the outbox is empty. The backend has no delta endpoint and
 * no conflict detection, so a snapshot fetched while a write is still queued describes a world
 * that does not include it, and applying it would make a completed to-do reappear.
 */
class HubThingsRepository(
    private val db: AppDatabase,
    private val api: ThingsApi,
    private val store: SnapshotStore,
    private val json: Json,
    private val staged: StagedSnapshot = StagedSnapshot(),
    private val readyToDraw: () -> Boolean = { true },
    private val kickOutbox: () -> Unit = {},
    private val onSynced: suspend (Long) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) : ThingsRepository {

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val _idRemaps = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)
    private val _syncState = MutableStateFlow(SyncState())
    private val writeLock = Mutex()

    val processor = OutboxProcessor(
        db = db,
        api = api,
        json = json,
        onIdResolved = { temp, real -> _idRemaps.emit(temp to real) },
        onDropped = { message -> _toasts.emit(message) },
        now = now,
        writeLock = writeLock,
    )

    override val snapshot: Flow<Snapshot?> = store.snapshots()
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    override val toasts: SharedFlow<String> = _toasts.asSharedFlow()
    override val idRemaps: SharedFlow<Pair<String, String>> = _idRemaps.asSharedFlow()

    // -- reading ------------------------------------------------------------------------------

    override suspend fun refresh(sync: Boolean, force: Boolean): RefreshResult {
        if (processor.pending() > 0) {
            kickOutbox()
            return RefreshResult.SkippedPendingWrites
        }
        _syncState.update { it.copy(refreshing = true) }
        return try {
            val dto = api.snapshot(sync)
            // On e-ink a list must not rearrange itself under someone who is reading it, so a
            // snapshot that arrives mid-page waits for the next thing the user does.
            if (!force && !readyToDraw()) {
                staged.put(dto)
                _syncState.update { it.copy(refreshing = false, stagedAvailable = true, lastError = null) }
                RefreshResult.Staged
            } else {
                // Under the write lock, and checked again: the fetch took a round trip, and a
                // to-do completed while it was in flight would otherwise be resurrected by an
                // answer that predates it.
                writeLock.withLock {
                    if (processor.pending() > 0) {
                        _syncState.update { it.copy(refreshing = false) }
                        kickOutbox()
                        return RefreshResult.SkippedPendingWrites
                    }
                    applyDto(dto)
                }
                RefreshResult.Applied
            }
        } catch (e: ApiException) {
            _syncState.update { it.copy(refreshing = false, lastError = e.message) }
            RefreshResult.Failed(e.message ?: "could not reach the server")
        }
    }

    override suspend fun applyStagedIfAny() {
        val dto = staged.take() ?: return
        writeLock.withLock {
            // Held back snapshots go stale the same way fresh ones do: anything queued since it
            // was fetched is missing from it, and drawing it would undo that work.
            if (processor.pending() > 0) return
            applyDto(dto)
        }
    }

    private suspend fun applyDto(dto: SnapshotDto) {
        store.apply(dto.toModel())
        staged.clear()
        val at = now()
        onSynced(at)
        _syncState.update {
            it.copy(refreshing = false, stagedAvailable = false, lastSyncedAt = at, lastError = null)
        }
        refreshPendingCount()
    }

    override suspend fun resync() {
        store.wipe()
        staged.clear()
        _syncState.update { it.copy(stagedAvailable = false, pendingWrites = 0) }
        refresh(sync = true, force = true)
    }

    override suspend fun loadLogbook(more: Boolean): List<Item> {
        val existing = store.logbook()
        val before = if (more) existing.lastOrNull()?.completedAt else null
        return try {
            val page = api.logbook(limit = LOGBOOK_PAGE, before = before, sync = !more)
            store.putLogbook(page, scope = null, replace = !more)
            store.logbook()
        } catch (e: ApiException) {
            _toasts.emit(e.message ?: "could not load the Logbook")
            existing
        }
    }

    override suspend fun loadLogged(projectId: String): List<Item> = try {
        val page = api.logbook(limit = PROJECT_LOG_PAGE, project = projectId, sync = false)
        store.putLogbook(page, scope = projectId, replace = true)
        store.logbook(projectId)
    } catch (_: ApiException) {
        store.logbook(projectId)
    }

    override suspend fun loadTrash(): List<Item> = try {
        store.putTrash(api.trash(sync = true))
        store.trash()
    } catch (e: ApiException) {
        _toasts.emit(e.message ?: "could not load the Trash")
        store.trash()
    }

    // -- writing ------------------------------------------------------------------------------

    /**
     * Patch locally, queue the write, ask for a send. The three steps every mutation takes.
     *
     * The row is read inside the lock as well, by [patch] itself. Reading it outside left a gap
     * in which the outbox could resolve a provisional id underneath: the edit would then be
     * written back under an id the server had just replaced, putting the row back and queueing
     * a write with no create ahead of it.
     */
    private suspend fun write(patch: suspend () -> OutboxOp?) = writeLock.withLock {
        db.withTransaction {
            val op = patch()
            op?.let { processor.enqueue(it) }
            db.metaDao().bumpGeneration()
        }
        refreshPendingCount()
        kickOutbox()
    }

    private suspend fun refreshPendingCount() {
        _syncState.update { it.copy(pendingWrites = processor.pending()) }
    }

    override suspend fun createTask(init: NewTaskInit): String {
        val tempId = newTempId()
        val today = store.today()
        val task = newProvisionalTask(tempId, init, today, Instant.ofEpochMilli(now()).toString())
        write {
            putTask(task)
            store.writeViews(store.readViews().reclassify(task, today))
            OutboxOp.CreateTask(
                tempId = tempId,
                title = init.title,
                note = init.note,
                whenValue = init.whenValue,
                project = init.project,
                tags = init.tags,
            )
        }
        if (init.heading != null || init.area != null) {
            // Queued separately, naming the provisional id: the create ahead of it resolves
            // that id first, and this is then sent against the real one.
            write {
                OutboxOp.EditTask(
                    us.liyifan.things.model.EditFields(
                        uuid = tempId,
                        heading = init.heading,
                        area = init.area,
                    ),
                )
            }
        }
        return tempId
    }

    override suspend fun updateTask(id: String, patch: TaskPatch) {
        if (patch.isEmpty) return
        write {
            val existing = db.taskDao().byId(id) ?: return@write null
            val today = store.today()
            val after = applyTaskPatch(existing.toModel(db.taskDao().tagsOf(id)), patch, today)
            putTask(after)
            // Projects and headings are not in the built-in lists, so only a task re-files.
            if (after.isTask) store.writeViews(store.readViews().reclassify(after, today))
            OutboxOp.EditTask(patch.toEditFields(id))
        }
    }

    /**
     * Ticking, and un-ticking from the Logbook.
     *
     * A completed row is marked rather than deleted, and leaves the built-in lists at once.
     * [forgetCompleted] removes it a beat later, which is what gives the row time to be seen
     * ticked without the write itself waiting on that pause — kill the app during it and the
     * completion is already queued.
     *
     * Reopening has to look somewhere else entirely: a completed to-do is not in the open
     * world, it is in the cached Logbook page the user is looking at.
     */
    override suspend fun completeTask(id: String, done: Boolean) {
        write {
            if (done) {
                val existing = db.taskDao().byId(id) ?: return@write null
                putTask(
                    existing.toModel(db.taskDao().tagsOf(id))
                        .copy(status = Status.COMPLETED, completedAt = Instant.ofEpochMilli(now()).toString()),
                )
                db.viewDao().remove(id)
            } else {
                val logged = store.loggedItem(id) ?: db.taskDao().byId(id)?.toModel()
                    ?: return@write null
                val reopened = logged.copy(status = Status.OPEN, completedAt = null)
                putTask(reopened)
                db.logbookDao().delete(id)
                if (reopened.isTask) {
                    store.writeViews(store.readViews().reclassify(reopened, store.today()))
                }
            }
            OutboxOp.TaskActionOp(if (done) "COMPLETE" else "UNCOMPLETE", id)
        }
    }

    /** Drops a ticked row once it has been seen. No write: the server was told at the tick. */
    override suspend fun forgetCompleted(id: String) = writeLock.withLock {
        db.withTransaction {
            val row = db.taskDao().byId(id)
            if (row != null && row.status == Status.COMPLETED) {
                db.taskDao().delete(id)
                db.viewDao().remove(id)
                db.metaDao().bumpGeneration()
            }
        }
    }

    override suspend fun cancelTask(id: String) = write {
        db.taskDao().delete(id)
        db.viewDao().remove(id)
        OutboxOp.TaskActionOp("CANCEL", id)
    }

    override suspend fun trashTask(id: String) = write {
        db.taskDao().delete(id)
        db.viewDao().remove(id)
        OutboxOp.TaskActionOp("TRASH", id)
    }

    override suspend fun untrashTask(id: String) = write {
        db.logbookDao().deleteTrash(id)
        OutboxOp.TaskActionOp("UNTRASH", id)
    }

    override suspend fun moveTask(id: String, to: String) {
        write {
            val existing = db.taskDao().byId(id) ?: return@write null
            val today = store.today()
            val moved = applyWhen(existing.toModel(db.taskDao().tagsOf(id)), to, today)
            putTask(moved)
            store.writeViews(store.readViews().reclassify(moved, today))
            OutboxOp.MoveTask(id, to)
        }
    }

    override suspend fun addChecklistItem(taskId: String, title: String): String {
        val tempId = newTempId()
        write {
            val index = db.checklistDao().countFor(taskId)
            db.checklistDao().put(
                ChecklistEntity.from(
                    ChecklistItem(tempId, taskId, title, Status.OPEN, index, provisional = true),
                ),
            )
            OutboxOp.CreateChecklistItem(tempId, taskId, title)
        }
        return tempId
    }

    override suspend fun toggleChecklistItem(id: String, done: Boolean) {
        write {
            val existing = db.checklistDao().byId(id) ?: return@write null
            db.checklistDao().put(existing.copy(status = if (done) Status.COMPLETED else Status.OPEN))
            OutboxOp.ChecklistActionOp(if (done) "COMPLETE" else "UNCOMPLETE", id)
        }
    }

    override suspend fun deleteChecklistItem(id: String) = write {
        db.checklistDao().delete(id)
        OutboxOp.ChecklistActionOp("DELETE", id)
    }

    override suspend fun createProject(title: String, areaId: String?): String {
        val tempId = newTempId()
        write {
            putTask(
                Item(
                    id = tempId,
                    type = ItemType.PROJECT,
                    title = title,
                    schedule = Schedule.ANYTIME,
                    areaId = areaId,
                    index = Int.MAX_VALUE,
                    createdAt = Instant.ofEpochMilli(now()).toString(),
                    provisional = true,
                ),
            )
            OutboxOp.CreateProject(tempId = tempId, title = title, area = areaId)
        }
        return tempId
    }

    override suspend fun createHeading(title: String, projectId: String): String {
        val tempId = newTempId()
        write {
            putTask(
                Item(
                    id = tempId,
                    type = ItemType.HEADING,
                    title = title,
                    projectId = projectId,
                    index = Int.MAX_VALUE,
                    provisional = true,
                ),
            )
            OutboxOp.CreateHeading(tempId, title, projectId)
        }
        return tempId
    }

    override suspend fun createArea(title: String): String {
        val tempId = newTempId()
        write {
            val index = db.areaDao().maxIndex() + 1
            db.areaDao().put(AreaEntity.from(Area(tempId, title, index), provisional = true))
            OutboxOp.CreateArea(tempId, title)
        }
        return tempId
    }

    override suspend fun renameArea(id: String, title: String) {
        write {
            val existing = db.areaDao().byId(id) ?: return@write null
            db.areaDao().put(existing.copy(title = title))
            OutboxOp.EditArea(id, title)
        }
    }

    /**
     * Things asks whether an area's projects should go too; the web client trashes them, and so
     * does this. The backend refuses the area delete itself unless it was started with
     * THINGS_ALLOW_AREA_DELETE, in which case the projects are still gone and the user is told
     * the area has to be removed in Things — which is exactly what the web client does.
     */
    override suspend fun deleteArea(id: String) {
        val projects = db.taskDao().projectsInArea(id)
        val loose = db.taskDao().tasksInArea(id)
        writeLock.withLock {
            db.withTransaction {
                projects.forEach { processor.enqueue(OutboxOp.TaskActionOp("TRASH", it.id)) }
                loose.forEach { processor.enqueue(OutboxOp.TaskActionOp("TRASH", it.id)) }
                processor.enqueue(OutboxOp.DeleteArea(id))
                db.taskDao().deleteInArea(id)
                db.areaDao().delete(id)
                (projects + loose).forEach { db.viewDao().remove(it.id) }
                db.metaDao().bumpGeneration()
            }
            refreshPendingCount()
            kickOutbox()
        }
    }

    override suspend fun createTag(title: String): String {
        val tempId = newTempId()
        write {
            val index = db.tagDao().maxIndex() + 1
            db.tagDao().put(TagEntity.from(Tag(tempId, title, index = index), provisional = true))
            OutboxOp.CreateTag(tempId, title)
        }
        return tempId
    }

    // -----------------------------------------------------------------------------------------

    private suspend fun putTask(item: Item) {
        db.taskDao().put(TaskEntity.from(item))
        db.taskDao().clearTagsOf(item.id)
        if (item.tagIds.isNotEmpty()) {
            db.taskDao().putTags(item.tagIds.map { TaskTagEntity(item.id, it) })
        }
    }

    private companion object {
        const val LOGBOOK_PAGE = 100
        const val PROJECT_LOG_PAGE = 500
    }
}

/**
 * A fetched snapshot that has not been drawn yet.
 *
 * In-memory on purpose: if the process dies before it is applied, the next launch fetches a
 * fresher one anyway, and persisting it would mean a second source of truth to keep in step.
 */
class StagedSnapshot {
    private val pending = MutableStateFlow<SnapshotDto?>(null)

    fun put(dto: SnapshotDto) { pending.value = dto }
    fun take(): SnapshotDto? = pending.value.also { pending.value = null }
    fun clear() { pending.value = null }
    val available: StateFlow<SnapshotDto?> get() = pending.asStateFlow()
}
