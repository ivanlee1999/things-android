package us.liyifan.things.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Expectations taken from things-web's src/model/dates.ts, which this file ports. */
class DatesTest {

    @Test fun `addDays crosses months and years`() {
        assertEquals("2026-10-01", addDays("2026-09-30", 1))
        assertEquals("2027-01-01", addDays("2026-12-31", 1))
        assertEquals("2026-02-28", addDays("2026-03-01", -1)) // 2026 is not a leap year
    }

    @Test fun `daysBetween is signed and whole`() {
        assertEquals(1, daysBetween("2026-09-15", "2026-09-16"))
        assertEquals(-1, daysBetween("2026-09-16", "2026-09-15"))
        assertEquals(0, daysBetween("2026-09-15", "2026-09-15"))
        assertEquals(365, daysBetween("2026-01-01", "2027-01-01"))
    }

    @Test fun `relativeDay matches the web wording`() {
        val today = "2026-09-15" // a Tuesday
        assertEquals("Today", relativeDay("2026-09-15", today))
        assertEquals("Tomorrow", relativeDay("2026-09-16", today))
        assertEquals("Yesterday", relativeDay("2026-09-14", today))
        assertEquals("Friday", relativeDay("2026-09-18", today))
        // Seven days out is no longer a weekday name, it is a date.
        assertEquals("22 Sep", relativeDay("2026-09-22", today))
        assertEquals("1 Jan 2027", relativeDay("2027-01-01", today))
    }

    @Test fun `shortDate carries the year only across a year boundary`() {
        assertEquals("12 Sep", shortDate("2026-09-12", "2026-09-15"))
        assertEquals("12 Sep 2025", shortDate("2025-09-12", "2026-09-15"))
    }

    @Test fun `deadlineLabel phrasing and flags`() {
        val today = "2026-09-15"
        assertEquals(DeadlineLabel("1 day ago", true, false), deadlineLabel("2026-09-14", today))
        assertEquals(DeadlineLabel("3 days ago", true, false), deadlineLabel("2026-09-12", today))
        assertEquals(DeadlineLabel("today", false, true), deadlineLabel("2026-09-15", today))
        assertEquals(DeadlineLabel("tomorrow", false, true), deadlineLabel("2026-09-16", today))
        assertEquals(DeadlineLabel("3 days left", false, true), deadlineLabel("2026-09-18", today))
        assertEquals(DeadlineLabel("4 days left", false, false), deadlineLabel("2026-09-19", today))
        assertEquals(DeadlineLabel("13 days left", false, false), deadlineLabel("2026-09-28", today))
        assertEquals(DeadlineLabel("29 Sep", false, false), deadlineLabel("2026-09-29", today))
    }

    @Test fun `monthGrid is Monday-first and covers the month`() {
        // September 2026 starts on a Tuesday, so one leading day from August.
        val cells = monthGrid("2026-09-15")
        assertEquals("2026-08-31", cells.first().cursor)
        assertTrue(cells.size == 35 || cells.size == 42)
        assertEquals(30, cells.count { it.inMonth })
        assertEquals(0, cells.size % 7)
        // Every cell is one day after the last.
        cells.zipWithNext().forEach { (a, b) -> assertEquals(b.cursor, addDays(a.cursor, 1)) }
    }

    @Test fun `monthGrid drops a trailing week that belongs wholly to the next month`() {
        // February 2027 starts on a Monday and has 28 days, so the raw 42-cell grid ends with two
        // whole March weeks. The web drops trailing empty weeks only while more than 35 cells
        // remain, which leaves five rows and a stable calendar height; this port keeps that.
        val cells = monthGrid("2027-02-10")
        assertEquals("2027-02-01", cells.first().cursor)
        assertEquals(35, cells.size)
        assertTrue(cells.takeLast(7).none { it.inMonth })
    }

    @Test fun `shiftMonth lands on the first and clamps month length`() {
        assertEquals("2026-10-01", shiftMonth("2026-09-15", 1))
        assertEquals("2026-08-01", shiftMonth("2026-09-15", -1))
        assertEquals("2027-01-01", shiftMonth("2026-12-31", 1))
    }

    @Test fun `weekday and month names`() {
        assertEquals("Tuesday", weekdayName("2026-09-15"))
        assertEquals("Tue", weekdayName("2026-09-15", short = true))
        assertEquals("September", monthName("2026-09-15"))
        assertEquals("Sep", monthName("2026-09-15", short = true))
        assertEquals("September 2026", monthTitle("2026-09-15"))
    }

    @Test fun `instantToLocalDate uses the given zone`() {
        val iso = "2026-09-16T04:30:00Z"
        assertEquals("2026-09-15", instantToLocalDate(iso, ZoneId.of("America/Los_Angeles")))
        assertEquals("2026-09-16", instantToLocalDate(iso, ZoneId.of("UTC")))
        assertEquals("21:30", timeOfDay(iso, ZoneId.of("America/Los_Angeles")))
    }

    @Test fun `bad input does not throw`() {
        assertNull(parseDate(""))
        assertNull(instantToLocalDate("not a date"))
        assertNull(timeOfDay(""))
    }
}
