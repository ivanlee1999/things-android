package us.liyifan.things.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import us.liyifan.things.model.Area
import us.liyifan.things.model.ChecklistItem
import us.liyifan.things.model.Item
import us.liyifan.things.model.Tag

/**
 * The local mirror of the server's snapshot.
 *
 * Shapes follow the wire: dates stay "YYYY-MM-DD" strings and instants stay RFC 3339, because
 * every one of them is handed straight back to the server or compared as a string, and parsing
 * on the way in would only add a place to get a timezone wrong.
 *
 * Nothing here is a foreign key. The mirror is routinely incomplete — a task's project may be
 * trashed, or a provisional row may point at another row the server has never seen — and Room
 * enforcing referential integrity would reject exactly the states this app has to hold.
 */

@Entity(tableName = "areas")
data class AreaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val idx: Int,
    /** True while this area exists only on this device, under a tmp- id. */
    val provisional: Boolean = false,
) {
    fun toModel() = Area(id = id, title = title, index = idx)

    companion object {
        fun from(a: Area, provisional: Boolean = false) =
            AreaEntity(a.id, a.title, a.index, provisional)
    }
}

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val title: String,
    val shortcut: String?,
    val parentId: String?,
    val idx: Int,
    val provisional: Boolean = false,
) {
    fun toModel() = Tag(id = id, title = title, shortcut = shortcut, parentId = parentId, index = idx)

    companion object {
        fun from(t: Tag, provisional: Boolean = false) =
            TagEntity(t.id, t.title, t.shortcut, t.parentId, t.index, provisional)
    }
}

/** Tasks, projects and headings alike; `type` tells them apart, as in Things' own schema. */
@Entity(
    tableName = "tasks",
    indices = [
        Index("type"), Index("projectId"), Index("areaId"), Index("headingId"),
        Index("provisional"),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val type: Int,
    val title: String,
    val note: String,
    val status: Int,
    val schedule: Int,
    val scheduledDate: String?,
    val deadline: String?,
    val areaId: String?,
    val projectId: String?,
    val headingId: String?,
    val idx: Int,
    val todayIndex: Int,
    val repeating: Boolean,
    val inTrash: Boolean,
    val createdAt: String?,
    val modifiedAt: String?,
    val completedAt: String?,
    val provisional: Boolean = false,
) {
    /** Tags live in their own table, so the caller supplies them. */
    fun toModel(tagIds: List<String> = emptyList()) = Item(
        id = id, type = type, title = title, note = note, status = status, schedule = schedule,
        scheduledDate = scheduledDate, deadline = deadline, areaId = areaId, projectId = projectId,
        headingId = headingId, tagIds = tagIds, index = idx, todayIndex = todayIndex,
        repeating = repeating, inTrash = inTrash, createdAt = createdAt, modifiedAt = modifiedAt,
        completedAt = completedAt, provisional = provisional,
    )

    companion object {
        fun from(i: Item) = TaskEntity(
            id = i.id, type = i.type, title = i.title, note = i.note, status = i.status,
            schedule = i.schedule, scheduledDate = i.scheduledDate, deadline = i.deadline,
            areaId = i.areaId, projectId = i.projectId, headingId = i.headingId, idx = i.index,
            todayIndex = i.todayIndex, repeating = i.repeating, inTrash = i.inTrash,
            createdAt = i.createdAt, modifiedAt = i.modifiedAt, completedAt = i.completedAt,
            provisional = i.provisional,
        )
    }
}

@Entity(tableName = "task_tags", primaryKeys = ["taskId", "tagId"], indices = [Index("tagId")])
data class TaskTagEntity(val taskId: String, val tagId: String)

@Entity(tableName = "checklist", indices = [Index("taskId")])
data class ChecklistEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val status: Int,
    val idx: Int,
    val provisional: Boolean = false,
) {
    fun toModel() = ChecklistItem(id, taskId, title, status, idx, provisional)

    companion object {
        fun from(c: ChecklistItem) = ChecklistEntity(c.id, c.taskId, c.title, c.status, c.index, c.provisional)
    }
}

/**
 * Which built-in list each task is in, and in what order — the server's answer, kept verbatim.
 * [position] preserves the order it sent; nothing here re-sorts.
 */
@Entity(tableName = "view_membership", primaryKeys = ["view", "taskId"], indices = [Index("view", "position")])
data class ViewMembershipEntity(val view: String, val taskId: String, val position: Int)

@Entity(tableName = "project_done")
data class ProjectDoneEntity(@PrimaryKey val projectId: String, val count: Int)

/**
 * The Logbook and the Trash, cached as the JSON the server sent.
 *
 * They are paged, read-mostly and never queried by field, so storing the rows whole keeps them
 * out of the snapshot's tables where a stale completed row could otherwise turn up in a list.
 */
@Entity(tableName = "logbook", indices = [Index("completedAt"), Index("projectId")])
data class LogbookEntity(
    @PrimaryKey val id: String,
    val json: String,
    val completedAt: String?,
    val projectId: String?,
    /** Null for the main Logbook; a project id for the rows loaded inside that project. */
    val scope: String?,
)

@Entity(tableName = "trash", indices = [Index("modifiedAt")])
data class TrashEntity(
    @PrimaryKey val id: String,
    val json: String,
    val modifiedAt: String?,
)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(@PrimaryKey val key: String, val value: String)

/**
 * A write that has not reached the server yet.
 *
 * Rows are sent oldest first and one at a time, which is what makes a temp id safe: the create
 * that mints an id always precedes anything that mentions it.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val kind: String,
    val payload: String,
    /** Set on a create: the provisional id this row will resolve. */
    val tempId: String?,
    val attempts: Int = 0,
    val lastError: String? = null,
    val lastAttemptAt: Long? = null,
)

/** A provisional id and the real one the server gave it, kept briefly so late callers can follow. */
@Entity(tableName = "id_map")
data class IdMapEntity(
    @PrimaryKey val tempId: String,
    val realId: String,
    val resolvedAt: Long,
)
