package us.liyifan.things.model

/**
 * Indexes and groupings over a snapshot — a port of things-web's src/model/derive.ts plus the
 * three groupings that live inline in its views (Upcoming, Logbook, a project's sections).
 *
 * Everything here is a pure function of the snapshot, so every screen can be a thin drawing of
 * one of these and the awkward ordering rules have exactly one home.
 */

/** Things' own order within a list: the manual index, then the title to break ties. */
private val byIndex = compareBy<Item> { it.index }.thenBy { it.title.lowercase() }

/**
 * Built once per snapshot and never mutated, which is worth telling Compose: every screen takes
 * one of these, and an unstable parameter means every row recomposes on any change. That costs
 * little on a phone and a visible flash on e-paper.
 */
@androidx.compose.runtime.Immutable
class Model(val snapshot: Snapshot) {
    val today: DateStr get() = snapshot.today

    val areasById: Map<String, Area> = snapshot.areas.associateBy { it.id }
    val tagsById: Map<String, Tag> = snapshot.tags.associateBy { it.id }
    val projectsById: Map<String, Item> = snapshot.projects.associateBy { it.id }
    val headingsById: Map<String, Item> = snapshot.headings.associateBy { it.id }
    val tasksById: Map<String, Item> = snapshot.tasks.associateBy { it.id }

    /** Projects with no area, in order. */
    val looseProjects: List<Item>

    /** Projects per area id, in order. */
    val projectsByArea: Map<String, List<Item>>

    init {
        val loose = mutableListOf<Item>()
        val byArea = linkedMapOf<String, MutableList<Item>>()
        snapshot.projects.sortedWith(byIndex).forEach { p ->
            val area = p.areaId
            if (area != null && areasById.containsKey(area)) {
                byArea.getOrPut(area) { mutableListOf() }.add(p)
            } else {
                loose.add(p)
            }
        }
        looseProjects = loose
        projectsByArea = byArea
    }

    /** Headings per project id, in order. */
    val headingsByProject: Map<String, List<Item>> =
        snapshot.headings.sortedWith(byIndex)
            .filter { it.projectId != null }
            .groupBy { it.projectId!! }

    /** Open tasks per project id, headed and unheaded alike, in order. */
    val tasksByProject: Map<String, List<Item>>

    /** Open tasks sitting directly in an area, with no project. */
    val tasksByArea: Map<String, List<Item>>

    init {
        val byProject = linkedMapOf<String, MutableList<Item>>()
        val byArea = linkedMapOf<String, MutableList<Item>>()
        snapshot.tasks.sortedWith(byIndex).forEach { t ->
            val project = t.projectId
            val area = t.areaId
            when {
                project != null && projectsById.containsKey(project) ->
                    byProject.getOrPut(project) { mutableListOf() }.add(t)
                area != null && areasById.containsKey(area) ->
                    byArea.getOrPut(area) { mutableListOf() }.add(t)
            }
        }
        tasksByProject = byProject
        tasksByArea = byArea
    }

    val checklistByTask: Map<String, List<ChecklistItem>> =
        snapshot.checklist.sortedBy { it.index }.groupBy { it.taskId }

    /** The built-in lists, resolved to items, in the order the server gave. */
    val views: Map<ViewId, List<Item>> =
        ViewId.entries.associateWith { v -> snapshot.views[v].mapNotNull { tasksById[it] } }

    /** Which built-in list a task is in, for the widget and for deep links. */
    val listOf: Map<String, ViewId> = buildMap {
        views.forEach { (v, items) -> items.forEach { put(it.id, v) } }
    }

    fun view(v: ViewId): List<Item> = views[v].orEmpty()

    /** The item behind an id whatever its type — rows, projects and headings share a table. */
    fun item(id: String): Item? = tasksById[id] ?: projectsById[id] ?: headingsById[id]

    /** Project completion as Things draws its pie: done / (done + open). */
    fun projectProgress(projectId: String, loggedCount: Int? = null): Float {
        val open = tasksByProject[projectId]?.size ?: 0
        val done = loggedCount ?: snapshot.projectDone[projectId] ?: 0
        val total = open + done
        return if (total == 0) 0f else done.toFloat() / total
    }

    /** ["Project"], ["Project", "Heading"] or ["Area"] — the grey line under a title. */
    fun breadcrumb(t: Item): List<String> = buildList {
        val project = t.projectId?.let { projectsById[it] }
        if (t.projectId != null) {
            project?.let { add(it.title) }
            t.headingId?.let { h -> headingsById[h]?.let { add(it.title) } }
        } else {
            t.areaId?.let { a -> areasById[a]?.let { add(it.title) } }
        }
    }

    /** The tags present on these items, in tag order, for the filter bar. */
    fun tagsIn(items: List<Item>): List<Tag> {
        val seen = items.flatMapTo(mutableSetOf()) { it.tagIds }
        return snapshot.tags.filter { it.id in seen }
    }

