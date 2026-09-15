package us.liyifan.things.model

/**
 * The domain model, which is deliberately the backend's wire shape (sync/snapshot.go in the
 * things-cloud fork, mirrored by things-web's src/api/types.ts).
 *
 * Keeping one shape end to end is the point: the server decides which built-in list a task is
 * in, and every place this app re-derives that instead is a place the two can disagree. The
 * history of the backend is a catalogue of exactly that bug.
 */

/** 0 task, 1 project, 2 heading — Things keeps all three in one table. */
object ItemType {
    const val TASK = 0
    const val PROJECT = 1
    const val HEADING = 2
}

/** 0 open, 2 cancelled, 3 completed. There is no 1. */
object Status {
    const val OPEN = 0
    const val CANCELLED = 2
    const val COMPLETED = 3
}

/** Things' `start` field: 0 inbox, 1 anytime/started, 2 someday/deferred. Not the view. */
object Schedule {
    const val INBOX = 0
    const val ANYTIME = 1
    const val SOMEDAY = 2
}

enum class ViewId(val wire: String) {
    INBOX("inbox"), TODAY("today"), UPCOMING("upcoming"), ANYTIME("anytime"), SOMEDAY("someday");

    companion object {
        fun fromWire(s: String): ViewId? = entries.firstOrNull { it.wire == s }
    }
}

data class Area(val id: String, val title: String, val index: Int)

data class Tag(
    val id: String,
    val title: String,
    val shortcut: String? = null,
    val parentId: String? = null,
    val index: Int,
)

/** A task, project or heading; [type] tells them apart. */
data class Item(
    val id: String,
    val type: Int,
    val title: String,
    val note: String = "",
    val status: Int = Status.OPEN,
    val schedule: Int = Schedule.ANYTIME,
    val scheduledDate: DateStr? = null,
    val deadline: DateStr? = null,
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
    /** True while this row exists only locally, under a `tmp-` id, waiting on the server. */
    val provisional: Boolean = false,
) {
    val isTask get() = type == ItemType.TASK
    val isProject get() = type == ItemType.PROJECT
    val isHeading get() = type == ItemType.HEADING
    val isOpen get() = status == Status.OPEN
}

data class ChecklistItem(
    val id: String,
    val taskId: String,
    val title: String,
    val status: Int = Status.OPEN,
    val index: Int = 0,
    val provisional: Boolean = false,
)

/** Ordered task ids per built-in list, exactly as the server ordered them. */
data class Views(
    val inbox: List<String> = emptyList(),
    val today: List<String> = emptyList(),
    val upcoming: List<String> = emptyList(),
    val anytime: List<String> = emptyList(),
    val someday: List<String> = emptyList(),
) {
    operator fun get(v: ViewId): List<String> = when (v) {
        ViewId.INBOX -> inbox
        ViewId.TODAY -> today
        ViewId.UPCOMING -> upcoming
        ViewId.ANYTIME -> anytime
        ViewId.SOMEDAY -> someday
    }

    fun with(v: ViewId, ids: List<String>): Views = when (v) {
        ViewId.INBOX -> copy(inbox = ids)
        ViewId.TODAY -> copy(today = ids)
        ViewId.UPCOMING -> copy(upcoming = ids)
        ViewId.ANYTIME -> copy(anytime = ids)
        ViewId.SOMEDAY -> copy(someday = ids)
    }

    fun map(f: (ViewId, List<String>) -> List<String>): Views =
        ViewId.entries.fold(this) { acc, v -> acc.with(v, f(v, acc[v])) }
}

/** One read of the whole open world. Completed work and trash are fetched separately. */
data class Snapshot(
    val generatedAt: String = "",
    val timezone: String = "",
    /** Today's date as the *server* reckons it, in THINGS_TZ. Views were built against this. */
    val today: DateStr,
    val areas: List<Area> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val projects: List<Item> = emptyList(),
    val headings: List<Item> = emptyList(),
    val tasks: List<Item> = emptyList(),
    val checklist: List<ChecklistItem> = emptyList(),
    val views: Views = Views(),
    /** Completed-or-cancelled count per project, for the progress pies. */
    val projectDone: Map<String, Int> = emptyMap(),
)

/** What a new to-do is created with. */
data class NewTaskInit(
    val title: String = "",
    val note: String? = null,
    val whenValue: String? = null,
    val project: String? = null,
    val heading: String? = null,
    val area: String? = null,
    val tags: List<String> = emptyList(),
)

/**
 * A field-level patch. [Opt] separates "leave this alone" from "set it to null", which the
 * wire needs too: an absent field means unchanged, and the literal string "none" clears.
 */
sealed interface Opt<out T> {
    data object Absent : Opt<Nothing>
    data class Value<T>(val value: T) : Opt<T>

    companion object {
        fun <T> of(value: T): Opt<T> = Value(value)
    }
}

fun <T> Opt<T>.orNull(): T? = (this as? Opt.Value)?.value
inline fun <T> Opt<T>.ifPresent(f: (T) -> Unit) { if (this is Opt.Value) f(value) }

data class TaskPatch(
    val title: Opt<String> = Opt.Absent,
    val note: Opt<String> = Opt.Absent,
    val whenValue: Opt<String> = Opt.Absent,
    val deadline: Opt<DateStr?> = Opt.Absent,
    val projectId: Opt<String?> = Opt.Absent,
    val headingId: Opt<String?> = Opt.Absent,
    val areaId: Opt<String?> = Opt.Absent,
    val tagIds: Opt<List<String>> = Opt.Absent,
) {
    val isEmpty: Boolean
        get() = listOf(title, note, whenValue, deadline, projectId, headingId, areaId, tagIds)
            .all { it is Opt.Absent }
}
