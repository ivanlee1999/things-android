package us.liyifan.things.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TODAY = "2026-09-15"

private fun task() = Item(id = "t", type = ItemType.TASK, title = "t")

class PatcherTest {

    @Test fun `named when values`() {
        val t = task().copy(schedule = Schedule.INBOX)
        applyWhen(t, "today", TODAY).let {
            assertEquals(Schedule.ANYTIME, it.schedule); assertEquals(TODAY, it.scheduledDate)
        }
        applyWhen(t, "anytime", TODAY).let {
            assertEquals(Schedule.ANYTIME, it.schedule); assertNull(it.scheduledDate)
        }
        applyWhen(t, "someday", TODAY).let {
            assertEquals(Schedule.SOMEDAY, it.schedule); assertNull(it.scheduledDate)
        }
        applyWhen(t, "inbox", TODAY).let {
            assertEquals(Schedule.INBOX, it.schedule); assertNull(it.scheduledDate)
        }
    }

    @Test fun `none clears the date and leaves the schedule alone`() {
        val t = task().copy(schedule = Schedule.SOMEDAY, scheduledDate = "2026-12-01")
        val after = applyWhen(t, "none", TODAY)
        assertNull(after.scheduledDate)
        assertEquals(Schedule.SOMEDAY, after.schedule)
    }

    @Test fun `a future date is stored as someday with a date`() {
        val after = applyWhen(task(), "2026-12-01", TODAY)
        assertEquals(Schedule.SOMEDAY, after.schedule)
        assertEquals("2026-12-01", after.scheduledDate)
        assertEquals(ViewId.UPCOMING, classify(after, TODAY))
    }

    @Test fun `a past date is pulled forward to today`() {
        val after = applyWhen(task(), "2026-01-01", TODAY)
        assertEquals(Schedule.ANYTIME, after.schedule)
        assertEquals(TODAY, after.scheduledDate)
        assertEquals(ViewId.TODAY, classify(after, TODAY))
    }

    @Test fun `an unparseable when is ignored`() {
        val t = task().copy(scheduledDate = "2026-10-10")
        assertEquals(t, applyWhen(t, "next tuesday", TODAY))
    }

    @Test fun `moving into a project clears the area`() {
        val t = task().copy(areaId = "area1")
        val after = applyTaskPatch(t, TaskPatch(projectId = Opt.of("p1")), TODAY)
        assertEquals("p1", after.projectId)
        assertNull(after.areaId)
    }

    @Test fun `leaving a project drops its heading`() {
        val t = task().copy(projectId = "p1", headingId = "h1")
        val after = applyTaskPatch(t, TaskPatch(projectId = Opt.of(null)), TODAY)
        assertNull(after.projectId)
        assertNull(after.headingId)
    }

    @Test fun `moving into an area clears project and heading`() {
        val t = task().copy(projectId = "p1", headingId = "h1")
        val after = applyTaskPatch(t, TaskPatch(areaId = Opt.of("a1")), TODAY)
        assertEquals("a1", after.areaId)
        assertNull(after.projectId)
        assertNull(after.headingId)
    }

    @Test fun `an absent field is left alone`() {
        val t = task().copy(title = "keep", note = "note", deadline = "2026-10-01")
        assertEquals(t, applyTaskPatch(t, TaskPatch(), TODAY))
        assertTrue(TaskPatch().isEmpty)
        assertFalse(TaskPatch(title = Opt.of("x")).isEmpty)
    }

    @Test fun `edit fields use none to clear and omit an emptied title`() {
        val fields = TaskPatch(
            title = Opt.of(""),
            note = Opt.of(""),
            deadline = Opt.of(null),
            projectId = Opt.of(null),
            tagIds = Opt.of(emptyList()),
        ).toEditFields("u1")
        // An empty title is not sent: the server would keep "New To-Do" either way, and sending
        // an empty string means "leave alone" on this wire.
        assertNull(fields.title)
        assertEquals("none", fields.note)
        assertEquals("none", fields.deadline)
        assertEquals("none", fields.project)
        assertEquals("none", fields.tags)
        assertNull(fields.area)
    }

    @Test fun `edit fields carry values through`() {
        val fields = TaskPatch(
            title = Opt.of("Buy milk"),
            whenValue = Opt.of("today"),
            deadline = Opt.of("2026-10-01"),
            areaId = Opt.of("a1"),
            tagIds = Opt.of(listOf("tag1", "tag2")),
        ).toEditFields("u1")
        assertEquals("Buy milk", fields.title)
        assertEquals("today", fields.whenValue)
        assertEquals("2026-10-01", fields.deadline)
        assertEquals("a1", fields.area)
        assertEquals("tag1,tag2", fields.tags)
    }

    @Test fun `edit fields name and rewrite the ids they reference`() {
        val fields = TaskPatch(projectId = Opt.of("tmp-p"), areaId = Opt.of(null))
            .toEditFields("tmp-t")
        assertEquals(setOf("tmp-t", "tmp-p"), fields.referencedIds())
        val rewritten = fields.rewriteIds(mapOf("tmp-t" to "T", "tmp-p" to "P"))
        assertEquals("T", rewritten.uuid)
        assertEquals("P", rewritten.project)
        assertEquals("none", rewritten.area) // "none" is a keyword, not an id
    }

    @Test fun `a new to-do inside a project starts in Anytime, elsewhere in the Inbox`() {
        val inProject = newProvisionalTask("tmp-1", NewTaskInit(title = "a", project = "p1"), TODAY)
        assertEquals(Schedule.ANYTIME, inProject.schedule)
        assertEquals(ViewId.ANYTIME, classify(inProject, TODAY))
        assertTrue(inProject.provisional)
        assertEquals(Int.MAX_VALUE, inProject.index)

        val loose = newProvisionalTask("tmp-2", NewTaskInit(title = "b"), TODAY)
        assertEquals(Schedule.INBOX, loose.schedule)
        assertEquals(ViewId.INBOX, classify(loose, TODAY))
    }

    @Test fun `an explicit when beats the default`() {
        val t = newProvisionalTask("tmp-3", NewTaskInit(title = "c", whenValue = "today"), TODAY)
        assertEquals(TODAY, t.scheduledDate)
        assertEquals(ViewId.TODAY, classify(t, TODAY))
    }

    @Test fun `temp ids are recognisable`() {
        assertTrue(isTempId(newTempId()))
        assertFalse(isTempId("ud5VEgS2ApfLrrD9tTeve"))
    }
}
