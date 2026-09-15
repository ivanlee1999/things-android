package us.liyifan.things.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import us.liyifan.things.ui.icons.Glyph
import us.liyifan.things.ui.icons.ProjectPie
import us.liyifan.things.ui.icons.ThingsIcon
import us.liyifan.things.ui.icons.glyph
import us.liyifan.things.ui.icons.listIcon
import us.liyifan.things.ui.theme.ThingsTheme

/** An entry in a long-press menu. */
sealed interface MenuEntry {
    data class Action(
        val label: String,
        val onSelect: () -> Unit,
        val icon: ImageVector? = null,
        val iconTint: Color? = null,
        val danger: Boolean = false,
        val enabled: Boolean = true,
    ) : MenuEntry

    data object Separator : MenuEntry
}

/**
 * The menu a long press opens.
 *
 * On the phone this is a sheet rather than a popover at the finger: a fixed position is easier
 * to hit, and on e-ink a panel that appears in a predictable place is one the eye finds without
 * waiting for the screen to settle.
 */
@Composable
fun ContextMenuSheet(entries: List<MenuEntry>, onDismiss: () -> Unit) {
    ThingsSheet(onDismiss = onDismiss) {
        entries.forEach { entry ->
            when (entry) {
                is MenuEntry.Separator -> SheetDivider()
                is MenuEntry.Action -> SheetRow(
                    label = entry.label,
                    onClick = { onDismiss(); entry.onSelect() },
                    icon = entry.icon,
                    iconTint = entry.iconTint ?: ThingsTheme.colors.text2,
                    danger = entry.danger,
                    enabled = entry.enabled,
                )
            }
        }
    }
}

/** The menu for a to-do: where it goes, whether it is done, and whether it stays. */
@Composable
fun taskMenu(
    onMove: (String) -> Unit,
    onOpenMovePicker: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
): List<MenuEntry> {
    val colors = ThingsTheme.colors
    return listOf(
        MenuEntry.Action("Today", { onMove("today") }, listIcon(ThingsIcon.Today, 17f), colors.today),
        MenuEntry.Action("Anytime", { onMove("anytime") }, listIcon(ThingsIcon.Anytime, 17f), colors.anytime),
        MenuEntry.Action("Someday", { onMove("someday") }, listIcon(ThingsIcon.Someday, 17f), colors.someday),
        MenuEntry.Separator,
        MenuEntry.Action("Move…", onOpenMovePicker, glyph(Glyph.Move, 16f)),
        MenuEntry.Separator,
        MenuEntry.Action("Complete", onComplete, glyph(Glyph.Checkmark, 15f)),
        MenuEntry.Action("Cancel", onCancel, glyph(Glyph.X, 13f)),
        MenuEntry.Separator,
        MenuEntry.Action("Delete", onDelete, listIcon(ThingsIcon.Trash, 16f), colors.trash, danger = true),
    )
}

/** "+ New List": a project, or an area to keep projects in. */
@Composable
fun NewListSheet(onPick: (NewList) -> Unit, onDismiss: () -> Unit) {
    val colors = ThingsTheme.colors
    ThingsSheet(onDismiss = onDismiss) {
        NewListOption(
            title = "New Project",
            description = "A to-do with a plan of its own.",
            onClick = { onDismiss(); onPick(NewList.PROJECT) },
        ) {
            ProjectPie(progress = 0.3f, size = 30.dp)
        }
        NewListOption(
            title = "New Area",
            description = "A part of life or work that holds projects.",
            onClick = { onDismiss(); onPick(NewList.AREA) },
        ) {
            Icon(listIcon(ThingsIcon.Area, 30f), contentDescription = null, tint = colors.area, modifier = Modifier.size(30.dp))
        }
    }
}

enum class NewList { PROJECT, AREA }

@Composable
private fun NewListOption(
    title: String,
    description: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = ThingsTheme.type.homeArea, color = ThingsTheme.colors.text)
            Text(description, style = ThingsTheme.type.sub, color = ThingsTheme.colors.text2)
        }
    }
}

/** A question that has to be answered before something is thrown away. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
) {
    val colors = ThingsTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = ThingsTheme.type.group, color = colors.text) },
        text = { Text(text, style = ThingsTheme.type.sub, color = colors.text2) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirmLabel, color = if (danger) colors.deadline else colors.blue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.blue) }
        },
        containerColor = colors.bgPopover,
    )
}
