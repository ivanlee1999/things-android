package us.liyifan.things.data.repo

import us.liyifan.things.data.api.ApiException
import us.liyifan.things.data.api.ChecklistAction
import us.liyifan.things.data.api.CreateProjectRequest
import us.liyifan.things.data.api.CreateTaskRequest
import us.liyifan.things.data.api.SnapshotDto
import us.liyifan.things.data.api.TaskAction
import us.liyifan.things.data.api.TaskDto
import us.liyifan.things.data.api.ThingsApi
import us.liyifan.things.model.EditFields

/**
 * A server that records what it was asked and can be told to refuse.
 *
 * It mints ids the way the real one does (a short opaque string, nothing like a temp id) so the
 * tests exercise the swap rather than assuming it.
 */
open class FakeThingsApi : ThingsApi {

    sealed interface Call {
        data class CreateTask(val request: CreateTaskRequest, val minted: String) : Call
        data class EditTask(val fields: EditFields) : Call
        data class Action(val action: TaskAction, val uuid: String) : Call
        data class Move(val uuid: String, val to: String) : Call
        data class CreateChecklist(val taskUuid: String, val title: String, val minted: String) : Call
        data class ChecklistAct(val action: ChecklistAction, val uuid: String) : Call
        data class CreateProject(val request: CreateProjectRequest, val minted: String) : Call
        data class CreateHeading(val title: String, val project: String, val minted: String) : Call
        data class CreateArea(val title: String, val minted: String) : Call
        data class EditArea(val uuid: String, val title: String) : Call
        data class DeleteArea(val uuid: String) : Call
        data class CreateTag(val title: String, val minted: String) : Call
        data class Snapshot(val sync: Boolean) : Call
    }

    val calls = mutableListOf<Call>()
    var snapshotToReturn: SnapshotDto = SnapshotDto(today = "2026-09-15")
    var logbookToReturn: List<TaskDto> = emptyList()
    var trashToReturn: List<TaskDto> = emptyList()

    /** Thrown once, on the next write, then cleared. */
    var failNextWith: ApiException? = null

    /** Thrown on every write until cleared — an offline phone. */
    var failAllWith: ApiException? = null

    private var minted = 0
    private fun mint(): String = "srv${++minted}"

    private fun gate() {
        failAllWith?.let { throw it }
        failNextWith?.let { failNextWith = null; throw it }
    }

    override suspend fun snapshot(sync: Boolean): SnapshotDto {
        calls += Call.Snapshot(sync)
        failAllWith?.let { throw it }
        return snapshotToReturn
    }

    override suspend fun logbook(limit: Int, before: String?, project: String?, sync: Boolean) =
        logbookToReturn

    override suspend fun trash(limit: Int, sync: Boolean) = trashToReturn

    override suspend fun createTask(request: CreateTaskRequest): String {
        gate()
        return mint().also { calls += Call.CreateTask(request, it) }
    }

    override suspend fun editTask(fields: EditFields) {
        gate(); calls += Call.EditTask(fields)
    }

    override suspend fun taskAction(action: TaskAction, uuid: String) {
        gate(); calls += Call.Action(action, uuid)
    }

    override suspend fun moveTask(uuid: String, to: String) {
        gate(); calls += Call.Move(uuid, to)
    }

    override suspend fun createChecklistItem(taskUuid: String, title: String): String {
        gate()
        return mint().also { calls += Call.CreateChecklist(taskUuid, title, it) }
    }

    override suspend fun checklistAction(action: ChecklistAction, uuid: String) {
        gate(); calls += Call.ChecklistAct(action, uuid)
    }

    override suspend fun createProject(request: CreateProjectRequest): String {
        gate()
        return mint().also { calls += Call.CreateProject(request, it) }
    }

    override suspend fun createHeading(title: String, project: String): String {
        gate()
        return mint().also { calls += Call.CreateHeading(title, project, it) }
    }

    override suspend fun createArea(title: String): String {
        gate()
        return mint().also { calls += Call.CreateArea(title, it) }
    }

    override suspend fun editArea(uuid: String, title: String) {
        gate(); calls += Call.EditArea(uuid, title)
    }

    override suspend fun deleteArea(uuid: String) {
        gate(); calls += Call.DeleteArea(uuid)
    }

    override suspend fun createTag(title: String): String {
        gate()
        return mint().also { calls += Call.CreateTag(title, it) }
    }

    inline fun <reified T : Call> only(): List<T> = calls.filterIsInstance<T>()
}
