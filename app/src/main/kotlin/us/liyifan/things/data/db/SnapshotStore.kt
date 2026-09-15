package us.liyifan.things.data.db

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import us.liyifan.things.data.api.TaskDto
import us.liyifan.things.model.Item
import us.liyifan.things.model.Snapshot
import us.liyifan.things.model.ViewId
import us.liyifan.things.model.Views
import us.liyifan.things.model.classify

/**
 * Reads and writes the mirror.
 *
 * The one subtle rule lives in [apply]: a snapshot from the server replaces the server's world
 * but must not take away rows this device invented and has not managed to send yet. Those are
 * kept, and re-sorted into the built-in lists with the same classifier the server's SQL follows,
 * so a to-do typed on a train does not vanish when the train reaches a signal.
 */
class SnapshotStore(
    private val db: AppDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {
    private val snapshotDao = db.snapshotDao()
    private val metaDao = db.metaDao()
    private val logbookDao = db.logbookDao()

    /** Emits once per writing transaction, never once per table. */
    fun snapshots(): Flow<Snapshot?> = metaDao.generationFlow()
        .distinctUntilChanged()
        .map { generation -> if (generation == null) null else read() }

    suspend fun read(): Snapshot = db.withTransaction {
        val tagsByTask = snapshotDao.taskTags().groupBy({ it.taskId }, { it.tagId })
        val tasks = snapshotDao.tasks()
        val byType = tasks.groupBy { it.type }
        val views = snapshotDao.views()
            .groupBy { it.view }
            .mapValues { (_, rows) -> rows.sortedBy { it.position }.map { it.taskId } }
        Snapshot(
            generatedAt = metaDao.get(GENERATED_AT).orEmpty(),
            timezone = metaDao.get(TIMEZONE).orEmpty(),
            today = metaDao.get(TODAY) ?: us.liyifan.things.model.localToday(),
            areas = snapshotDao.areas().map { it.toModel() }.sortedBy { it.index },
            tags = snapshotDao.tags().map { it.toModel() }.sortedBy { it.index },
            projects = byType[1].orEmpty().map { it.toModel(tagsByTask[it.id].orEmpty()) },
            headings = byType[2].orEmpty().map { it.toModel(tagsByTask[it.id].orEmpty()) },
            tasks = byType[0].orEmpty().map { it.toModel(tagsByTask[it.id].orEmpty()) },
            checklist = snapshotDao.checklist().map { it.toModel() },
            views = Views(
                inbox = views[ViewId.INBOX.wire].orEmpty(),
                today = views[ViewId.TODAY.wire].orEmpty(),
                upcoming = views[ViewId.UPCOMING.wire].orEmpty(),
                anytime = views[ViewId.ANYTIME.wire].orEmpty(),
                someday = views[ViewId.SOMEDAY.wire].orEmpty(),
            ),
            projectDone = snapshotDao.projectDone().associate { it.projectId to it.count },
        )
    }

    /** Replaces the server's rows, keeps this device's, and bumps the generation once. */
    suspend fun apply(snapshot: Snapshot) = db.withTransaction {
        val keptTasks = snapshotDao.provisionalTasks()
        val keptAreas = snapshotDao.provisionalAreas()
        val keptTags = snapshotDao.provisionalTags()
        val keptChecklist = snapshotDao.provisionalChecklist()

        snapshotDao.clearAreas()
        snapshotDao.clearTags()
        snapshotDao.clearTasks()
        snapshotDao.clearChecklist()
        snapshotDao.clearViews()
        snapshotDao.clearProjectDone()

        snapshotDao.putAreas(snapshot.areas.map { AreaEntity.from(it) })
        snapshotDao.putTags(snapshot.tags.map { TagEntity.from(it) })
        val serverItems = snapshot.tasks + snapshot.projects + snapshot.headings
        snapshotDao.putTasks(serverItems.map { TaskEntity.from(it) })
        snapshotDao.putChecklist(snapshot.checklist.map { ChecklistEntity.from(it) })
        snapshotDao.putProjectDone(snapshot.projectDone.map { ProjectDoneEntity(it.key, it.value) })

        // Tag links are rewritten wholesale for the rows the server just sent.
        snapshotDao.putTaskTags(serverItems.flatMap { item -> item.tagIds.map { TaskTagEntity(item.id, it) } })

        // Put the un-sent rows back, and file the tasks among them into their lists.
        if (keptAreas.isNotEmpty()) snapshotDao.putAreas(keptAreas)
        if (keptTags.isNotEmpty()) snapshotDao.putTags(keptTags)
        if (keptTasks.isNotEmpty()) snapshotDao.putTasks(keptTasks)
        if (keptChecklist.isNotEmpty()) snapshotDao.putChecklist(keptChecklist)
        snapshotDao.clearOrphanTaskTags()

        var views = snapshot.views
        keptTasks.forEach { row ->
            val item = row.toModel()
            if (item.isTask) {
                val v = classify(item, snapshot.today) ?: return@forEach
                views = views.with(v, views[v] + item.id)
            }
        }
        writeViews(views)

        metaDao.put(SyncMetaEntity(GENERATED_AT, snapshot.generatedAt))
        metaDao.put(SyncMetaEntity(TIMEZONE, snapshot.timezone))
        metaDao.put(SyncMetaEntity(TODAY, snapshot.today))
        metaDao.bumpGeneration()
    }

    suspend fun writeViews(views: Views) {
        db.viewDao().clear()
        db.viewDao().put(
            ViewId.entries.flatMap { v ->
                views[v].mapIndexed { i, id -> ViewMembershipEntity(v.wire, id, i) }
            },
        )
    }

    suspend fun readViews(): Views {
        val rows = db.viewDao().all().groupBy { it.view }
            .mapValues { (_, r) -> r.sortedBy { it.position }.map { it.taskId } }
        return Views(
            inbox = rows[ViewId.INBOX.wire].orEmpty(),
            today = rows[ViewId.TODAY.wire].orEmpty(),
            upcoming = rows[ViewId.UPCOMING.wire].orEmpty(),
            anytime = rows[ViewId.ANYTIME.wire].orEmpty(),
            someday = rows[ViewId.SOMEDAY.wire].orEmpty(),
        )
    }

    suspend fun today(): String = metaDao.get(TODAY) ?: us.liyifan.things.model.localToday()

    // -- the paged lists, stored as the JSON they arrived as -----------------------------------

    suspend fun putLogbook(items: List<TaskDto>, scope: String?, replace: Boolean) = db.withTransaction {
        if (replace) {
            if (scope == null) logbookDao.clearMain() else logbookDao.clearProject(scope)
        }
        logbookDao.put(
            items.map {
                LogbookEntity(
                    id = it.id,
                    json = json.encodeToString(it),
                    completedAt = it.completedAt,
                    projectId = it.projectId,
                    scope = scope,
                )
            },
        )
        metaDao.bumpGeneration()
    }

    suspend fun logbook(scope: String? = null): List<Item> =
        (if (scope == null) logbookDao.page() else logbookDao.forProject(scope))
            .mapNotNull { row -> runCatching { json.decodeFromString<TaskDto>(row.json).toModel() }.getOrNull() }

    suspend fun putTrash(items: List<TaskDto>) = db.withTransaction {
        logbookDao.clearTrash()
        logbookDao.putTrash(
            items.map { TrashEntity(it.id, json.encodeToString(it), it.modifiedAt) },
        )
        metaDao.bumpGeneration()
    }

    suspend fun trash(): List<Item> = logbookDao.trash()
        .mapNotNull { row -> runCatching { json.decodeFromString<TaskDto>(row.json).toModel() }.getOrNull() }

    /** Forgets everything, including un-sent writes. Only the explicit Resync does this. */
    suspend fun wipe() = db.withTransaction {
        snapshotDao.clearViews()
        snapshotDao.clearProjectDone()
        snapshotDao.deleteAllTasks()
        snapshotDao.deleteAllAreas()
        snapshotDao.deleteAllTags()
        snapshotDao.deleteAllTaskTags()
        snapshotDao.deleteAllChecklist()
        db.outboxDao().clear()
        db.outboxDao().clearIdMap()
        logbookDao.clearAll()
        logbookDao.clearTrash()
        metaDao.clear()
    }

    private companion object {
        const val GENERATED_AT = "generated_at"
        const val TIMEZONE = "timezone"
        const val TODAY = "today"
    }
}
