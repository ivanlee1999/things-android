package us.liyifan.things.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TODAY = "2026-09-15"

private fun task(
    id: String,
    title: String = id,
    index: Int = 0,
    projectId: String? = null,
    headingId: String? = null,
    areaId: String? = null,
    scheduledDate: DateStr? = null,
    tagIds: List<String> = emptyList(),
    note: String = "",
    completedAt: String? = null,
) = Item(
    id = id, type = ItemType.TASK, title = title, index = index, projectId = projectId,
    headingId = headingId, areaId = areaId, scheduledDate = scheduledDate, tagIds = tagIds,
    note = note, completedAt = completedAt,
)

private fun project(id: String, title: String = id, index: Int = 0, areaId: String? = null) =
    Item(id = id, type = ItemType.PROJECT, title = title, index = index, areaId = areaId)

private fun heading(id: String, projectId: String, title: String = id, index: Int = 0) =
    Item(id = id, type = ItemType.HEADING, title = title, index = index, projectId = projectId)

class DeriveTest {

    private val snapshot = Snapshot(
        today = TODAY,
        areas = listOf(Area("home", "Home", 0), Area("work", "Work", 1)),
        tags = listOf(Tag("t1", "errand", index = 0), Tag("t2", "call", index = 1)),
        projects = listOf(
            project("pw", "Launch", index = 0, areaId = "work"),
            project("ph", "Garden", index = 1, areaId = "home"),
            project("pl", "Loose", index = 2),
        ),
        headings = listOf(heading("h1", "pw", "First", index = 1), heading("h0", "pw", "Zeroth", index = 0)),
        tasks = listOf(
            task("a", "Alpha", index = 2, projectId = "pw", headingId = "h1", tagIds = listOf("t1")),
            task("b", "Beta", index = 1, projectId = "pw"),
            task("c", "Gamma", index = 0, areaId = "home"),
            task("d", "Delta", index = 3, note = "buy the special milk"),
        ),
        checklist = listOf(
            ChecklistItem("c2", "a", "second", index = 1),
            ChecklistItem("c1", "a", "first", index = 0),
        ),
        views = Views(inbox = listOf("d"), anytime = listOf("b", "a", "c")),
        projectDone = mapOf("pw" to 3),
    )
    private val m = buildModel(snapshot)

    @Test fun `projects split into area buckets and loose ones, in index order`() {
        assertEquals(listOf("pl"), m.looseProjects.map { it.id })
        assertEquals(listOf("pw"), m.projectsByArea["work"]?.map { it.id })
        assertEquals(listOf("ph"), m.projectsByArea["home"]?.map { it.id })
    }

    @Test fun `headings and tasks sort by index, not by insertion`() {
        assertEquals(listOf("h0", "h1"), m.headingsByProject["pw"]?.map { it.id })
        assertEquals(listOf("b", "a"), m.tasksByProject["pw"]?.map { it.id })
        assertEquals(listOf("c"), m.tasksByArea["home"]?.map { it.id })
        assertEquals(listOf("c1", "c2"), m.checklistByTask["a"]?.map { it.id })
    }

    @Test fun `views keep the server's order and are resolved to items`() {
        assertEquals(listOf("b", "a", "c"), m.view(ViewId.ANYTIME).map { it.id })
        assertEquals(ViewId.INBOX, m.listOf["d"])
        assertEquals(ViewId.ANYTIME, m.listOf["a"])
        assertNull(m.listOf["nope"])
    }

    @Test fun `a view id with no task is dropped rather than crashing`() {
        val ghost = buildModel(snapshot.copy(views = Views(today = listOf("gone", "a"))))
        assertEquals(listOf("a"), ghost.view(ViewId.TODAY).map { it.id })
    }

    @Test fun `project progress is done over done plus open`() {
        assertEquals(0.6f, m.projectProgress("pw"), 0.001f) // 3 done, 2 open
        assertEquals(0.5f, m.projectProgress("pw", loggedCount = 2), 0.001f)
        assertEquals(0f, m.projectProgress("ph"), 0.001f)
        assertEquals(0f, m.projectProgress("nothing"), 0.001f)
    }

    @Test fun `breadcrumbs read project then heading, or the area alone`() {
        assertEquals(listOf("Launch", "First"), m.breadcrumb(m.tasksById.getValue("a")))
        assertEquals(listOf("Launch"), m.breadcrumb(m.tasksById.getValue("b")))
        assertEquals(listOf("Home"), m.breadcrumb(m.tasksById.getValue("c")))
        assertEquals(emptyList<String>(), m.breadcrumb(m.tasksById.getValue("d")))
    }

