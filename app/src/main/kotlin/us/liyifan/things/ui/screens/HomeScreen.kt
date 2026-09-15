package us.liyifan.things.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import us.liyifan.things.model.Model
import us.liyifan.things.model.ViewId
import us.liyifan.things.ui.components.ListScaffold
import us.liyifan.things.ui.components.PageHeader
import us.liyifan.things.ui.components.SearchField
import us.liyifan.things.ui.components.SyncStatusLine
import us.liyifan.things.ui.icons.Glyph
import us.liyifan.things.ui.icons.ProjectPie
import us.liyifan.things.ui.icons.ThingsIcon
import us.liyifan.things.ui.icons.glyph
import us.liyifan.things.ui.icons.listIcon
import us.liyifan.things.ui.nav.Route
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * The home screen: every list, then every area with the projects inside it.
 *
 * This is the web client's phone layout rather than its sidebar. A phone has one column, and the
 * sidebar's job — knowing where everything is — is better done by a screen you leave than by a
 * panel that takes half the width of a 6-inch display.
 */
@Composable
fun HomeScreen(
    model: Model,
    syncLabel: String,
    stagedAvailable: Boolean,
    onNavigate: (Route) -> Unit,
    onSearch: (String) -> Unit,
    onNewList: () -> Unit,
    onSyncTap: () -> Unit,
) {
    val colors = ThingsTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    ListScaffold(
        onBack = null,
        listState = listState,
        navTrailing = {
            Box(
                Modifier.size(ThingsTheme.dims.iconButton).clickable { onNavigate(Route.Settings) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(glyph(Glyph.Ellipsis, 18f), contentDescription = "Settings", tint = colors.text3, modifier = Modifier.size(18.dp))
            }
        },
    ) {
        item { PageHeader(title = "Lists") }
        item {
            SearchField(
                value = query,
                onValueChange = { text ->
                    query = text
                    if (text.isNotBlank()) onSearch(text)
                },
            )
        }

        item {
            HomeRow(
                title = "Inbox",
                icon = { Icon(listIcon(ThingsIcon.Inbox, 22f), null, tint = colors.inbox, modifier = Modifier.size(22.dp)) },
                count = model.view(ViewId.INBOX).size,
                onClick = { onNavigate(Route.Inbox) },
            )
        }

        item { HomeGroupGap() }

        item {
            HomeRow(
                title = "Today",
                icon = { Icon(listIcon(ThingsIcon.Today, 22f), null, tint = colors.today, modifier = Modifier.size(22.dp)) },
                count = model.view(ViewId.TODAY).size,
                onClick = { onNavigate(Route.Today) },
            )
        }
        item {
            HomeRow(
                title = "Upcoming",
                icon = { Icon(listIcon(ThingsIcon.Upcoming, 22f), null, tint = colors.upcoming, modifier = Modifier.size(22.dp)) },
                count = null,
                onClick = { onNavigate(Route.Upcoming) },
            )
        }
        item {
            HomeRow(
                title = "Anytime",
                icon = { Icon(listIcon(ThingsIcon.Anytime, 22f), null, tint = colors.anytime, modifier = Modifier.size(22.dp)) },
                count = null,
                onClick = { onNavigate(Route.Anytime) },
            )
        }
        item {
            HomeRow(
                title = "Someday",
                icon = { Icon(listIcon(ThingsIcon.Someday, 22f), null, tint = colors.someday, modifier = Modifier.size(22.dp)) },
                count = null,
                onClick = { onNavigate(Route.Someday) },
            )
        }

        item { HomeGroupGap() }

        item {
            HomeRow(
                title = "Logbook",
                icon = { Icon(listIcon(ThingsIcon.Logbook, 22f), null, tint = colors.logbook, modifier = Modifier.size(22.dp)) },
                count = null,
                onClick = { onNavigate(Route.Logbook) },
            )
        }
        item {
            HomeRow(
                title = "Trash",
                icon = { Icon(listIcon(ThingsIcon.Trash, 22f), null, tint = colors.trash, modifier = Modifier.size(22.dp)) },
                count = null,
                onClick = { onNavigate(Route.Trash) },
            )
        }

        if (model.looseProjects.isNotEmpty()) {
            item { HomeGroupGap() }
            projectRows(model, model.looseProjects, onNavigate)
        }

        model.snapshot.areas.forEach { area ->
            item(key = "area-${area.id}") {
                HomeRow(
                    title = area.title,
                    icon = { Icon(listIcon(ThingsIcon.Area, 22f), null, tint = colors.area, modifier = Modifier.size(22.dp)) },
                    count = null,
                    bold = true,
                    onClick = { onNavigate(Route.Area(area.id)) },
                )
            }
            projectRows(model, model.projectsByArea[area.id].orEmpty(), onNavigate, indent = true)
        }

        item {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onNewList).padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(glyph(Glyph.Plus, 15f), contentDescription = null, tint = colors.blue, modifier = Modifier.size(15.dp))
                Text("New List", style = ThingsTheme.type.homeRow, color = colors.blue)
            }
        }

        item { SyncStatusLine(label = syncLabel, stagedAvailable = stagedAvailable, onClick = onSyncTap) }
    }
}

/** The gap between blocks of rows, which is how the phone layout separates them. */
@Composable
private fun HomeGroupGap() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .height(ThingsTheme.dims.hairline)
            .background(ThingsTheme.colors.line),
    )
}

private fun LazyListScope.projectRows(
    model: Model,
    projects: List<us.liyifan.things.model.Item>,
    onNavigate: (Route) -> Unit,
    indent: Boolean = false,
) {
    items(projects.size, key = { i -> "proj-${projects[i].id}" }) { index ->
        val project = projects[index]
        HomeRow(
            title = project.title.ifBlank { "New Project" },
            icon = { ProjectPie(progress = model.projectProgress(project.id), size = 20.dp) },
            count = model.tasksByProject[project.id]?.size,
            indent = indent,
            onClick = { onNavigate(Route.Project(project.id)) },
        )
    }
}

@Composable
private fun HomeRow(
    title: String,
    icon: @Composable () -> Unit,
    count: Int?,
    bold: Boolean = false,
    indent: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ThingsTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = ThingsTheme.dims.homeRowHeight)
            .padding(start = if (indent) 22.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            text = title,
            style = if (bold) ThingsTheme.type.homeArea else ThingsTheme.type.homeRow,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        if (count != null && count > 0) {
            Text(count.toString(), style = ThingsTheme.type.sub, color = colors.text3)
        }
        Icon(
            glyph(Glyph.Chevron, 12f),
            contentDescription = null,
            tint = colors.text3,
            modifier = Modifier.size(12.dp),
        )
    }
}
