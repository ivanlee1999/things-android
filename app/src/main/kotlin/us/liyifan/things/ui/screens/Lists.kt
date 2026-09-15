package us.liyifan.things.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import us.liyifan.things.model.DateStr
import us.liyifan.things.model.Item
import us.liyifan.things.model.Model
import us.liyifan.things.model.ViewId
import us.liyifan.things.model.dayOfMonth
import us.liyifan.things.model.daysBetween
import us.liyifan.things.model.filterByTag
import us.liyifan.things.model.monthName
import us.liyifan.things.model.weekdayName
import us.liyifan.things.model.yearOf
import us.liyifan.things.ui.components.DayHeader
import us.liyifan.things.ui.components.EmptyState
import us.liyifan.things.ui.components.GroupHeader
import us.liyifan.things.ui.components.ListScaffold
import us.liyifan.things.ui.components.PageHeader
import us.liyifan.things.ui.components.RowContext
import us.liyifan.things.ui.components.TagBar
import us.liyifan.things.ui.icons.ThingsIcon
import us.liyifan.things.ui.icons.listIcon
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * The five built-in lists.
 *
 * Which to-do is in which list is the server's answer, carried in the snapshot; nothing here
 * re-decides it. What each screen does decide is how its rows are grouped, and that is the only
 * thing that differs between them.
 */

/** The flat ones: Inbox and Today. */
@Composable
fun FlatListScreen(
    title: String,
    icon: ThingsIcon,
    iconTint: androidx.compose.ui.graphics.Color,
    view: ViewId,
    model: Model,
    host: ListHost,
    emptyText: String,
    showCrumb: Boolean,
    showWhen: Boolean,
) {
    val all = model.view(view)
    val items = filterByTag(all, host.tagFilter)
    ListScaffold(onBack = host.onBack, listState = host.listState) {
        item { PageHeader(title = title, icon = { ListIcon(icon, iconTint) }) }
        item { TagBar(model.tagsIn(all), host.tagFilter, host.onTagFilter) }
        taskRows(items, model, host, RowContext(showCrumb = showCrumb, showWhen = showWhen))
        if (items.isEmpty()) {
            item { EmptyState(icon = listIcon(icon, 56f), text = emptyText) }
        }
    }
}

/**
 * Upcoming: a header for each of the next seven days, present even when the day is empty, then
 * one section per month. An empty Thursday is information — it says the week has room in it.
 */
@Composable
fun UpcomingScreen(model: Model, host: ListHost) {
    val colors = ThingsTheme.colors
    val all = model.view(ViewId.UPCOMING)
    val items = filterByTag(all, host.tagFilter)
    val groups = model.upcomingGroups(items)

    ListScaffold(onBack = host.onBack, listState = host.listState) {
        item { PageHeader(title = "Upcoming", icon = { ListIcon(ThingsIcon.Upcoming, colors.upcoming) }) }
        item { TagBar(model.tagsIn(all), host.tagFilter, host.onTagFilter) }

        groups.days.forEach { day ->
            item(key = "day-${day.date}") {
                DayHeader(
                    day = dayOfMonth(day.date),
                    name = if (daysBetween(model.today, day.date) == 1) "Tomorrow" else weekdayName(day.date),
                )
            }
            taskRows(day.tasks, model, host, RowContext(showCrumb = true), keyPrefix = day.date)
        }

        groups.months.forEach { month ->
            val first = "${month.month}-01"
            item(key = "month-${month.month}") {
                GroupHeader(
                    title = monthName(first) + if (yearOf(first) != yearOf(model.today)) " ${yearOf(first)}" else "",
                    underline = false,
                )
            }
            month.tasks.forEach { task ->
                item(key = "m-${task.id}") {
                    // Beyond the week, each row carries its own date in a gutter, since the
                    // section header only names the month.
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = task.scheduledDate?.let { "${dayOfMonth(it)} ${monthName(it, short = true)}" }.orEmpty(),
                            style = ThingsTheme.type.sub,
                            color = colors.text3,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(44.dp).padding(top = 10.dp),
                        )
                        Box(Modifier.weight(1f)) {
                            host.row(task, RowContext(showCrumb = true))
                        }
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item { EmptyState(icon = listIcon(ThingsIcon.Upcoming, 56f), text = "Nothing scheduled") }
        }
    }
}

/** Anytime and Someday, grouped by project and area the way Things groups them. */
@Composable
fun GroupedListScreen(
    title: String,
    icon: ThingsIcon,
    iconTint: androidx.compose.ui.graphics.Color,
    view: ViewId,
    model: Model,
    host: ListHost,
    emptyText: String,
) {
    val all = model.view(view)
    val items = filterByTag(all, host.tagFilter)
    val groups = model.groupByProject(items)

    ListScaffold(onBack = host.onBack, listState = host.listState) {
        item { PageHeader(title = title, icon = { ListIcon(icon, iconTint) }) }
        item { TagBar(model.tagsIn(all), host.tagFilter, host.onTagFilter) }

        groups.forEach { group ->
            if (group.project != null || group.area != null) {
                item(key = "g-${group.key}") {
                    GroupHeader(
                        title = group.project?.title?.ifBlank { "New Project" } ?: group.area!!.title,
                        trailing = {
                            // A project under an area says which one, quietly, on the right.
                            if (group.project != null && group.area != null) {
                                Text(group.area.title, style = ThingsTheme.type.sub, color = ThingsTheme.colors.text3)
                            }
                        },
                    )
                }
            }
            taskRows(group.tasks, model, host, RowContext(showWhen = true), keyPrefix = group.key)
        }

        if (items.isEmpty()) {
            item { EmptyState(icon = listIcon(icon, 56f), text = emptyText) }
        }
    }
}

@Composable
fun ListIcon(kind: ThingsIcon, tint: androidx.compose.ui.graphics.Color, size: Int = 30) {
    Icon(listIcon(kind, size.toFloat()), contentDescription = null, tint = tint, modifier = Modifier.size(size.dp))
}

/** Rows plus the editing card, which replaces a row in place when it is opened. */
fun LazyListScope.taskRows(
    items: List<Item>,
    model: Model,
    host: ListHost,
    ctx: RowContext,
    keyPrefix: String = "",
) {
    items(items.size, key = { i -> "$keyPrefix:${items[i].id}" }) { index ->
        host.row(items[index], ctx)
    }
}

/** What a list needs from the screen above it, so the lists themselves hold no dependencies. */
interface ListHost {
    val listState: androidx.compose.foundation.lazy.LazyListState
    val onBack: (() -> Unit)?
    val tagFilter: String?
    val onTagFilter: (String?) -> Unit

    @Composable
    fun row(task: Item, ctx: RowContext)
}