    /**
     * Quick Find: titles and notes, capped so a one-letter query cannot render the world.
     */
    fun search(q: String): SearchResult {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) return SearchResult()
        fun hit(s: String) = s.lowercase().contains(needle)
        return SearchResult(
            tasks = snapshot.tasks.filter { hit(it.title) || hit(it.note) }.take(SEARCH_LIMIT),
            projects = snapshot.projects.filter { hit(it.title) },
            areas = snapshot.areas.filter { hit(it.title) },
        )
    }

    /**
     * How Anytime and Someday break a flat list up: one section per project, or per area for a
     * task that has no project, and the project-less tasks first.
     */
    fun groupByProject(items: List<Item>): List<ProjectGroup> {
        val groups = linkedMapOf<String, MutableList<Item>>()
        val meta = mutableMapOf<String, Pair<Area?, Item?>>()
        items.forEach { t ->
            val project = t.projectId?.let { projectsById[it] }
            // A task's area is its project's, and only then its own: a to-do in a project that
            // sits in an area belongs under that area even when the row itself names none.
            val area = (project?.areaId ?: t.areaId)?.let { areasById[it] }
            val key = when {
                project != null -> "p:${project.id}"
                area != null -> "a:${area.id}"
                else -> "none"
            }
            groups.getOrPut(key) { mutableListOf() }.add(t)
            meta.getOrPut(key) { area to project }
        }
        val areaOrder = snapshot.areas.map { it.id }
        fun rank(key: String): Pair<Int, Int> {
            val (area, project) = meta[key] ?: (null to null)
            if (area == null && project == null) return -1 to -1 // ungrouped first
            val ai = area?.let { areaOrder.indexOf(it.id) } ?: snapshot.areas.size
            val pi = project?.let {
                val siblings = if (area != null) projectsByArea[area.id].orEmpty() else looseProjects
                siblings.indexOfFirst { p -> p.id == it.id }
            } ?: -1
            return ai to pi
        }
        return groups.keys
            .sortedWith(compareBy({ rank(it).first }, { rank(it).second }))
            .map { key ->
                val (area, project) = meta.getValue(key)
                ProjectGroup(key, area, project, groups.getValue(key))
            }
    }

    /**
     * Upcoming: a header for each of the next seven days, present even when empty so the week
     * reads as a week, then one section per month for everything beyond.
     */
    fun upcomingGroups(items: List<Item>): UpcomingGroups {
        val days = linkedMapOf<DateStr, MutableList<Item>>()
        (1..7).forEach { days[addDays(today, it.toLong())] = mutableListOf() }
        val months = sortedMapOf<String, MutableList<Item>>()
        items.forEach { t ->
            val d = t.scheduledDate ?: return@forEach
            if (daysBetween(today, d) <= 7) {
                days.getOrPut(d) { mutableListOf() }.add(t)
            } else {
                months.getOrPut(d.take(7)) { mutableListOf() }.add(t)
            }
        }
        return UpcomingGroups(
            days = days.entries.sortedBy { it.key }.map { DayGroup(it.key, it.value) },
            months = months.map { (key, list) -> MonthGroup(key, list) },
        )
    }

    /** A project's rows: the unheaded ones, then each heading with its own. */
    /**
     * A project's rows: the unheaded ones, then each heading with its own.
     *
     * "Unheaded" has to mean "not under a heading *of this project*". Testing only whether the
     * heading exists at all would drop a to-do whose heading belongs somewhere else — it would
     * match no section here and simply vanish from the screen, which the mirror makes possible
     * whenever a task and its heading disagree about their project.
     */
    fun projectSections(projectId: String, tasks: List<Item>): ProjectSections {
        val headings = headingsByProject[projectId].orEmpty()
        val here = headings.mapTo(mutableSetOf()) { it.id }
        return ProjectSections(
            unheaded = tasks.filter { it.headingId == null || it.headingId !in here },
            headed = headings.map { h -> HeadingSection(h, tasks.filter { it.headingId == h.id }) },
        )
    }

    companion object {
        const val SEARCH_LIMIT = 50
    }
}

fun buildModel(snapshot: Snapshot): Model = Model(snapshot)

fun filterByTag(items: List<Item>, tagId: String?): List<Item> =
    if (tagId == null) items else items.filter { tagId in it.tagIds }

data class SearchResult(
    val tasks: List<Item> = emptyList(),
    val projects: List<Item> = emptyList(),
    val areas: List<Area> = emptyList(),
) {
    val isEmpty get() = tasks.isEmpty() && projects.isEmpty() && areas.isEmpty()
}

data class ProjectGroup(
    val key: String,
    val area: Area?,
    val project: Item?,
    val tasks: List<Item>,
)

data class DayGroup(val date: DateStr, val tasks: List<Item>)
data class MonthGroup(val month: String, val tasks: List<Item>)
data class UpcomingGroups(val days: List<DayGroup>, val months: List<MonthGroup>)

data class HeadingSection(val heading: Item, val tasks: List<Item>)
data class ProjectSections(val unheaded: List<Item>, val headed: List<HeadingSection>)

/**
 * Logbook sections, keyed by the *device's* day of completion rather than the server's: a to-do
 * finished at 22:00 is today's where the reader is standing. [unknownKey] collects rows whose
 * completion instant the mirror never recorded.
 */
fun logbookGroups(items: List<Item>): List<DayGroup> {
    val groups = linkedMapOf<String, MutableList<Item>>()
    items.forEach { t ->
        val key = t.completedAt?.let { instantToLocalDate(it) } ?: UNKNOWN_DAY
        groups.getOrPut(key) { mutableListOf() }.add(t)
    }
    return groups.map { (day, list) -> DayGroup(day, list) }
}

const val UNKNOWN_DAY = "unknown"
