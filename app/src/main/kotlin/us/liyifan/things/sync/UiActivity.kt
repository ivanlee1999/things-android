package us.liyifan.things.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the person holding the phone is doing, as far as the sync layer needs to know.
 *
 * This exists for one rule: a list must never rearrange itself under someone who is reading it.
 * That matters everywhere and matters enormously on e-paper, where a refresh is a visible flash
 * rather than a frame. So a snapshot that arrives while the screen has been still is held, and
 * applied the moment the user does anything at all.
 */
object UiActivity {

    private val _editorOpen = MutableStateFlow(false)
    val editorOpen: StateFlow<Boolean> = _editorOpen.asStateFlow()

    @Volatile
    var lastInteractionAt: Long = 0L
        private set

    fun setEditorOpen(open: Boolean) {
        _editorOpen.value = open
        if (open) touched()
    }

    /** Called from the root of the UI on any pointer event. */
    fun touched() {
        lastInteractionAt = System.currentTimeMillis()
    }

    /** A screen change is both an interaction and a good moment to redraw. */
    fun onNavigate() = touched()

    /**
     * True when redrawing now would not pull the page out from under anyone: nothing is being
     * edited, and the screen has just been touched.
     */
    fun readyToDraw(now: Long = System.currentTimeMillis()): Boolean =
        !_editorOpen.value && now - lastInteractionAt < QUIET_AFTER_MS

    const val QUIET_AFTER_MS = 3_000L
}
