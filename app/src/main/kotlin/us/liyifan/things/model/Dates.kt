package us.liyifan.things.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Calendar-date helpers, a port of things-web's src/model/dates.ts.
 *
 * A Things date is a day ("2026-09-02"), never an instant, so everything here works on
 * YYYY-MM-DD strings. Instants (created/modified/completed) are RFC 3339 and only ever get
 * converted to a day for Logbook grouping, deliberately in the device's own zone: the server
 * groups by THINGS_TZ, and a Logbook read at 01:00 should say "Yesterday" where the reader is.
 */

/** A calendar day, "YYYY-MM-DD". */
typealias DateStr = String

private val WEEKDAYS = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
)
private val WEEKDAYS_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val MONTHS_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

fun LocalDate.toDateStr(): DateStr = toString()

/** Parses a wire date. Returns null rather than throwing: the mirror holds some very old rows. */
fun parseDate(s: DateStr): LocalDate? = try {
    LocalDate.parse(s)
} catch (_: DateTimeParseException) {
    null
}

// LocalDate.EPOCH is API 34; the rest of java.time is API 26, and minSdk here is 29.
private val EPOCH: LocalDate = LocalDate.of(1970, 1, 1)

private fun date(s: DateStr): LocalDate = parseDate(s) ?: EPOCH

fun localToday(zone: ZoneId = ZoneId.systemDefault()): DateStr = LocalDate.now(zone).toDateStr()

fun addDays(s: DateStr, n: Long): DateStr = date(s).plusDays(n).toDateStr()

/** Whole days from [from] to [to]; negative when [to] is earlier. */
fun daysBetween(from: DateStr, to: DateStr): Int =
    (date(to).toEpochDay() - date(from).toEpochDay()).toInt()

fun weekdayName(s: DateStr, short: Boolean = false): String {
    val i = date(s).dayOfWeek.value - 1 // DayOfWeek is Monday=1
    return if (short) WEEKDAYS_SHORT[i] else WEEKDAYS[i]
}

fun monthName(s: DateStr, short: Boolean = false): String {
    val i = date(s).monthValue - 1
    return if (short) MONTHS_SHORT[i] else MONTHS[i]
}

fun dayOfMonth(s: DateStr): Int = date(s).dayOfMonth

fun yearOf(s: DateStr): Int = date(s).year

/** "12 Sep", carrying the year only when it differs from today's. */
fun shortDate(s: DateStr, today: DateStr): String {
    val year = if (yearOf(s) != yearOf(today)) " ${yearOf(s)}" else ""
    return "${dayOfMonth(s)} ${monthName(s, short = true)}$year"
}

/** "Today", "Tomorrow", "Yesterday", a weekday within the week, else "12 Sep". */
fun relativeDay(s: DateStr, today: DateStr): String = when (val n = daysBetween(today, s)) {
    0 -> "Today"
    1 -> "Tomorrow"
    -1 -> "Yesterday"
    else -> if (n > 1 && n < 7) weekdayName(s) else shortDate(s, today)
}

data class DeadlineLabel(val text: String, val overdue: Boolean, val soon: Boolean)

/** Deadline phrasing, as Things puts it under the flag. */
fun deadlineLabel(deadline: DateStr, today: DateStr): DeadlineLabel {
    val n = daysBetween(today, deadline)
    return when {
        n < 0 -> DeadlineLabel("${-n} day${if (n == -1) "" else "s"} ago", overdue = true, soon = false)
        n == 0 -> DeadlineLabel("today", overdue = false, soon = true)
        n == 1 -> DeadlineLabel("tomorrow", overdue = false, soon = true)
        n < 14 -> DeadlineLabel("$n days left", overdue = false, soon = n <= 3)
        else -> DeadlineLabel(shortDate(deadline, today), overdue = false, soon = false)
    }
}

data class MonthCell(val cursor: DateStr, val inMonth: Boolean)

/** The dates drawn for the month containing [s], Monday first, trailing empty week dropped. */
fun monthGrid(s: DateStr): List<MonthCell> {
    val first = date(s).withDayOfMonth(1)
    val lead = (first.dayOfWeek.value - 1).toLong() // Monday-first offset
    val start = first.minusDays(lead)
    val cells = (0 until 42).map { i ->
        val d = start.plusDays(i.toLong())
        MonthCell(d.toDateStr(), d.monthValue == first.monthValue && d.year == first.year)
    }.toMutableList()
    // A trailing week belonging entirely to the next month is drawn by nobody.
    while (cells.size > 35 && cells.takeLast(7).none { it.inMonth }) {
        repeat(7) { cells.removeAt(cells.size - 1) }
    }
    return cells
}

fun monthTitle(s: DateStr): String = "${monthName(s)} ${yearOf(s)}"

fun shiftMonth(s: DateStr, n: Long): DateStr = date(s).withDayOfMonth(1).plusMonths(n).toDateStr()

/** The local day an RFC 3339 instant falls on — the Logbook's grouping key. */
fun instantToLocalDate(iso: String, zone: ZoneId = ZoneId.systemDefault()): DateStr? = try {
    Instant.parse(iso).atZone(zone).toLocalDate().toDateStr()
} catch (_: Exception) {
    null
}

fun timeOfDay(iso: String, zone: ZoneId = ZoneId.systemDefault()): String? = try {
    val t = Instant.parse(iso).atZone(zone).toLocalTime()
    "${t.hour}:${t.minute.toString().padStart(2, '0')}"
} catch (_: Exception) {
    null
}
