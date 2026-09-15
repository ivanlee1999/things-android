package us.liyifan.things.ui.keys

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * The BOOX Palma's page-turn keys, and a Bluetooth keyboard's.
 *
 * Volume keys do not reach a composable through focus — the system takes them first — so the
 * activity intercepts them and publishes here. Whether they are volume or page keys depends on
 * how the device is configured; both are handled, and both are only claimed while e-ink mode is
 * on, so a phone in a pocket still changes volume.
 */
enum class PageKey { Up, Down }

object PageKeyBus {
    private val _events = MutableSharedFlow<PageKey>(extraBufferCapacity = 2)
    val events = _events.asSharedFlow()

    fun emit(key: PageKey) {
        _events.tryEmit(key)
    }
}

/**
 * Scrolls a list one screen at a time.
 *
 * A page leaves one row of overlap so the eye has something to land on, and on e-ink it jumps
 * rather than animating: a smooth scroll is dozens of partial refreshes, and paging is the
 * gesture e-readers taught everyone anyway.
 */
fun Modifier.pageKeys(listState: LazyListState): Modifier = composed {
    val eink = ThingsTheme.eink.enabled
    val overlap = with(LocalDensity.current) { ThingsTheme.dims.rowMinHeight.toPx() }

    LaunchedEffect(listState, eink) {
        PageKeyBus.events.collect { key ->
            val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
            val distance = (viewport - overlap).coerceAtLeast(overlap)
            val delta = if (key == PageKey.Down) distance else -distance
            if (eink) listState.jumpBy(delta) else listState.animateScrollBy(delta)
        }
    }

    // A Bluetooth keyboard's page keys and space bar reach Compose normally.
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.PageDown, Key.Spacebar -> { PageKeyBus.emit(PageKey.Down); true }
            Key.PageUp -> { PageKeyBus.emit(PageKey.Up); true }
            else -> false
        }
    }
}

private suspend fun LazyListState.jumpBy(delta: Float) {
    scroll { scrollBy(delta) }
}

/**
 * The web client's keyboard shortcuts, for anyone who pairs a keyboard.
 *
 * Only the ones that mean something on a phone: there is no sidebar to focus and no mouse to
 * right-click with. A bare letter is ignored while a text field has focus, or typing a note
 * would keep completing the to-do it is attached to.
 */
data class ShortcutActions(
    val onNew: () -> Unit,
    val onComplete: () -> Unit,
    val onTrash: () -> Unit,
    val onMove: (String) -> Unit,
    val onClose: () -> Unit,
    val onGoTo: (Int) -> Unit,
    val onFind: () -> Unit,
    val isEditing: () -> Boolean,
    val hasSelection: () -> Boolean,
)

fun Modifier.thingsShortcuts(actions: ShortcutActions): Modifier = composed {
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val modified = event.isCtrlPressed || event.isMetaPressed
        val editing = actions.isEditing()

        when {
            // Escape closes whatever is open, whether or not a field has focus.
            event.key == Key.Escape -> { actions.onClose(); true }

            modified && event.key == Key.K -> { actions.onComplete(); true }
            modified && event.key == Key.F -> { actions.onFind(); true }
            modified && event.key == Key.Backspace -> { actions.onTrash(); true }
            modified && event.key == Key.T -> { actions.onMove("today"); true }
            modified && event.key == Key.R -> { actions.onMove("anytime"); true }
            modified && event.key == Key.S -> { actions.onMove("someday"); true }
            modified && event.key == Key.I -> { actions.onMove("inbox"); true }
            modified && event.key == Key.N -> { actions.onNew(); true }
            modified && event.key in DIGITS -> { actions.onGoTo(DIGITS.indexOf(event.key) + 1); true }

            // Bare letters only when nothing is being typed into.
            !editing && event.key == Key.N -> { actions.onNew(); true }

            else -> false
        }
    }
}

private val DIGITS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven)
