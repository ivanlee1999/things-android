package us.liyifan.things.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import us.liyifan.things.model.Area
import us.liyifan.things.model.ChecklistItem
import us.liyifan.things.model.Item
import us.liyifan.things.model.Snapshot
import us.liyifan.things.model.Tag
import us.liyifan.things.model.Views

/**
 * The wire shapes of the things-cloud REST API, and the conversions to the domain model.
 *
 * These are separate types from the model on purpose: the server sends a few fields this app
 * does not model (and will send more), and every one of them would otherwise be a parse failure
 * or a field to keep in step. The Json instance is configured to ignore unknown keys for the
 * same reason.
 */

@Serializable
data class SnapshotDto(
    val generatedAt: String = "",
    val timezone: String = "",
    val today: String,
    val areas: List<AreaDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val projects: List<TaskDto> = emptyList(),
    val headings: List<TaskDto> = emptyList(),
    val tasks: List<TaskDto> = emptyList(),
    val checklist: List<ChecklistItemDto> = emptyList(),
    val views: ViewsDto = ViewsDto(),
    val projectDone: Map<String, Int> = emptyMap(),
) {
    fun toModel() = Snapshot(
        generatedAt = generatedAt,
        timezone = timezone,
        today = today,
        areas = areas.map { it.toModel() },
        tags = tags.map { it.toModel() },
        projects = projects.map { it.toModel() },
        headings = headings.map { it.toModel() },
        tasks = tasks.map { it.toModel() },
        checklist = checklist.map { it.toModel() },
        views = views.toModel(),
        projectDone = projectDone,
    )
}

@Serializable
data class AreaDto(val id: String, val title: String = "", val index: Int = 0) {
    fun toModel() = Area(id = id, title = title, index = index)
}

@Serializable
data class TagDto(
    val id: String,
    val title: String = "",
    val shortcut: String? = null,
    val parentId: String? = null,
    val index: Int = 0,
) {
    fun toModel() = Tag(id = id, title = title, shortcut = shortcut, parentId = parentId, index = index)
}

@Serializable
data class TaskDto(
    val id: String,
    val type: Int = 0,
    val title: String = "",
    val note: String = "",
    val status: Int = 0,
    val schedule: Int = 1,
    val scheduledDate: String? = null,
    val deadline: String? = null,
    val areaId: String? = null,
    val projectId: String? = null,
    val headingId: String? = null,
    val tagIds: List<String> = emptyList(),
    val index: Int = 0,
    val todayIndex: Int = 0,
    val repeating: Boolean = false,
    val inTrash: Boolean = false,
    val createdAt: String? = null,
    val modifiedAt: String? = null,
    val completedAt: String? = null,
) {
    fun toModel() = Item(
        id = id, type = type, title = title, note = note, status = status, schedule = schedule,
        scheduledDate = scheduledDate, deadline = deadline, areaId = areaId, projectId = projectId,
        headingId = headingId, tagIds = tagIds, index = index, todayIndex = todayIndex,
        repeating = repeating, inTrash = inTrash, createdAt = createdAt, modifiedAt = modifiedAt,
        completedAt = completedAt,
    )
}

@Serializable
data class ChecklistItemDto(
    val id: String,
    val taskId: String,
    val title: String = "",
    val status: Int = 0,
    val index: Int = 0,
) {
    fun toModel() = ChecklistItem(id = id, taskId = taskId, title = title, status = status, index = index)
}

@Serializable
data class ViewsDto(
    val inbox: List<String> = emptyList(),
    val today: List<String> = emptyList(),
    val upcoming: List<String> = emptyList(),
    val anytime: List<String> = emptyList(),
    val someday: List<String> = emptyList(),
) {
    fun toModel() = Views(inbox, today, upcoming, anytime, someday)
}

/** Every write answers with this and nothing else, which is why a write is followed by a read. */
@Serializable
data class WriteResponse(val status: String = "", val uuid: String? = null)

@Serializable
data class ErrorResponse(val error: String = "")

@Serializable
data class CreateTaskRequest(
    val title: String,
    val note: String? = null,
    @SerialName("when") val whenValue: String? = null,
    val deadline: String? = null,
    val project: String? = null,
    @SerialName("parent_task") val parentTask: String? = null,
    /** Comma-separated tag ids: the backend takes a string here, not an array. */
    val tags: String? = null,
    val repeat: String? = null,
)

@Serializable
data class EditTaskRequest(
    val uuid: String,
    val title: String? = null,
    val note: String? = null,
    @SerialName("when") val whenValue: String? = null,
    val deadline: String? = null,
    val project: String? = null,
    @SerialName("parent_task") val parentTask: String? = null,
    val area: String? = null,
    val heading: String? = null,
    val tags: String? = null,
    val repeat: String? = null,
)

@Serializable
data class CreateProjectRequest(
    val title: String,
    val note: String? = null,
    @SerialName("when") val whenValue: String? = null,
    val deadline: String? = null,
    val area: String? = null,
)

@Serializable data class UuidRequest(val uuid: String)
@Serializable data class MoveRequest(val uuid: String, val to: String)
@Serializable data class CreateChecklistRequest(@SerialName("task_uuid") val taskUuid: String, val title: String)
@Serializable data class CreateHeadingRequest(val title: String, val project: String)
@Serializable data class TitleRequest(val title: String)
@Serializable data class EditAreaRequest(val uuid: String, val title: String)
@Serializable data class CreateTagRequest(val title: String, val shorthand: String? = null, val parent: String? = null)
