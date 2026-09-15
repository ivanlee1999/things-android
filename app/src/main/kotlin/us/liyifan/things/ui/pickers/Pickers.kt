package us.liyifan.things.ui.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import us.liyifan.things.model.Item
import us.liyifan.things.model.Model
import us.liyifan.things.model.Opt
import us.liyifan.things.model.TaskPatch
import us.liyifan.things.model.addDays
import us.liyifan.things.model.localToday
import us.liyifan.things.model.weekdayName
import us.liyifan.things.ui.components.SheetDivider
import us.liyifan.things.ui.components.SheetLabel
import us.liyifan.things.ui.components.SheetRow
import us.liyifan.things.ui.icons.Glyph
import us.liyifan.things.ui.icons.ProjectPie
import us.liyifan.things.ui.icons.ThingsIcon
import us.liyifan.things.ui.icons.glyph
import us.liyifan.things.ui.icons.listIcon
import us.liyifan.things.ui.theme.ThingsTheme

/** When: Today, Tomorrow, Anytime, Someday, or a date off the calendar. */
@Composable
fun ColumnScope.WhenPicker(task: Item, onPick: (String) -> Unit) {
    val colors = ThingsTheme.colors
    val today = remember { localToday() }
    val tomorrow = remember(today) { addDays(today, 1) }

    SheetRow("Today", { onPick("today") }, icon = listIcon(ThingsIcon.Today, 17f), iconTint = colors.today)
    SheetRow(
        label = "Tomorrow",
        onClick = { onPick(tomorrow) },
        icon = glyph(Glyph.Chevron, 14f),
        trailing = { Text(weekdayName(tomorrow, short = true), style = ThingsTheme.type.sub, color = colors.text3) },
    )
    SheetRow("Anytime", { onPick("anytime") }, icon = listIcon(ThingsIcon.Anytime, 17f), iconTint = colors.anytime)
    SheetRow("Someday", { onPick("someday") }, icon = listIcon(ThingsIcon.Someday, 17f), iconTint = colors.someday)
    SheetDivider()
    CalendarGrid(value = task.scheduledDate, onPick = onPick)
    if (task.scheduledDate != null) {
        SheetDivider()
        SheetRow("Clear", { onPick("none") })
    }
}

/** A deadline is a date or nothing; the backend refuses one in the past, so the grid does too. */
@Composable
fun ColumnScope.DeadlinePicker(task: Item, onPick: (String?) -> Unit) {
    SheetLabel("Deadline")
    CalendarGrid(value = task.deadline, min = localToday(), onPick = { onPick(it) })
    if (task.deadline != null) {
        SheetDivider()
        SheetRow("Remove Deadline", { onPick(null) })
    }
}

/** Tags, with a field that both filters and creates. */
@Composable
fun ColumnScope.TagPicker(
    task: Item,
    model: Model,
    onToggle: (List<String>) -> Unit,
    onCreate: (String) -> Unit,
    onDone: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val needle = query.trim().lowercase()
    val matches = model.snapshot.tags.filter { it.title.lowercase().contains(needle) }
    val exact = model.snapshot.tags.any { it.title.lowercase() == needle }

    PickerSearchField(value = query, onValueChange = { query = it }, placeholder = "Tag…")
    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
        matches.forEach { tag ->
            val on = tag.id in task.tagIds
            SheetRow(
                label = tag.title,
                onClick = {
                    onToggle(if (on) task.tagIds - tag.id else task.tagIds + tag.id)
                },
                trailing = {
                    if (on) {
                        Icon(
                            glyph(Glyph.Checkmark, 13f),
                            contentDescription = null,
                            tint = ThingsTheme.colors.blue,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                },
            )
        }
        if (needle.isNotEmpty() && !exact) {
            SheetRow("Create “${query.trim()}”", { onCreate(query.trim()); query = "" }, icon = glyph(Glyph.Plus, 14f))
        }
    }
    SheetDivider()
    SheetRow("Done", onDone)
}

/**
 * Move: the Inbox, every project with its headings, and every area, filtered as you type.
 *
 * A heading matches when its project does, so typing a project name shows where inside it the
 * to-do could go rather than making you clear the field and look again.
 */
@Composable
fun ColumnScope.MovePicker(model: Model, onMove: (TaskPatch) -> Unit) {
    val colors = ThingsTheme.colors
    var query by remember { mutableStateOf("") }
    val needle = query.trim().lowercase()
    fun hit(s: String) = needle.isEmpty() || s.lowercase().contains(needle)

    PickerSearchField(value = query, onValueChange = { query = it }, placeholder = "Move to…")
    Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
        if (hit("inbox")) {
            SheetRow(
                label = "Inbox",
                onClick = {
                    onMove(
                        TaskPatch(
                            projectId = Opt.of(null),
                            headingId = Opt.of(null),
                            areaId = Opt.of(null),
                            whenValue = Opt.of("inbox"),
                        ),
                    )
                },
                icon = listIcon(ThingsIcon.Inbox, 17f),
                iconTint = colors.inbox,
            )
        }

        ProjectRows(model, model.looseProjects, needle, 0, onMove)
        model.snapshot.areas.forEach { area ->
            val projects = model.projectsByArea[area.id].orEmpty()
            val childHit = projects.any { p ->
                hit(p.title) || model.headingsByProject[p.id].orEmpty().any { hit(it.title) }
            }
            if (!hit(area.title) && !childHit) return@forEach
            SheetRow(
                label = area.title,
                onClick = {
                    onMove(TaskPatch(areaId = Opt.of(area.id), projectId = Opt.of(null), headingId = Opt.of(null)))
                },
                icon = listIcon(ThingsIcon.Area, 17f),
                iconTint = colors.area,
            )
            ProjectRows(model, projects, needle, 1, onMove)
        }
    }
}

/**
 * A project and the headings inside it, as move destinations.
 *
 * A heading is listed when its project matches too, not only when the heading itself does, so
 * typing a project's name shows where within it the to-do could land.
 */
@Composable
private fun ColumnScope.ProjectRows(
    model: Model,
    projects: List<Item>,
    needle: String,
    indent: Int,
    onMove: (TaskPatch) -> Unit,
) {
    fun hit(s: String) = needle.isEmpty() || s.lowercase().contains(needle)
    projects.forEach { p ->
        val headings = model.headingsByProject[p.id].orEmpty()
        if (!hit(p.title) && headings.none { hit(it.title) }) return@forEach
        SheetRow(
            label = p.title.ifBlank { "New Project" },
            onClick = { onMove(TaskPatch(projectId = Opt.of(p.id), headingId = Opt.of(null))) },
            indent = indent,
            trailing = {
                ProjectPie(progress = model.projectProgress(p.id), size = 15.dp)
            },
        )
        headings.forEach { h ->
            if (!hit(h.title) && !hit(p.title)) return@forEach
            SheetRow(
                label = "\u203A  ${h.title}",
                onClick = { onMove(TaskPatch(projectId = Opt.of(null), headingId = Opt.of(h.id))) },
                indent = indent + 1,
            )
        }
    }
}

@Composable
private fun PickerSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = ThingsTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .background(colors.bgInput, RoundedCornerShape(9.dp))
            .then(
                if (colors.isEink) {
                    Modifier.background(colors.bg, RoundedCornerShape(9.dp))
                } else Modifier,
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = ThingsTheme.type.popoverItem, color = colors.text3)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = ThingsTheme.type.popoverItem.copy(color = colors.text),
            cursorBrush = SolidColor(colors.blue),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
