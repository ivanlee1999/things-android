package us.liyifan.things.data.outbox

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import us.liyifan.things.data.api.ApiException
import us.liyifan.things.data.api.ChecklistAction
import us.liyifan.things.data.api.CreateProjectRequest
import us.liyifan.things.data.api.CreateTaskRequest
import us.liyifan.things.data.api.TaskAction
import us.liyifan.things.data.api.ThingsApi
import us.liyifan.things.data.db.AppDatabase
import us.liyifan.things.data.db.IdMapEntity
import us.liyifan.things.data.db.OutboxEntity

/**
 * Sends queued writes, oldest first, one at a time.
 *
 * Order is the whole design. The backend has no batch endpoint and no conflict detection, and a
 * create is what turns a provisional id into a real one, so nothing that mentions a `tmp-` id
 * may overtake the create that resolves it. A single serial drain gives that for free, and the
 * cost — writes are not parallel — is irrelevant against a server that talks to Things Cloud on
 * every call anyway.
 *
 * Failures split in two. A refusal (4xx) will be refused identically forever, so the row is
 * dropped, the user is told in plain words, and anything queued behind it that depended on it
 * goes too. Anything else — no network, a timeout, the server's own sync to Things Cloud
 * failing — leaves the queue untouched for the worker to retry with backoff.
 */