    @Test fun `tagsIn returns only present tags, in tag order`() {
        assertEquals(listOf("t1"), m.tagsIn(m.snapshot.tasks).map { it.id })
        assertEquals(emptyList<Tag>(), m.tagsIn(listOf(m.tasksById.getValue("b"))))
    }

    @Test fun `filterByTag is a no-op without a tag`() {
        val all = m.snapshot.tasks
        assertEquals(all, filterByTag(all, null))
        assertEquals(listOf("a"), filterByTag(all, "t1").map { it.id })
    }

    @Test fun `search covers titles and notes and is case-insensitive`() {
        assertEquals(listOf("d"), m.search("MILK").tasks.map { it.id })
        assertEquals(listOf("a"), m.search("alph").tasks.map { it.id })
        assertEquals(listOf("pw"), m.search("launch").projects.map { it.id })
        assertEquals(listOf("home"), m.search("hom").areas.map { it.id })
        assertTrue(m.search("   ").isEmpty)
    }

    @Test fun `groupByProject puts loose tasks first, then areas in sidebar order`() {
        val groups = m.groupByProject(listOf(
            m.tasksById.getValue("a"), // project pw, area work
            m.tasksById.getValue("c"), // area home
            m.tasksById.getValue("d"), // nothing
        ))
        assertEquals(listOf("none", "a:home", "p:pw"), groups.map { it.key })
        assertEquals("Home", groups[1].area?.title)
        assertNull(groups[1].project)
        // A task in a project inherits that project's area for grouping.
        assertEquals("Work", groups[2].area?.title)
        assertEquals("Launch", groups[2].project?.title)
    }

    @Test fun `upcoming seeds seven days and buckets the rest by month`() {
        val items = listOf(
            task("u1", scheduledDate = addDays(TODAY, 1)),
            task("u2", scheduledDate = addDays(TODAY, 7)),
            task("u3", scheduledDate = addDays(TODAY, 8)),
            task("u4", scheduledDate = "2026-11-02"),
            task("u5", scheduledDate = "2026-11-20"),
        )
        val groups = m.upcomingGroups(items)
        assertEquals(7, groups.days.size)
        assertEquals(addDays(TODAY, 1), groups.days.first().date)
        assertEquals(listOf("u1"), groups.days.first().tasks.map { it.id })
        assertEquals(listOf("u2"), groups.days.last().tasks.map { it.id })
        assertEquals(listOf("2026-09", "2026-11"), groups.months.map { it.month })
        assertEquals(listOf("u4", "u5"), groups.months[1].tasks.map { it.id })
    }

    @Test fun `project sections separate unheaded rows from each heading`() {
        val tasks = m.tasksByProject.getValue("pw")
        val sections = m.projectSections("pw", tasks)
        assertEquals(listOf("b"), sections.unheaded.map { it.id })
        assertEquals(listOf("h0", "h1"), sections.headed.map { it.heading.id })
        assertEquals(emptyList<Item>(), sections.headed[0].tasks)
        assertEquals(listOf("a"), sections.headed[1].tasks.map { it.id })
    }

    @Test fun `a task under a heading that no longer exists still shows in the project`() {
        val orphan = task("o", projectId = "pw", headingId = "gone")
        val sections = m.projectSections("pw", listOf(orphan))
        assertEquals(listOf("o"), sections.unheaded.map { it.id })
    }

    @Test fun `a task whose heading belongs to another project is not lost`() {
        // The mirror can hold a task in one project under a heading in another. Matching on
        // "does this heading exist anywhere" would put it in no section at all, and it would
        // disappear from the screen rather than appear in the wrong place.
        val model = buildModel(
            snapshot.copy(
                headings = snapshot.headings + heading("hOther", "ph", "Elsewhere"),
                tasks = listOf(task("stray", projectId = "pw", headingId = "hOther")),
            ),
        )
        val sections = model.projectSections("pw", model.tasksByProject.getValue("pw"))
        assertEquals(listOf("stray"), sections.unheaded.map { it.id })
        assertTrue(sections.headed.all { it.tasks.isEmpty() })
    }

    @Test fun `logbook groups by local completion day and keeps undated rows`() {
        val groups = logbookGroups(listOf(
            task("l1", completedAt = "2026-09-15T20:00:00Z"),
            task("l2", completedAt = "2026-09-15T21:00:00Z"),
            task("l3", completedAt = null),
        ))
        assertEquals(2, groups.size)
        assertEquals(listOf("l1", "l2"), groups[0].tasks.map { it.id })
        assertEquals(UNKNOWN_DAY, groups[1].date)
    }
}
