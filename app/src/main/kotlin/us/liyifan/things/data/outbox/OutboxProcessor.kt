package us.liyifan.things.data.outbox

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
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
    /**
     * Shared with the repository's local writes. An id swap rewrites rows the user may be
     * editing at that moment; without one lock between them, an edit read before the swap is
     * written back after it, reinstating a provisional id whose create has already gone.
     */
    private val writeLock: Mutex = Mutex(),
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
                if (tempId != null && mintedId != null) {
                    // The id swap and the removal of this row commit together. Apart, a crash
                    // between them would leave the create queued against a to-do the server has
                    // already made, and the next drain would make a second one.
                    resolve(tempId, mintedId, done = row.id)
                } else {
                    outbox.delete(row.id)
                }
                sent++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val api = e as? ApiException
                val transient = api != null && !api.permanent
                if (transient) {
                    // The server was not at fault for long enough to give up on: a network that
                    // came and went, or its own sync to Things Cloud failing. Only failures the
                    // server actually answered count towards giving up, or a fortnight in a
                    // basement would throw away perfectly good work.
                    val counts = api.kind != ApiException.Kind.NETWORK
                    val attempts = row.attempts + if (counts) 1 else 0
                    if (counts && attempts >= MAX_SERVER_ATTEMPTS) {
                        outbox.delete(row.id)
                        dropDependents(op)
                        needsFullRefresh = true
                        onDropped("Gave up saving ${op.describe} after $attempts tries: ${e.message}")
                        continue
                    }
                    if (counts) outbox.recordFailure(row.id, e.message, now())
                    return@withLock Result.Blocked(e.message ?: "cannot reach the server", outbox.count())
                }
                // Either the server refused it, which it will do identically forever, or
                // something unforeseen went wrong turning this row into a request. Both have to
                // drop the row: leaving it at the head of a queue drained in order would stop
                // every later write from ever being sent.
                outbox.delete(row.id)
                dropDependents(op)
                needsFullRefresh = true
                onDropped("Could not save ${op.describe}: ${e.message ?: e::class.simpleName}")
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
        is OutboxOp.CreateTask -> api.createTask(
            CreateTaskRequest(
                title = op.title,
                note = op.note,
                whenValue = op.whenValue,
                deadline = op.deadline,
                project = op.project,
                tags = op.tags.takeIf { it.isNotEmpty() }?.joinToString(","),
            ),
        )
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
    private suspend fun resolve(tempId: String, realId: String, done: Long?) = writeLock.withLock {
      db.withTransaction {
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
        done?.let { outbox.delete(it) }
        db.metaDao().bumpGeneration()
        onIdResolved(tempId, realId)
      }
    }

    /**
     * A create that was refused leaves orphans: the local row, and every queued write that names
     * it. Sending those would fail one by one with the same message, so they go together.
     */
    private suspend fun dropDependents(failed: OutboxOp) = writeLock.withLock {
      db.withTransaction {
        val root = failed.mintsFor ?: return@withTransaction
        // Follow the chain: an area refused takes its projects, and those take their to-dos.
        // Stopping at the first level would leave rows on screen that can never be sent.
        val doomed = mutableSetOf(root)
        var grew = true
        while (grew) {
            grew = false
            outbox.all().forEach { row ->
                val op = decode(row) ?: return@forEach
                if (op.referencedIds().none { it in doomed }) return@forEach
                op.mintsFor?.let { if (doomed.add(it)) grew = true }
            }
        }
        outbox.all().forEach { row ->
            val op = decode(row) ?: return@forEach
            if (op.referencedIds().any { it in doomed }) outbox.delete(row.id)
        }
        doomed.forEach { id ->
            db.checklistDao().deleteForTask(id)
            db.checklistDao().delete(id)
            db.taskDao().deleteInProject(id)
            db.taskDao().delete(id)
            db.areaDao().delete(id)
            db.tagDao().delete(id)
            db.viewDao().remove(id)
        }
        db.metaDao().bumpGeneration()
      }
    }

    private suspend fun pruneIdMap() {
        outbox.pruneIdMap(now() - ID_MAP_TTL_MS)
    }

    private companion object {
        const val ID_MAP_TTL_MS = 24L * 60 * 60 * 1000

        /** How many answered-and-failed attempts before a write is abandoned. */
        const val MAX_SERVER_ATTEMPTS = 10
    }
}
