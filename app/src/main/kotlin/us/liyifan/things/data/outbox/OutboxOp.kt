package us.liyifan.things.data.outbox

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import us.liyifan.things.model.EditFields

/**
 * A write waiting to be sent.
 *
 * Every op can say which ids it names and hand back a copy with some of them replaced. That is
 * what makes offline work safe: a to-do created on a train has a `tmp-` id, and the edits,
 * completions and checklist items that follow it all name that id. When the create finally
 * reaches the server and comes back with a real one, each queued op is rewritten structurally —
 * never by substituting text in JSON, which would also rewrite a title that happened to contain
 * the id.
 */
@Serializable
sealed class OutboxOp {

    abstract fun referencedIds(): Set<String>
    abstract fun rewriteIds(map: Map<String, String>): OutboxOp

    /** The provisional row this op will give a real id to, if it is a create. */
    open val mintsFor: String? get() = null

    /** A short phrase for the toast when this op is refused for good. */
    abstract val describe: String

    @Serializable
    @SerialName("create_task")
    data class CreateTask(
        val tempId: String,
        val title: String,
        val note: String? = null,
        val whenValue: String? = null,
        val deadline: String? = null,
        val project: String? = null,
        val tags: List<String> = emptyList(),
    ) : OutboxOp() {
        override val mintsFor get() = tempId
        override val describe get() = "the to-do “${title.ifBlank { "New To-Do" }}”"
        override fun referencedIds() = setOfNotNull(tempId, project) + tags
        override fun rewriteIds(map: Map<String, String>) = copy(
            project = project?.let { map[it] ?: it },
            // A tag made offline carries a provisional id too, and a create still holding one
            // would hand the server an id it has never heard of.
            tags = tags.map { map[it] ?: it },
        )
    }

    @Serializable
    @SerialName("edit_task")
    data class EditTask(val fields: EditFields) : OutboxOp() {
        override val describe get() = "an edit"
        override fun referencedIds() = fields.referencedIds()
        override fun rewriteIds(map: Map<String, String>) = copy(fields = fields.rewriteIds(map))
    }

    @Serializable
    @SerialName("task_action")
    data class TaskActionOp(val action: String, val uuid: String) : OutboxOp() {
        override val describe get() = "a $action"
        override fun referencedIds() = setOf(uuid)
        override fun rewriteIds(map: Map<String, String>) = copy(uuid = map[uuid] ?: uuid)
    }

    @Serializable
    @SerialName("move_task")
    data class MoveTask(val uuid: String, val to: String) : OutboxOp() {
        override val describe get() = "a move to $to"
        override fun referencedIds() = setOf(uuid)
        override fun rewriteIds(map: Map<String, String>) = copy(uuid = map[uuid] ?: uuid)
    }

    @Serializable
    @SerialName("create_checklist")
    data class CreateChecklistItem(val tempId: String, val taskId: String, val title: String) : OutboxOp() {
        override val mintsFor get() = tempId
        override val describe get() = "a checklist item"
        override fun referencedIds() = setOf(tempId, taskId)
        override fun rewriteIds(map: Map<String, String>) = copy(taskId = map[taskId] ?: taskId)
    }

    @Serializable
    @SerialName("checklist_action")
    data class ChecklistActionOp(val action: String, val uuid: String) : OutboxOp() {
        override val describe get() = "a checklist change"
        override fun referencedIds() = setOf(uuid)
        override fun rewriteIds(map: Map<String, String>) = copy(uuid = map[uuid] ?: uuid)
    }

    @Serializable
    @SerialName("create_project")
    data class CreateProject(
        val tempId: String,
        val title: String,
        val note: String? = null,
        val whenValue: String? = null,
        val deadline: String? = null,
        val area: String? = null,
    ) : OutboxOp() {
        override val mintsFor get() = tempId
        override val describe get() = "the project “${title.ifBlank { "New Project" }}”"
        override fun referencedIds() = setOfNotNull(tempId, area)
        override fun rewriteIds(map: Map<String, String>) = copy(area = area?.let { map[it] ?: it })
    }

    @Serializable
    @SerialName("create_heading")
    data class CreateHeading(val tempId: String, val title: String, val project: String) : OutboxOp() {
        override val mintsFor get() = tempId
        override val describe get() = "the heading “$title”"
        override fun referencedIds() = setOf(tempId, project)
        override fun rewriteIds(map: Map<String, String>) = copy(project = map[project] ?: project)
    }

    @Serializable
    @SerialName("create_area")
    data class CreateArea(val tempId: String, val title: String) : OutboxOp() {
        override val mintsFor get() = tempId
        override val describe get() = "the area “${title.ifBlank { "New Area" }}”"
        override fun referencedIds() = setOf(tempId)
        override fun rewriteIds(map: Map<String, String>) = this
    }

    @Serializable
    @SerialName("edit_area")
    data class EditArea(val uuid: String, val title: String) : OutboxOp() {
        override val describe get() = "renaming an area"
        override fun referencedIds() = setOf(uuid)
        override fun rewriteIds(map: Map<String, String>) = copy(uuid = map[uuid] ?: uuid)
    }

    @Serializable
    @SerialName("delete_area")
    data class DeleteArea(val uuid: String) : OutboxOp() {
        override val describe get() = "deleting an area"
        override fun referencedIds() = setOf(uuid)
        override fun rewriteIds(map: Map<String, String>) = copy(uuid = map[uuid] ?: uuid)
    }

    @Serializable
    @SerialName("create_tag")
    data class CreateTag(val tempId: String, val title: String) : OutboxOp() {
        override val mintsFor get() = tempId
        override val describe get() = "the tag “$title”"
        override fun referencedIds() = setOf(tempId)
        override fun rewriteIds(map: Map<String, String>) = this
    }
}
