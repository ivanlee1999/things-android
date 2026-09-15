package us.liyifan.things.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import us.liyifan.things.model.Item
import us.liyifan.things.model.Model
import us.liyifan.things.model.filterByTag
import us.liyifan.things.ui.components.EmptyState
import us.liyifan.things.ui.components.GroupHeader
import us.liyifan.things.ui.components.IconButton
import us.liyifan.things.ui.components.ListScaffold
import us.liyifan.things.ui.components.LoggedRow
import us.liyifan.things.ui.components.PageHeader
import us.liyifan.things.ui.components.RowContext
import us.liyifan.things.ui.components.SearchField
import us.liyifan.things.ui.components.TagBar
import us.liyifan.things.ui.components.TextButton
import us.liyifan.things.ui.icons.Glyph
import us.liyifan.things.ui.icons.ProjectPie
import us.liyifan.things.ui.icons.ThingsIcon
import us.liyifan.things.ui.icons.glyph
import us.liyifan.things.ui.icons.listIcon
import us.liyifan.things.ui.theme.ThingsTheme

/** What a project screen can do beyond showing rows. */
data class ProjectActions(
    val onRename: (String) -> Unit,
    val onEditNote: (String) -> Unit,
    val onOpenMenu: () -> Unit,
    val onRenameHeading: (String, String) -> Unit,
    val onDeleteHeading: (String) -> Unit,
    val onLoadLogged: () -> Unit,
    val onUncomplete: (String) -> Unit,
)

/**
 * A project: its title, its note, its unheaded to-dos, then each heading with its own.
 *
 * Completed work is hidden behind a toggle and fetched only when asked for, because it is a
 * separate paged read against a server that syncs with Things Cloud before it answers.
 */
@Composable
fun ProjectScreen(
    project: Item,
    model: Model,
    host: ListHost,
    actions: ProjectActions,
    logged: List<Item>?,
    isNew: Boolean,
) {
    val colors = ThingsTheme.colors
    var showLogged by rememberSaveable(project.id) { mutableStateOf(false) }
    val all = model.tasksByProject[project.id].orEmpty()
    val tasks = filterByTag(all, host.tagFilter)
    val sections = model.projectSections(project.id, tasks)
    val progress = model.projectProgress(project.id, logged?.size)

    ListScaffold(onBack = host.onBack, listState = host.listState) {
        item {
            PageHeader(
                title = project.title,
                placeholder = "New Project",
                icon = { ProjectPie(progress = progress, size = 26.dp) },
                editable = true,
                autoFocus = isNew,
                onRename = actions.onRename,
                trailing = {
                    IconButton(
                        icon = glyph(Glyph.Ellipsis, 18f),
                        contentDescription = "Project menu",
                        onClick = actions.onOpenMenu,
                        iconSize = 18.dp,
                    )
                },
            )
        }
        item { ProjectNote(note = project.note, onEdit = actions.onEditNote) }
        item { TagBar(model.tagsIn(all), host.tagFilter, host.onTagFilter) }

        taskRows(sections.unheaded, model, host, RowContext(showWhen = true), keyPrefix = "u")

        sections.headed.forEach { section ->
            item(key = "h-${section.heading.id}") {
                HeadingRow(
                    heading = section.heading,
                    count = section.tasks.size,
                    onRename = { actions.onRenameHeading(section.heading.id, it) },
                    onDelete = { actions.onDeleteHeading(section.heading.id) },
                )
            }
            taskRows(section.tasks, model, host, RowContext(showWhen = true), keyPrefix = section.heading.id)
        }

        if (all.isEmpty()) {
            item { EmptyState(icon = listIcon(ThingsIcon.Anytime, 56f), text = "Nothing left in this project") }
        }

        val loggedCount = logged?.size ?: 0
        if (loggedCount > 0 || logged == null) {
            item {
                TextButton(
                    text = if (showLogged) "Hide logged items" else "Show $loggedCount logged items",
                    onClick = {
                        showLogged = !showLogged
                        if (showLogged && logged == null) actions.onLoadLogged()
                    },
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
        if (showLogged && logged != null) {
            items(logged.size, key = { i -> "logged-${logged[i].id}" }) { index ->
                LoggedRow(logged[index], model, onUncomplete = { actions.onUncomplete(logged[index].id) })
            }
        }
    }
}

@Composable
private fun ProjectNote(note: String, onEdit: (String) -> Unit) {
    val colors = ThingsTheme.colors
    var value by remember(note) { mutableStateOf(note) }
    var hadFocus by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (value.isEmpty()) Text("Notes", style = ThingsTheme.type.note, color = colors.text3)
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            textStyle = ThingsTheme.type.note.copy(color = colors.text),
            cursorBrush = SolidColor(colors.blue),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    // Saved when the note is left, not per keystroke: each save is a write the
                    // backend forwards to Things Cloud.
                    if (hadFocus && !state.isFocused && value != note) onEdit(value)
                    hadFocus = state.isFocused
                },
        )
    }
}

