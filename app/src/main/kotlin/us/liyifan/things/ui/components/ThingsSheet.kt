package us.liyifan.things.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * Everything that slides up from the bottom: the pickers, the row menu, the new-list chooser.
 *
 * Two implementations behind one call. Off e-ink it is Material's bottom sheet, which slides and
 * dims behind itself. On e-ink both of those are wrong — the slide is a dozen partial refreshes
 * and the dim is a full-screen alpha wash that ghosts — so it becomes a dialog that is simply
 * there, bordered in black, over an undimmed page.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ThingsSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ThingsTheme.colors
    val dims = ThingsTheme.dims

    if (colors.isEink) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            // Tapping outside closes it, the way the sheet's scrim would, but without drawing
            // a scrim: a full-screen wash of alpha is the single worst thing to put on e-paper.
            val dismissSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = dismissSource, onClick = onDismiss),
            ) {
                Column(
                    modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .fillMaxWidth()
                        .border(dims.sheetBorder, colors.line, RoundedCornerShape(dims.sheetRadius))
                        .background(colors.bgPopover, RoundedCornerShape(dims.sheetRadius))
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp),
                    content = content,
                )
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.bgPopover,
            shape = RoundedCornerShape(topStart = dims.sheetRadius, topEnd = dims.sheetRadius),
            dragHandle = {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Spacer(
                        Modifier
                            .width(36.dp)
                            .height(5.dp)
                            .background(colors.lineStrong, RoundedCornerShape(50)),
                    )
                }
            },
        ) {
            Column(modifier.navigationBarsPadding().padding(bottom = 8.dp), content = content)
        }
    }
}

/** A row inside a sheet: an optional icon, a label, and whether it is the current value. */
@Composable
fun SheetRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = ThingsTheme.colors.text2,
    trailing: @Composable (() -> Unit)? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    indent: Int = 0,
) {
    val colors = ThingsTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ThingsTheme.dims.popoverItemHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp)
            .padding(start = (indent * 16).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) iconTint else colors.text3, modifier = Modifier.size(17.dp))
        }
        Text(
            text = label,
            style = ThingsTheme.type.popoverItem,
            color = when {
                !enabled -> colors.text3
                danger -> colors.deadline
                else -> colors.text
            },
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** A small uppercase label above a group of sheet rows. */
@Composable
fun SheetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = ThingsTheme.type.popoverLabel,
        color = ThingsTheme.colors.text3,
        modifier = modifier.padding(start = 18.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** A divider between groups of sheet rows. */
@Composable
fun SheetDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(ThingsTheme.dims.hairline)
            .background(ThingsTheme.colors.line),
    )
}
