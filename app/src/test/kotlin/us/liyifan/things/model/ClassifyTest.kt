package us.liyifan.things.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val TODAY = "2026-09-15"

private fun task(
    id: String = "t1",
    status: Int = Status.OPEN,
    schedule: Int = Schedule.ANYTIME,
    scheduledDate: DateStr? = null,
    inTrash: Boolean = false,
) = Item(id = id, type = ItemType.TASK, title = id, status = status, schedule = schedule,
    scheduledDate = scheduledDate, inTrash = inTrash)

/**
 * The table these assertions encode is the contract between three implementations: this file,
 * things-web's classify(), and the SQL in the backend's sync/state.go. A change here that is not
 * a change there is a bug that shows up as a to-do in the wrong list after an edit.
 */
class ClassifyTest {

    @Test fun `undated tasks fall out by schedule`() {
        assertEquals(ViewId.INBOX, classify(task(schedule = Schedule.INBOX), TODAY))
        assertEquals(ViewId.ANYTIME, classify(task(schedule = Schedule.ANYTIME), TODAY))
        assertEquals(ViewId.SOMEDAY, classify(task(schedule = Schedule.SOMEDAY), TODAY))
    }

    @Test fun `a date on or before today is Today, and overdue carries forward`() {
        assertEquals(ViewId.TODAY, classify(task(scheduledDate = TODAY), TODAY))
        assertEquals(ViewId.TODAY, classify(task(scheduledDate = "2026-09-01"), TODAY))
        // Someday plus a past date is still Today: the date wins over the schedule.
        assertEquals(
            ViewId.TODAY,
            classify(task(schedule = Schedule.SOMEDAY, scheduledDate = "2026-09-14"), TODAY),
        )
    }

    @Test fun `a future date is Upcoming whatever the schedule`() {
        assertEquals(ViewId.UPCOMING, classify(task(scheduledDate = "2026-09-16"), TODAY))
        assertEquals(
            ViewId.UPCOMING,
            classify(task(schedule = Schedule.SOMEDAY, scheduledDate = "2027-01-01"), TODAY),
        )
    }

    @Test fun `a dated Inbox row is in no list at all`() {
        assertNull(classify(task(schedule = Schedule.INBOX, scheduledDate = TODAY), TODAY))
        // But a future date still reaches Upcoming, as on the server.
        assertEquals(
            ViewId.UPCOMING,
            classify(task(schedule = Schedule.INBOX, scheduledDate = "2026-12-01"), TODAY),
        )
    }

    @Test fun `closed and trashed rows are in no list`() {
        assertNull(classify(task(status = Status.COMPLETED), TODAY))
        assertNull(classify(task(status = Status.CANCELLED), TODAY))
        assertNull(classify(task(inTrash = true), TODAY))
        assertNull(classify(task(status = Status.COMPLETED, scheduledDate = TODAY), TODAY))
    }

    @Test fun `reclassify moves a task between lists exactly once`() {
        val t = task(id = "a", schedule = Schedule.ANYTIME)
        val views = Views(anytime = listOf("x", "a", "y"))
        val moved = views.reclassify(t.copy(scheduledDate = TODAY), TODAY)
        assertEquals(listOf("x", "y"), moved.anytime)
        assertEquals(listOf("a"), moved.today)
    }

    @Test fun `Today appends and every other list prepends`() {
        val t = task(id = "a")
        val views = Views(today = listOf("x"), someday = listOf("y"))
        assertEquals(listOf("x", "a"), views.reclassify(t.copy(scheduledDate = TODAY), TODAY).today)
        assertEquals(
            listOf("a", "y"),
            views.reclassify(t.copy(schedule = Schedule.SOMEDAY), TODAY).someday,
        )
    }

    @Test fun `reclassify of a closed task just removes it`() {
        val t = task(id = "a", status = Status.COMPLETED)
        val views = Views(today = listOf("a", "b"), anytime = listOf("a"))
        val after = views.reclassify(t, TODAY)
        assertEquals(listOf("b"), after.today)
        assertEquals(emptyList<String>(), after.anytime)
    }

    @Test fun `remove and rename touch every list`() {
        val views = Views(inbox = listOf("a"), today = listOf("a", "b"), someday = listOf("c"))
        assertEquals(Views(today = listOf("b"), someday = listOf("c")), views.remove("a"))
        val renamed = views.rename("a", "real")
        assertEquals(listOf("real"), renamed.inbox)
        assertEquals(listOf("real", "b"), renamed.today)
    }
}