/** A heading inside a project: renamed in place, removed by the cross beside it. */
@Composable
fun HeadingRow(
    heading: Item,
    count: Int,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ThingsTheme.colors
    var value by remember(heading.title) { mutableStateOf(heading.title) }
    var hadFocus by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = ThingsTheme.type.group.copy(color = colors.blueDeep),
                cursorBrush = SolidColor(colors.blue),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        if (hadFocus && !state.isFocused && value != heading.title) onRename(value)
                        hadFocus = state.isFocused
                    },
            )
            if (count > 0) {
                Text(count.toString(), style = ThingsTheme.type.sub, color = colors.text3)
            }
            IconButton(
                icon = listIcon(ThingsIcon.Trash, 15f),
                contentDescription = "Delete heading",
                onClick = onDelete,
                tint = colors.trash,
                iconSize = 15.dp,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 3.dp)
                .height(ThingsTheme.dims.hairline)
                .background(colors.bgHeadingLine),
        )
    }
}

/** An area: the projects in it, then the to-dos that belong to it directly. */
@Composable
fun AreaScreen(
    area: us.liyifan.things.model.Area,
    model: Model,
    host: ListHost,
    onRename: (String) -> Unit,
    onOpenMenu: () -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val colors = ThingsTheme.colors
    val projects = model.projectsByArea[area.id].orEmpty()
    val tasks = filterByTag(model.tasksByArea[area.id].orEmpty(), host.tagFilter)

    ListScaffold(onBack = host.onBack, listState = host.listState) {
        item {
            PageHeader(
                title = area.title,
                placeholder = "New Area",
                icon = { ListIcon(ThingsIcon.Area, colors.area, 26) },
                editable = true,
                onRename = onRename,
                trailing = {
                    IconButton(
                        icon = glyph(Glyph.Ellipsis, 18f),
                        contentDescription = "Area menu",
                        onClick = onOpenMenu,
                        iconSize = 18.dp,
                    )
                },
            )
        }

        items(projects.size, key = { i -> "p-${projects[i].id}" }) { index ->
            val project = projects[index]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenProject(project.id) }
                    .defaultMinSize(minHeight = ThingsTheme.dims.rowMinHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProjectPie(progress = model.projectProgress(project.id), size = 18.dp)
                Text(
                    project.title.ifBlank { "New Project" },
                    style = ThingsTheme.type.row,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                val open = model.tasksByProject[project.id]?.size ?: 0
                if (open > 0) Text(open.toString(), style = ThingsTheme.type.sub, color = colors.text3)
            }
        }

        if (tasks.isNotEmpty()) {
            item { GroupHeader(title = "To-Dos") }
            taskRows(tasks, model, host, RowContext(showWhen = true), keyPrefix = "area")
        }

        if (projects.isEmpty() && tasks.isEmpty()) {
            item { EmptyState(icon = listIcon(ThingsIcon.Area, 56f), text = "Nothing in this area yet") }
        }
    }
}

/** Quick Find, over the snapshot the device already holds. */
@Composable
fun SearchScreen(
    model: Model,
    host: ListHost,
    initialQuery: String,
    onOpenProject: (String) -> Unit,
    onOpenArea: (String) -> Unit,
) {
    val colors = ThingsTheme.colors
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val results = remember(query, model) { model.search(query) }

    ListScaffold(onBack = host.onBack, listState = host.listState) {
        item { PageHeader(title = "Quick Find") }
        item { SearchField(value = query, onValueChange = { query = it }, autoFocus = true) }

        if (results.areas.isNotEmpty()) {
            item { GroupHeader(title = "Areas") }
            items(results.areas.size, key = { i -> "a-${results.areas[i].id}" }) { index ->
                val area = results.areas[index]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenArea(area.id) }
                        .defaultMinSize(minHeight = ThingsTheme.dims.rowMinHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ListIcon(ThingsIcon.Area, colors.area, 18)
                    Text(area.title, style = ThingsTheme.type.row, color = colors.text)
                }
            }
        }

        if (results.projects.isNotEmpty()) {
            item { GroupHeader(title = "Projects") }
            items(results.projects.size, key = { i -> "sp-${results.projects[i].id}" }) { index ->
                val project = results.projects[index]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProject(project.id) }
                        .defaultMinSize(minHeight = ThingsTheme.dims.rowMinHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProjectPie(progress = model.projectProgress(project.id), size = 18.dp)
                    Text(project.title.ifBlank { "New Project" }, style = ThingsTheme.type.row, color = colors.text)
                }
            }
        }

        if (results.tasks.isNotEmpty()) {
            item { GroupHeader(title = "To-Dos") }
            taskRows(results.tasks, model, host, RowContext(showCrumb = true, showWhen = true), keyPrefix = "s")
        }

        if (query.isNotBlank() && results.isEmpty) {
            item { EmptyState(icon = glyph(Glyph.Search, 56f), text = "Nothing found") }
        }
    }
}
