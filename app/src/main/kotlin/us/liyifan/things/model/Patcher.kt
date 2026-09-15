package us.liyifan.things.model

import java.time.Instant

/**
 * Local edits: what a change does to an [Item] before the server has seen it, and what goes on
 * the wire when it does. A port of things-web's applyWhen/updateTask (src/store/store.ts),
 * which mirrors parseWhen and the edit handler in the backend's server/write.go.
 */

private val DATE_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")

/**
 * Applies a `when` value the way the server's parseWhen does.
 *
 * The surprising case is a future date: Things stores it as *someday with a date*, not as
 * "anytime, later". A past date is pulled forward to today rather than left behind.
 */
fun applyWhen(t: Item, whenValue: String, today: DateStr): Item = when (whenValue) {
    "today" -> t.copy(schedule = Schedule.ANYTIME, scheduledDate = today)
    "anytime" -> t.copy(schedule = Schedule.ANYTIME, scheduledDate = null)
    "someday" -> t.copy(schedule = Schedule.SOMEDAY, scheduledDate = null)
    "inbox" -> t.copy(schedule = Schedule.INBOX, scheduledDate = null)
    "none" -> t.copy(scheduledDate = null)
    else -> when {
        !DATE_RE.matches(whenValue) -> t
        whenValue <= today -> t.copy(schedule = Schedule.ANYTIME, scheduledDate = today)
        else -> t.copy(schedule = Schedule.SOMEDAY, scheduledDate = whenValue)
    }
}

/**
 * Applies a [TaskPatch] to an item, including the move invariants Things keeps: a task lives
 * under a project *or* an area, never both, and a heading only makes sense inside its project.
 */
fun applyTaskPatch(item: Item, patch: TaskPatch, today: DateStr): Item {
    var t = item
    patch.title.ifPresent { t = t.copy(title = it) }
    patch.note.ifPresent { t = t.copy(note = it) }
    patch.deadline.ifPresent { t = t.copy(deadline = it) }
    patch.projectId.ifPresent { project ->
        t = t.copy(projectId = project)
        // Leaving a project leaves its headings behind; joining one leaves the bare area.
        if (project == null) t = t.copy(headingId = null) else t = t.copy(areaId = null)
    }
    patch.headingId.ifPresent { t = t.copy(headingId = it) }
    patch.areaId.ifPresent { area ->
        t = t.copy(areaId = area)
        if (area != null) t = t.copy(projectId = null, headingId = null)
    }
    patch.tagIds.ifPresent { t = t.copy(tagIds = it) }
    patch.whenValue.ifPresent { t = applyWhen(t, it, today) }
    return t
}

/** The fields of POST /api/tasks/edit. Absent means unchanged; "none" clears. */
data class EditFields(
    val uuid: String,
    val title: String? = null,
    val note: String? = null,
    val whenValue: String? = null,
    val deadline: String? = null,
    val project: String? = null,
    val heading: String? = null,
    val area: String? = null,
    val tags: String? = null,
) {
    /** Every id this edit names, so a queued edit can be rewritten when a temp id resolves. */
    fun referencedIds(): Set<String> = setOfNotNull(
        uuid,
        project?.takeIf { it != NONE },
        heading?.takeIf { it != NONE },
        area?.takeIf { it != NONE },
    )

    fun rewriteIds(map: Map<String, String>): EditFields = copy(
        uuid = map[uuid] ?: uuid,
        project = project?.let { map[it] ?: it },
        heading = heading?.let { map[it] ?: it },
        area = area?.let { map[it] ?: it },
    )

    companion object {
        const val NONE = "none"
    }
}

/**
 * Turns a patch into the wire request.
 *
 * Two conventions of the backend are load-bearing here. An empty string means "leave alone", so
 * a title emptied on screen is simply not sent and the server keeps its "New To-Do" — the web
 * client does the same. And the literal "none" is how every other field is cleared; there is no
 * null on this wire.
 */
fun TaskPatch.toEditFields(uuid: String): EditFields = EditFields(
    uuid = uuid,
    title = title.orNull()?.takeIf { it.isNotEmpty() },
    note = note.orNull()?.let { it.ifEmpty { EditFields.NONE } },
    whenValue = whenValue.orNull(),
    deadline = if (deadline is Opt.Value) (deadline.value ?: EditFields.NONE) else null,
    project = if (projectId is Opt.Value) (projectId.value ?: EditFields.NONE) else null,
    heading = if (headingId is Opt.Value) (headingId.value ?: EditFields.NONE) else null,
    area = if (areaId is Opt.Value) (areaId.value ?: EditFields.NONE) else null,
    tags = if (tagIds is Opt.Value) tagIds.value.joinToString(",").ifEmpty { EditFields.NONE } else null,
)

/** A `tmp-` id is a row this device invented and the server has not acknowledged yet. */
fun newTempId(): String = "tmp-" + java.util.UUID.randomUUID().toString()

fun isTempId(id: String): Boolean = id.startsWith("tmp-")

/**
 * The row a new to-do gets on screen before the server answers.
 *
 * `index` is deliberately the largest representable value so the row sorts last in a project
 * until a real index arrives with the next snapshot. The default `when` follows the web client:
 * anything created inside a project or an area starts in Anytime, everything else in the Inbox.
 */
fun newProvisionalTask(
    tempId: String,
    init: NewTaskInit,
    today: DateStr,
    now: String = Instant.now().toString(),
): Item {
    val task = Item(
        id = tempId,
        type = ItemType.TASK,
        title = init.title,
        note = init.note.orEmpty(),
        status = Status.OPEN,
        schedule = Schedule.ANYTIME,
        areaId = init.area,
        projectId = init.project,
        headingId = init.heading,
        tagIds = init.tags,
        index = Int.MAX_VALUE,
        todayIndex = Int.MAX_VALUE,
        createdAt = now,
        provisional = true,
    )
    val whenValue = init.whenValue
        ?: if (init.project != null || init.area != null) "anytime" else "inbox"
    return applyWhen(task, whenValue, today)
}
