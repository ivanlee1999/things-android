package us.liyifan.things.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import us.liyifan.things.model.Item
import us.liyifan.things.model.Model
import us.liyifan.things.model.Status
import us.liyifan.things.model.deadlineLabel
import us.liyifan.things.model.relativeDay
import us.liyifan.things.ui.icons.Glyph
import us.liyifan.things.ui.icons.glyph
import us.liyifan.things.ui.theme.ThingsTheme

/** What a list wants shown on its rows; the same row is drawn differently in each. */
data class RowContext(
    /** The project or area line under the title. Today shows it; Inbox does not. */
    val showCrumb: Boolean = false,
    /** The scheduled date on the right. Upcoming hides it, having grouped by it already. */
    val showWhen: Boolean = false,
    /** A Logbook row: ticked, greyed, and not editable. */
    val logged: Boolean = false,
)

/**
 * One to-do, collapsed.
 *
 * A tap opens the editing card in place; a long press opens the menu, with a haptic tick so the
 * gesture is confirmed by feel rather than by an animation an e-ink panel cannot draw.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    task: Item,
    model: Model,
    ctx: RowContext,
    selected: Boolean,
    settling: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCheck: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThingsTheme.colors
    val dims = ThingsTheme.dims
    val type = ThingsTheme.type
    val haptics = LocalHapticFeedback.current

    val done = task.status == Status.COMPLETED || settling
    val cancelled = task.status == Status.CANCELLED
    val crumb = if (ctx.showCrumb) model.breadcrumb(task) else emptyList()
    val checklist = model.checklistByTask[task.id].orEmpty()
    val deadline = task.deadline?.let { deadlineLabel(it, model.today) }
    val scheduled = task.scheduledDate
    val isTodayOrOverdue = scheduled != null && scheduled <= model.today

    Row(
        modifier
            .fillMaxWidth()
            .then(
                // Selection is a wash of colour normally; on e-ink that is a grey block, so it
                // becomes a bar down the left edge, which one refresh can draw cleanly.
                if (!selected) Modifier
                else if (colors.isEink) Modifier.drawBehind {
                    drawRect(
                        color = colors.text,
                        topLeft = Offset.Zero,
                        size = Size(dims.selectionBar.toPx(), size.height),
                    )
                }
                else Modifier.background(colors.bgRowSelected)
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
                enabled = !ctx.logged,
            )
            .defaultMinSize(minHeight = dims.rowMinHeight)
            .padding(horizontal = dims.rowPadHorizontal, vertical = dims.rowPadVertical)
            .padding(start = if (selected && colors.isEink) 8.dp else 0.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        ThingsCheckbox(
            checked = done || cancelled,
            cancelled = cancelled,
            round = task.isProject,
            onChange = if (ctx.logged) null else onCheck,
            modifier = Modifier.padding(top = 1.dp),
        )

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = task.title.ifBlank { "New To-Do" },
                style = if (done || cancelled) type.rowDone else type.row,
                color = if (done || cancelled || task.title.isBlank()) colors.text3 else colors.text,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (crumb.isNotEmpty()) {
                Text(
                    text = crumb.joinToString("  ›  "),
                    style = type.sub,
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            if (task.note.isNotBlank()) MetaGlyph(Glyph.Note, 12f)
            if (checklist.isNotEmpty()) MetaGlyph(Glyph.Checklist, 12f)
            if (task.repeating) MetaGlyph(Glyph.Repeat, 12f)

            if (ctx.showWhen && scheduled != null && !ctx.logged) {
                if (isTodayOrOverdue) {
                    // A star, not the word "Today": the row is in Today, and the star is how
                    // Things says so in one glyph.
                    Icon(
                        imageVector = glyph(Glyph.Star, 11f),
                        contentDescription = "Today",
                        tint = colors.today,
                        modifier = Modifier.size(11.dp),
                    )
                } else {
                    Text(relativeDay(scheduled, model.today), style = type.sub, color = colors.text2)
                }
            }

            if (deadline != null) {
                // The flag carries the meaning, the colour only sharpens it — which is what lets
                // this read on a monochrome panel.
                val urgent = deadline.overdue || deadline.soon
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(
                        imageVector = glyph(Glyph.Flag, 11f),
                        contentDescription = "Deadline",
                        tint = if (urgent) colors.deadline else colors.text2,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = deadline.text,
                        style = type.sub,
                        color = if (urgent) colors.deadline else colors.text2,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaGlyph(kind: Glyph, size: Float) {
    Icon(
        imageVector = glyph(kind, size),
        contentDescription = null,
        tint = ThingsTheme.colors.text3,
        modifier = Modifier.size(size.dp),
    )
}

/** A Logbook row: the tick puts it back, nothing else is editable. */
@Composable
fun LoggedRow(
    task: Item,
    model: Model,
    onUncomplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThingsTheme.colors
    val dims = ThingsTheme.dims
    val cancelled = task.status == Status.CANCELLED
    val project = task.projectId?.let { model.projectsById[it] }
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dims.rowMinHeight)
            .padding(horizontal = dims.rowPadHorizontal, vertical = dims.rowPadVertical),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        ThingsCheckbox(
            checked = true,
            cancelled = cancelled,
            round = task.isProject,
            onChange = { onUncomplete() },
            modifier = Modifier.padding(top = 1.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title.ifBlank { "New To-Do" },
                style = ThingsTheme.type.row,
                color = colors.text2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (project != null) {
                Text(project.title, style = ThingsTheme.type.sub, color = colors.text3, maxLines = 1)
            }
        }
    }
}

/** A trashed row, with the one thing that can be done to it. */
@Composable
fun TrashRow(task: Item, onPutBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ThingsTheme.colors
    val dims = ThingsTheme.dims
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dims.rowMinHeight)
            .padding(horizontal = dims.rowPadHorizontal, vertical = dims.rowPadVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .size(dims.checkbox)
                .drawBehind {
                    drawRoundRect(
                        color = colors.checkboxBorder,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = dims.checkboxStroke.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.22f),
                    )
                },
        )
        Text(
            text = task.title.ifBlank { "New To-Do" },
            style = ThingsTheme.type.row,
            color = colors.text2,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(text = "Put Back", onClick = onPutBack)
    }
}
