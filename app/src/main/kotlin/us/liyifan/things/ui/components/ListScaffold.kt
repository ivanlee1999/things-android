package us.liyifan.things.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import us.liyifan.things.ui.icons.Glyph
import us.liyifan.things.ui.icons.glyph
import us.liyifan.things.ui.keys.pageKeys
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * The frame every list is drawn in: a back bar, a scrolling column, and the page keys.
 *
 * The bar is solid rather than the web client's translucent blur. Blur is an expensive alpha
 * effect that an e-ink panel renders as mud, and on a colour panel it is invisible anyway.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ListScaffold(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    navTrailing: @Composable (() -> Unit)? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = ThingsTheme.colors
    val dims = ThingsTheme.dims

    Column(modifier.fillMaxSize().background(colors.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(44.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Row(
                    Modifier.clickable(onClick = onBack).padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        glyph(Glyph.Chevron, 15f),
                        contentDescription = "Back",
                        tint = colors.blue,
                        modifier = Modifier.size(15.dp).rotate(180f),
                    )
                    Text(us.liyifan.things.ui.nav.BACK_LABEL, style = ThingsTheme.type.nav, color = colors.blue)
                }
            }
            Box(Modifier.weight(1f))
            navTrailing?.invoke()
        }

        // Pulling to refresh matters most in e-ink mode, where the minute-by-minute poll is
        // off and this is how you ask for the server's latest.
        val pullState = rememberPullToRefreshState()
        Box(Modifier.fillMaxSize()) {
            val list: @Composable () -> Unit = {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .pageKeys(listState)
                        .padding(horizontal = dims.pageHorizontal),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                    content = content,
                )
            }
            if (onRefresh == null) {
                list()
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    state = pullState,
                    indicator = {
                        Indicator(
                            state = pullState,
                            isRefreshing = refreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                            containerColor = colors.bgCard,
                            color = colors.blue,
                        )
                    },
                    content = { list() },
                )
            }
        }
    }
}

/**
 * A list's big title, editable where the thing it names can be renamed.
 *
 * A freshly made project opens with its placeholder title selected, so the first keystroke
 * replaces it — which is how Things behaves and why a new project never stays "New Project".
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    editable: Boolean = false,
    autoFocus: Boolean = false,
    placeholder: String = "New List",
    onRename: (String) -> Unit = {},
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = ThingsTheme.colors
    val style = ThingsTheme.type.title.copy(color = colors.text)

    Row(
        modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { icon() }
        }
        Box(Modifier.weight(1f)) {
            if (!editable) {
                Text(title, style = style)
            } else {
                var value by remember(title) {
                    mutableStateOf(TextFieldValue(title, TextRange(0, if (autoFocus) title.length else 0)))
                }
                var hadFocus by remember { mutableStateOf(false) }
                val focus = remember { FocusRequester() }
                LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
                if (value.text.isEmpty()) Text(placeholder, style = style.copy(color = colors.text3))
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    textStyle = style,
                    cursorBrush = SolidColor(colors.blue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        // A rename is committed when the field is left, not per keystroke: each
                        // save is a write the server forwards to Things Cloud.
                        .onFocusChanged { state ->
                            if (hadFocus && !state.isFocused && value.text.trim() != title) {
                                onRename(value.text.trim())
                            }
                            hadFocus = state.isFocused
                        },
                )
            }
        }
        trailing?.invoke()
    }
}

/** A search field, used by Quick Find and by the Home screen's header. */
private val fieldShape = RoundedCornerShape(9.dp)

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Quick Find",
    autoFocus: Boolean = false,
) {
    val colors = ThingsTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    Row(
        modifier
            .fillMaxWidth()
            .then(
                // A pale grey field is invisible on e-paper, so there it is an outline instead.
                if (colors.isEink) Modifier.border(1.5.dp, colors.line, fieldShape)
                else Modifier.background(colors.bgInput, fieldShape),
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(glyph(Glyph.Search, 14f), contentDescription = null, tint = colors.text3, modifier = Modifier.size(14.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = ThingsTheme.type.popoverItem, color = colors.text3)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = ThingsTheme.type.popoverItem.copy(color = colors.text),
                cursorBrush = SolidColor(colors.blue),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

/** Scrolling stops when the finger lifts: momentum on e-paper is a stutter, not a glide. */
object NoFling : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float = initialVelocity
}
