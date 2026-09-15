package us.liyifan.things.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import us.liyifan.things.model.Item
import us.liyifan.things.model.Model
import us.liyifan.things.model.UNKNOWN_DAY
import us.liyifan.things.model.daysBetween
import us.liyifan.things.model.localToday
import us.liyifan.things.model.logbookGroups
import us.liyifan.things.model.relativeDay
import us.liyifan.things.model.weekdayName
import us.liyifan.things.ui.components.EmptyState
import us.liyifan.things.ui.components.GroupHeader
import us.liyifan.things.ui.components.ListScaffold
import us.liyifan.things.ui.components.LoggedRow
import us.liyifan.things.ui.components.PageHeader
import us.liyifan.things.ui.components.TextButton
import us.liyifan.things.ui.components.TrashRow
import us.liyifan.things.ui.icons.ThingsIcon
import us.liyifan.things.ui.icons.listIcon
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * The Logbook: what has been finished, newest first, in days.
 *
 * The day is the device's, not the server's. Completion is an instant, and a to-do ticked at
 * 22:00 belongs to today wherever the person who ticked it is standing — which is the one place
 * this app deliberately disagrees with the backend's timezone.
 */
@Composable
fun LogbookScreen(
    items: List<Item>,
    model: Model,
    host: ListHost,
    complete: Boolean,
    onLoad: (Boolean) -> Unit,
    onUncomplete: (String) -> Unit,
) {
    val colors = ThingsTheme.colors
    val today = localToday()
    val groups = logbookGroups(items)

    LaunchedEffect(Unit) { onLoad(false) }

    ListScaffold(onBack = host.onBack, listState = host.listState) {
        item { PageHeader(title = "Logbook", icon = { ListIcon(ThingsIcon.Logbook, colors.logbook, 26) }) }

        groups.forEach { group ->
            item(key = "lg-${group.date}") {
                GroupHeader(
                    title = when {
                        group.date == UNKNOWN_DAY -> "Earlier"
                        else -> relativeDay(group.date, today)
                    },
                    trailing = {
                        // Past the last few days a weekday alone is ambiguous, so the header
                        // carries the day name beside the date.
                        if (group.date != UNKNOWN_DAY && daysBetween(today, group.date) < -1) {
                            androidx.compose.material3.Text(
                                weekdayName(group.date),
                                style = ThingsTheme.type.sub,
                                color = colors.text3,
                            )
                        }
                    },
                )
            }
            items(group.tasks.size, key = { i -> "l-${group.tasks[i].id}" }) { index ->
                val task = group.tasks[index]
                LoggedRow(task, model, onUncomplete = { onUncomplete(task.id) })
            }
        }

        if (!complete && items.isNotEmpty()) {
            item {
                TextButton(text = "Show more…", onClick = { onLoad(true) }, modifier = Modifier.padding(top = 16.dp))
            }
        }
        if (items.isEmpty()) {
            item { EmptyState(icon = listIcon(ThingsIcon.Logbook, 56f), text = "Nothing logged yet") }
        }
    }
}

/** The Trash, where the only thing to do is take something back out. */
@Composable
fun TrashScreen(
    items: List<Item>,
    host: ListHost,
    onLoad: () -> Unit,
    onPutBack: (String) -> Unit,
) {
    val colors = ThingsTheme.colors
    LaunchedEffect(Unit) { onLoad() }

    ListScaffold(onBack = host.onBack, listState = host.listState) {
        item { PageHeader(title = "Trash", icon = { ListIcon(ThingsIcon.Trash, colors.trash, 26) }) }
        items(items.size, key = { i -> "t-${items[i].id}" }) { index ->
            TrashRow(items[index], onPutBack = { onPutBack(items[index].id) })
        }
        if (items.isEmpty()) {
            item { EmptyState(icon = listIcon(ThingsIcon.Trash, 56f), text = "The Trash is empty") }
        }
    }
}
