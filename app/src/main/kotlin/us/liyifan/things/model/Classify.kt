package us.liyifan.things.model

/**
 * Which built-in list a task belongs to, and how the lists are rebuilt after a local edit.
 *
 * The server answers this question authoritatively in every snapshot (`views`), and this file
 * exists only so an optimistic edit lands in the right list before the next read. It is a port
 * of things-web's classify/reclassify (src/store/store.ts), which in turn mirrors the SQL in
 * the backend's sync/state.go. If one of the three changes, all three must.
 */

/** Null when the task belongs to no built-in list: done, cancelled, trashed, or a dated inbox row. */
fun classify(t: Item, today: DateStr): ViewId? {
    if (t.status != Status.OPEN || t.inTrash) return null
    val date = t.scheduledDate
    if (date != null) {
        return if (date <= today) {
            // A dated row still in the Inbox shows in neither list, as on the server.
            if (t.schedule == Schedule.INBOX) null else ViewId.TODAY
        } else {
            ViewId.UPCOMING
        }
    }
    return when (t.schedule) {
        Schedule.INBOX -> ViewId.INBOX
        Schedule.ANYTIME -> ViewId.ANYTIME
        else -> ViewId.SOMEDAY
    }
}

/**
 * Removes [t] from every list and puts it back where it now belongs.
 *
 * Today appends and the others prepend, matching the web client: a to-do moved to Today joins
 * the end of the day's plan, while one dropped into Anytime or Someday appears at the top where
 * it can be seen. The server's own ordering replaces this on the next read either way.
 */
fun Views.reclassify(t: Item, today: DateStr): Views {
    val without = map { _, ids -> ids.filterNot { it == t.id } }
    val v = classify(t, today) ?: return without
    val ids = without[v]
    return without.with(v, if (v == ViewId.TODAY) ids + t.id else listOf(t.id) + ids)
}

/** Drops [id] from every list, for a row that has just been completed, cancelled or trashed. */
fun Views.remove(id: String): Views = map { _, ids -> ids.filterNot { it == id } }

/** Rewrites a provisional id to the real one the server minted, in place and in order. */
fun Views.rename(from: String, to: String): Views =
    map { _, ids -> ids.map { if (it == from) to else it } }
