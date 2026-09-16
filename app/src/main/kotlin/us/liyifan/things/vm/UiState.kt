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
    /**
     * Provisional ids the server has since replaced. A route or an open sheet naming the old one
     * is resolved through this, so creating a project and being taken straight to it does not
     * end on an empty screen the moment the create comes back.
     */
    val idMap: Map<String, String> = emptyMap(),
)
