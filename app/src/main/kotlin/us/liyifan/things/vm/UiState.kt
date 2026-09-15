package us.liyifan.things.vm

/**
 * What the app is doing, as opposed to what it knows.
 *
 * [expandedId] is deliberately global rather than per screen: the editing card opens in place
 * inside whichever list, the keyboard and the button both act on it, and when a provisional id
 * is replaced by a real one the open card has to follow it.
 */
data class UiState(
    val selectedId: String? = null,
    val expandedId: String? = null,
    /** Ticked, still on screen, about to leave. */
    val settling: Set<String> = emptySet(),
    val toast: String? = null,
    val tagFilter: String? = null,
)