class OutboxProcessor(
    private val db: AppDatabase,
    private val api: ThingsApi,
    private val json: Json,
    private val onIdResolved: suspend (tempId: String, realId: String) -> Unit = { _, _ -> },
    private val onDropped: suspend (message: String) -> Unit = { },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val outbox = db.outboxDao()
    private val mutex = Mutex()

    sealed interface Result {
        /** Nothing left to send. [needsFullRefresh] when something was refused and local state is suspect. */
        data class Drained(val sent: Int, val needsFullRefresh: Boolean) : Result

        /** The network or the server is unavailable; the queue is intact and should be retried. */
        data class Blocked(val reason: String, val remaining: Int) : Result
    }

    suspend fun drain(): Result = mutex.withLock {
        var sent = 0
        var needsFullRefresh = false
        while (true) {
            val row = outbox.oldest() ?: break
            val op = decode(row)
            if (op == null) {
                // Unreadable payload: a row from an older schema, or a bad write. Dropping it is
                // the only option that lets the queue behind it move.
                outbox.delete(row.id)
                needsFullRefresh = true
                continue
            }
            try {
                val mintedId = send(op)
                val tempId = op.mintsFor
                if (tempId != null && mintedId != null) resolve(tempId, mintedId)
                outbox.delete(row.id)
                sent++
            } catch (e: ApiException) {
                if (!e.permanent) {
                    outbox.recordFailure(row.id, e.message, now())
                    return@withLock Result.Blocked(e.message ?: "cannot reach the server", outbox.count())
                }
                outbox.delete(row.id)
                dropDependents(op)
                needsFullRefresh = true
                onDropped("Could not save ${op.describe}: ${e.message}")
            }
        }
        pruneIdMap()
        Result.Drained(sent, needsFullRefresh)
    }

    suspend fun enqueue(op: OutboxOp) {
        outbox.insert(
            OutboxEntity(
                createdAt = now(),
                kind = op::class.simpleName.orEmpty(),
                payload = json.encodeToString(OutboxOp.serializer(), op),
                tempId = op.mintsFor,
            ),
        )
    }

    suspend fun pending(): Int = outbox.count()

    private fun decode(row: OutboxEntity): OutboxOp? =
        runCatching { json.decodeFromString(OutboxOp.serializer(), row.payload) }.getOrNull()

    /** Returns the id the server minted, for the ops that create something. */
    private suspend fun send(op: OutboxOp): String? = when (op) {
        is OutboxOp.CreateTask -> {
            val id = api.createTask(
                CreateTaskRequest(
                    title = op.title,
                    note = op.note,
                    whenValue = op.whenValue,
                    deadline = op.deadline,
                    project = op.project,
                    tags = op.tags.takeIf { it.isNotEmpty() }?.joinToString(","),
                ),
            )
            // Create takes no heading or area, so a to-do filed under either needs a second call.
            if (op.heading != null || op.area != null) {
                api.editTask(
                    us.liyifan.things.model.EditFields(
                        uuid = id,
                        heading = op.heading,
                        area = op.area,
                    ),
                )
            }
            id
        }
        is OutboxOp.EditTask -> { api.editTask(op.fields); null }
        is OutboxOp.TaskActionOp -> { api.taskAction(TaskAction.valueOf(op.action), op.uuid); null }
        is OutboxOp.MoveTask -> { api.moveTask(op.uuid, op.to); null }
        is OutboxOp.CreateChecklistItem -> api.createChecklistItem(op.taskId, op.title)
        is OutboxOp.ChecklistActionOp -> { api.checklistAction(ChecklistAction.valueOf(op.action), op.uuid); null }
        is OutboxOp.CreateProject -> api.createProject(
            CreateProjectRequest(
                title = op.title,
                note = op.note,
                whenValue = op.whenValue,
                deadline = op.deadline,
                area = op.area,
            ),
        )
        is OutboxOp.CreateHeading -> api.createHeading(op.title, op.project)
        is OutboxOp.CreateArea -> api.createArea(op.title)
        is OutboxOp.EditArea -> { api.editArea(op.uuid, op.title); null }
        is OutboxOp.DeleteArea -> { api.deleteArea(op.uuid); null }
        is OutboxOp.CreateTag -> api.createTag(op.title)
    }

    /**
     * Swaps a provisional id for the real one everywhere it is held: the row itself, its
     * children, its list membership, and every write still queued behind it.
     */
    private suspend fun resolve(tempId: String, realId: String) = db.withTransaction {
        val tasks = db.taskDao()
        outbox.putIdMap(IdMapEntity(tempId, realId, now()))

        tasks.renameId(tempId, realId)
        tasks.renameProject(tempId, realId)
        tasks.renameHeading(tempId, realId)
        tasks.renameArea(tempId, realId)
        tasks.renameTaskTagTask(tempId, realId)
        tasks.renameTaskTagTag(tempId, realId)
        db.areaDao().renameId(tempId, realId)
        db.tagDao().renameId(tempId, realId)
        db.checklistDao().renameId(tempId, realId)
        db.checklistDao().renameTask(tempId, realId)
        db.viewDao().renameId(tempId, realId)

        val map = mapOf(tempId to realId)
        outbox.all().forEach { row ->
            val op = decode(row) ?: return@forEach
            if (tempId !in op.referencedIds()) return@forEach
            outbox.setPayload(row.id, json.encodeToString(OutboxOp.serializer(), op.rewriteIds(map)))
            if (row.tempId == tempId) outbox.setTempId(row.id, null)
        }
        db.metaDao().bumpGeneration()
        onIdResolved(tempId, realId)
    }

    /**
     * A create that was refused leaves orphans: the local row, and every queued write that names
     * it. Sending those would fail one by one with the same message, so they go together.
     */
    private suspend fun dropDependents(failed: OutboxOp) = db.withTransaction {
        val tempId = failed.mintsFor ?: return@withTransaction
        outbox.all().forEach { row ->
            val op = decode(row) ?: return@forEach
            if (tempId in op.referencedIds()) outbox.delete(row.id)
        }
        db.checklistDao().deleteForTask(tempId)
        db.checklistDao().delete(tempId)
        db.taskDao().delete(tempId)
        db.areaDao().delete(tempId)
        db.tagDao().delete(tempId)
        db.viewDao().remove(tempId)
        db.metaDao().bumpGeneration()
    }

    private suspend fun pruneIdMap() {
        outbox.pruneIdMap(now() - ID_MAP_TTL_MS)
    }

    private companion object {
        const val ID_MAP_TTL_MS = 24L * 60 * 60 * 1000
    }
}
