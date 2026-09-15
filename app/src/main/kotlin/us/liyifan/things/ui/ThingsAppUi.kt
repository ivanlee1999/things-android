package us.liyifan.things.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import us.liyifan.things.model.Item
import us.liyifan.things.model.Model
import us.liyifan.things.model.NewTaskInit
import us.liyifan.things.model.Opt
import us.liyifan.things.model.TaskPatch
import us.liyifan.things.model.ViewId
import us.liyifan.things.sync.UiActivity
import us.liyifan.things.ui.components.ConfirmDialog
import us.liyifan.things.ui.components.ContextMenuSheet
import us.liyifan.things.ui.components.EditorActions
import us.liyifan.things.ui.components.MenuEntry
import us.liyifan.things.ui.components.NewList
import us.liyifan.things.ui.components.NewListSheet
import us.liyifan.things.ui.components.RowContext
import us.liyifan.things.ui.components.TaskEditor
import us.liyifan.things.ui.components.TaskRow
import us.liyifan.things.ui.components.ThingsFab
import us.liyifan.things.ui.components.ThingsSheet
import us.liyifan.things.ui.components.ThingsToast
import us.liyifan.things.ui.components.taskMenu
import us.liyifan.things.ui.eink.EpdRefresher
import us.liyifan.things.ui.icons.ThingsIcon
import us.liyifan.things.ui.nav.Route
import us.liyifan.things.ui.pickers.DeadlinePicker
import us.liyifan.things.ui.pickers.MovePicker
import us.liyifan.things.ui.pickers.TagPicker
import us.liyifan.things.ui.pickers.WhenPicker
import us.liyifan.things.ui.screens.AreaScreen
import us.liyifan.things.ui.screens.ConnectionStatus
import us.liyifan.things.ui.screens.FlatListScreen
import us.liyifan.things.ui.screens.GroupedListScreen
import us.liyifan.things.ui.screens.HomeScreen
import us.liyifan.things.ui.screens.ListHost
import us.liyifan.things.ui.screens.LogbookScreen
import us.liyifan.things.ui.screens.ProjectActions
import us.liyifan.things.ui.screens.ProjectScreen
import us.liyifan.things.ui.screens.SearchScreen
import us.liyifan.things.ui.screens.SettingsScreen
import us.liyifan.things.ui.screens.TrashScreen
import us.liyifan.things.ui.screens.UpcomingScreen
import us.liyifan.things.ui.theme.ThingsTheme
import us.liyifan.things.vm.AppViewModel

/** Which sheet, if any, is open over the list. */
private sealed interface Sheet {
    data class RowMenu(val taskId: String) : Sheet
    data class When(val taskId: String) : Sheet
    data class Deadline(val taskId: String) : Sheet
    data class Tags(val taskId: String) : Sheet
    data class Move(val taskId: String) : Sheet
    data object NewList : Sheet
    data class ProjectMenu(val projectId: String) : Sheet
    data class AreaMenu(val areaId: String) : Sheet
}

/**
 * The whole app above the data layer: the navigation graph, the one open card, and the sheets.
 *
 * There is a single expanded to-do at a time, held in the view model rather than per screen,
 * because the card opens in place inside whichever list and has to survive the id underneath it
 * being replaced when the server answers a create.
 */
@Composable
fun ThingsAppUi(
    viewModel: AppViewModel,
    connection: ConnectionUi,
    version: String,
    navController: NavHostController = rememberNavController(),
) {
    val model by viewModel.model.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val eink = ThingsTheme.eink
    val view = LocalView.current
    val epd = remember { EpdRefresher.forThisDevice() }

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backStackEntry?.id) {
        viewModel.onNavigated()
        // One full repaint after a page change: partial refreshes are what make e-ink quick and
        // also what leaves the last screen's ghost behind.
        if (eink.enabled && eink.fullRefresh) epd.fullRefresh(view)
    }

    LaunchedEffect(ui.expandedId) { UiActivity.setEditorOpen(ui.expandedId != null) }

    Box(
        Modifier
            .fillMaxSize()
            // Any touch counts as "the reader is here", which is what releases a snapshot that
            // was held back rather than drawn under them.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        UiActivity.touched()
                    }
                }
            },
    ) {
        val m = model
        if (m == null) {
            LoadingOrConnect(connection = connection, version = version)
            return@Box
        }

        val host = rememberListHost(
            viewModel = viewModel,
            model = m,
            ui = ui,
            navController = navController,
            onOpenSheet = { sheet = it },
        )

        // Read once here: a NavHost transition lambda is not a composable scope, and in e-ink
        // mode these all return None anyway.
        val motion = ThingsTheme.motion
        NavHost(
            navController = navController,
            startDestination = if (connection.configured) Route.Home else Route.Connect,
            enterTransition = { motion.enter() },
            exitTransition = { motion.exit() },
            popEnterTransition = { motion.popEnter() },
            popExitTransition = { motion.popExit() },
        ) {
            composable<Route.Connect> {
                us.liyifan.things.ui.screens.ConnectScreen(
                    config = connection.config,
                    onConfigChange = connection.onChange,
                    status = connection.status,
                    onTest = connection.onTest,
                    onSave = {
                        connection.onSave()
                        navController.navigate(Route.Home) { popUpTo(Route.Connect) { inclusive = true } }
                    },
                )
            }
            composable<Route.Home> {
                HomeScreen(
                    model = m,
                    syncLabel = syncLabel(syncState.lastSyncedAt, syncState.pendingWrites),
                    stagedAvailable = syncState.stagedAvailable,
                    onNavigate = { navController.navigate(it) },
                    onSearch = { navController.navigate(Route.Search(it)) },
                    onNewList = { sheet = Sheet.NewList },
                    onSyncTap = { viewModel.refresh(sync = true, force = true) },
                )
            }
            composable<Route.Inbox> {
                FlatListScreen(
                    "Inbox", ThingsIcon.Inbox, ThingsTheme.colors.inbox, ViewId.INBOX, m, host,
                    emptyText = "Your Inbox is empty", showCrumb = false, showWhen = true,
                )
            }
            composable<Route.Today> {
                FlatListScreen(
                    "Today", ThingsIcon.Today, ThingsTheme.colors.today, ViewId.TODAY, m, host,
                    emptyText = "Nothing for today", showCrumb = true, showWhen = false,
                )
            }
            composable<Route.Upcoming> { UpcomingScreen(m, host) }
            composable<Route.Anytime> {
                GroupedListScreen(
                    "Anytime", ThingsIcon.Anytime, ThingsTheme.colors.anytime, ViewId.ANYTIME, m, host,
                    emptyText = "Nothing to do",
                )
            }
            composable<Route.Someday> {
                GroupedListScreen(
                    "Someday", ThingsIcon.Someday, ThingsTheme.colors.someday, ViewId.SOMEDAY, m, host,
                    emptyText = "Nothing put off",
                )
            }
            composable<Route.Logbook> {
                val items by viewModel.logbook.collectAsStateWithLifecycle()
                val complete by viewModel.logbookComplete.collectAsStateWithLifecycle()
                LogbookScreen(
                    items = items, model = m, host = host, complete = complete,
                    onLoad = { viewModel.loadLogbook(it) },
                    onUncomplete = { viewModel.completeTask(it, false) },
                )
            }
            composable<Route.Trash> {
                val items by viewModel.trash.collectAsStateWithLifecycle()
                TrashScreen(
                    items = items, host = host,
                    onLoad = { viewModel.loadTrash() },
                    onPutBack = { viewModel.untrashTask(it) },
                )
            }
            composable<Route.Project> { entry ->
                val route = entry.toRoute<Route.Project>()
                val project = m.projectsById[route.id]
                val logged by viewModel.loggedByProject.collectAsStateWithLifecycle()
                if (project == null) {
                    us.liyifan.things.ui.components.EmptyState(
                        icon = us.liyifan.things.ui.icons.listIcon(ThingsIcon.Trash, 56f),
                        text = "This project is no longer here",
                    )
                } else {
                    ProjectScreen(
                        project = project,
                        model = m,
                        host = host,
                        logged = logged[route.id],
                        isNew = route.isNew,
                        actions = ProjectActions(
                            onRename = { viewModel.updateTask(project.id, TaskPatch(title = Opt.of(it))) },
                            onEditNote = { viewModel.updateTask(project.id, TaskPatch(note = Opt.of(it))) },
                            onOpenMenu = { sheet = Sheet.ProjectMenu(project.id) },
                            onRenameHeading = { id, title -> viewModel.updateTask(id, TaskPatch(title = Opt.of(title))) },
                            onDeleteHeading = { id ->
                                confirm = ConfirmRequest(
                                    title = "Delete heading?",
                                    text = "Its to-dos stay in the project.",
                                    confirmLabel = "Delete",
                                ) { viewModel.trashTask(id) }
                            },
                            onLoadLogged = { viewModel.loadLogged(project.id) },
                            onUncomplete = { viewModel.completeTask(it, false) },
                        ),
                    )
                }
            }
            composable<Route.Area> { entry ->
                val route = entry.toRoute<Route.Area>()
                val area = m.areasById[route.id]
                if (area != null) {
                    AreaScreen(
                        area = area,
                        model = m,
                        host = host,
                        onRename = { viewModel.renameArea(area.id, it) },
                        onOpenMenu = { sheet = Sheet.AreaMenu(area.id) },
                        onOpenProject = { navController.navigate(Route.Project(it)) },
                    )
                }
            }
            composable<Route.Search> { entry ->
                SearchScreen(
                    model = m,
                    host = host,
                    initialQuery = entry.toRoute<Route.Search>().q,
                    onOpenProject = { navController.navigate(Route.Project(it)) },
                    onOpenArea = { navController.navigate(Route.Area(it)) },
                )
            }
            composable<Route.Settings> {
                val appearance by viewModel.appearance.collectAsStateWithLifecycle()
                SettingsScreen(
                    config = connection.config,
                    onConfigChange = connection.onChange,
                    appearance = appearance,
                    onAppearanceChange = { viewModel.saveAppearance(it) },
                    status = connection.status,
                    onTest = connection.onTest,
                    onSave = connection.onSave,
                    onResync = { viewModel.resync() },
                    onSignOut = connection.onSignOut,
                    onBack = { navController.popBackStack() },
                    host = host,
                    pendingWrites = syncState.pendingWrites,
                    version = version,
                )
            }
        }

        val route = backStackEntry?.destination?.route.orEmpty()
        val showFab = listOf("Logbook", "Trash", "Settings", "Connect").none { route.endsWith(it) }
        if (showFab) {
            ThingsFab(
                onClick = { viewModel.createTask(newHere(route, backStackEntry?.arguments)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 24.dp),
            )
        }

        ui.toast?.let { message ->
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 56.dp)) {
                ThingsToast(message)
            }
        }

        SheetHost(sheet = sheet, model = m, viewModel = viewModel, onDismiss = { sheet = null }, onOpen = { sheet = it }, navController = navController)

        confirm?.let { request ->
            ConfirmDialog(
                title = request.title,
                text = request.text,
                confirmLabel = request.confirmLabel,
                onConfirm = request.onConfirm,
                onDismiss = { confirm = null },
            )
        }

        BackHandler(enabled = ui.expandedId != null) { viewModel.expand(null) }
    }
}

private data class ConfirmRequest(
    val title: String,
    val text: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

/** Everything the connection form needs, so the UI holds no settings plumbing of its own. */
data class ConnectionUi(
    val config: us.liyifan.things.data.settings.ConnectionConfig,
    val configured: Boolean,
    val status: ConnectionStatus,
    val onChange: (us.liyifan.things.data.settings.ConnectionConfig) -> Unit,
    val onTest: () -> Unit,
    val onSave: () -> Unit,
    val onSignOut: () -> Unit,
)

/**
 * A new to-do lands where you are: typed in Today it is for today, typed in a project it joins
 * that project, and typed anywhere else it goes to the Inbox.
 */
private fun newHere(route: String, args: android.os.Bundle?): NewTaskInit = when {
    route.endsWith("Today") -> NewTaskInit(whenValue = "today")
    route.endsWith("Anytime") -> NewTaskInit(whenValue = "anytime")
    route.endsWith("Someday") -> NewTaskInit(whenValue = "someday")
    route.contains("Project") -> NewTaskInit(project = args?.getString("id"))
    route.contains("Area") -> NewTaskInit(area = args?.getString("id"), whenValue = "anytime")
    else -> NewTaskInit()
}

/** "Synced 10:42", or what is waiting to be sent, which matters more when something is. */
private fun syncLabel(lastSyncedAt: Long, pendingWrites: Int): String = when {
    pendingWrites > 0 -> "$pendingWrites change${if (pendingWrites == 1) "" else "s"} waiting to send"
    lastSyncedAt == 0L -> "Not synced yet"
    else -> {
        val time = java.time.Instant.ofEpochMilli(lastSyncedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
        "Synced %d:%02d".format(time.hour, time.minute)
    }
}

@Composable
private fun LoadingOrConnect(connection: ConnectionUi, version: String) {
    if (!connection.configured) {
        us.liyifan.things.ui.screens.ConnectScreen(
            config = connection.config,
            onConfigChange = connection.onChange,
            status = connection.status,
            onTest = connection.onTest,
            onSave = connection.onSave,
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text(
                "Loading…",
                style = ThingsTheme.type.empty,
                color = ThingsTheme.colors.text3,
            )
        }
    }
}

/**
 * Draws a row, or the editing card in its place.
 *
 * Both are emitted under the same lazy-list key, so opening a to-do replaces the row exactly
 * where it stood rather than inserting a second item and pushing the list around.
 */
@Composable
private fun rememberListHost(
    viewModel: AppViewModel,
    model: Model,
    ui: us.liyifan.things.vm.UiState,
    navController: NavHostController,
    onOpenSheet: (Sheet) -> Unit,
): ListHost {
    val listState = rememberLazyListState()
    return remember(model, ui, listState) {
        object : ListHost {
            override val listState: LazyListState = listState
            override val onBack: (() -> Unit)? = { navController.popBackStack() }
            override val tagFilter: String? = ui.tagFilter
            override val onTagFilter: (String?) -> Unit = { viewModel.setTagFilter(it) }

            @Composable
            override fun row(task: Item, ctx: RowContext) {
                if (ui.expandedId == task.id && !ctx.logged) {
                    TaskEditor(
                        task = task,
                        model = model,
                        actions = EditorActions(
                            onSave = { viewModel.updateTask(task.id, it) },
                            onComplete = { viewModel.completeTask(task.id, it) },
                            onAddChecklistItem = { viewModel.addChecklistItem(task.id, it) },
                            onToggleChecklistItem = { id, done -> viewModel.toggleChecklistItem(id, done) },
                            onDeleteChecklistItem = { viewModel.deleteChecklistItem(it) },
                            onOpenWhen = { onOpenSheet(Sheet.When(task.id)) },
                            onOpenDeadline = { onOpenSheet(Sheet.Deadline(task.id)) },
                            onOpenTags = { onOpenSheet(Sheet.Tags(task.id)) },
                        ),
                    )
                } else {
                    TaskRow(
                        task = task,
                        model = model,
                        ctx = ctx,
                        selected = ui.selectedId == task.id,
                        settling = task.id in ui.settling,
                        onTap = { viewModel.expand(task.id) },
                        onLongPress = { viewModel.select(task.id); onOpenSheet(Sheet.RowMenu(task.id)) },
                        onCheck = { viewModel.completeTask(task.id, it) },
                    )
                }
            }
        }
    }
}

/** Whichever sheet is open, and what choosing something in it does. */
@Composable
private fun SheetHost(
    sheet: Sheet?,
    model: Model,
    viewModel: AppViewModel,
    navController: NavHostController,
    onDismiss: () -> Unit,
    onOpen: (Sheet) -> Unit,
) {
    when (sheet) {
        null -> Unit

        is Sheet.RowMenu -> ContextMenuSheet(
            entries = taskMenu(
                onMove = { viewModel.moveTask(sheet.taskId, it) },
                onOpenMovePicker = { onOpen(Sheet.Move(sheet.taskId)) },
                onComplete = { viewModel.completeTask(sheet.taskId, true) },
                onCancel = { viewModel.cancelTask(sheet.taskId) },
                onDelete = { viewModel.trashTask(sheet.taskId) },
            ),
            onDismiss = onDismiss,
        )

        is Sheet.When -> model.item(sheet.taskId)?.let { task ->
            ThingsSheet(onDismiss = onDismiss) {
                WhenPicker(task) { value ->
                    viewModel.updateTask(task.id, TaskPatch(whenValue = Opt.of(value)))
                    onDismiss()
                }
            }
        }

        is Sheet.Deadline -> model.item(sheet.taskId)?.let { task ->
            ThingsSheet(onDismiss = onDismiss) {
                DeadlinePicker(task) { date ->
                    viewModel.updateTask(task.id, TaskPatch(deadline = Opt.of(date)))
                    onDismiss()
                }
            }
        }

        is Sheet.Tags -> model.item(sheet.taskId)?.let { task ->
            ThingsSheet(onDismiss = onDismiss) {
                TagPicker(
                    task = task,
                    model = model,
                    onToggle = { viewModel.updateTask(task.id, TaskPatch(tagIds = Opt.of(it))) },
                    onCreate = { title ->
                        viewModel.createTag(title) { id ->
                            viewModel.updateTask(task.id, TaskPatch(tagIds = Opt.of(task.tagIds + id)))
                        }
                    },
                    onDone = onDismiss,
                )
            }
        }

        is Sheet.Move -> ThingsSheet(onDismiss = onDismiss) {
            MovePicker(model) { patch ->
                viewModel.updateTask(sheet.taskId, patch)
                onDismiss()
            }
        }

        Sheet.NewList -> NewListSheet(
            onDismiss = onDismiss,
            onPick = { choice ->
                when (choice) {
                    NewList.PROJECT -> viewModel.createProject("", areaId = null) { id ->
                        navController.navigate(Route.Project(id, isNew = true))
                    }
                    NewList.AREA -> viewModel.createArea("") { id ->
                        navController.navigate(Route.Area(id, isNew = true))
                    }
                }
            },
        )

        is Sheet.ProjectMenu -> {
            val project = model.projectsById[sheet.projectId]
            ContextMenuSheet(
                entries = listOfNotNull(
                    MenuEntry.Action("Complete Project", { viewModel.completeTask(sheet.projectId, true) }),
                    MenuEntry.Separator,
                    MenuEntry.Action("Anytime", {
                        viewModel.updateTask(sheet.projectId, TaskPatch(whenValue = Opt.of("anytime")))
                    }),
                    MenuEntry.Action("Someday", {
                        viewModel.updateTask(sheet.projectId, TaskPatch(whenValue = Opt.of("someday")))
                    }),
                    MenuEntry.Separator,
                    MenuEntry.Action("New Heading", { viewModel.createHeading("New Heading", sheet.projectId) }),
                    MenuEntry.Separator,
                    MenuEntry.Action(
                        label = "Delete Project",
                        onSelect = {
                            viewModel.trashTask(sheet.projectId)
                            navController.popBackStack()
                        },
                        danger = true,
                    ),
                ),
                onDismiss = onDismiss,
            )
        }

        is Sheet.AreaMenu -> ContextMenuSheet(
            entries = listOf(
                MenuEntry.Action("New Project", {
                    viewModel.createProject("", areaId = sheet.areaId) { id ->
                        navController.navigate(Route.Project(id, isNew = true))
                    }
                }),
                MenuEntry.Separator,
                MenuEntry.Action(
                    label = "Delete Area",
                    onSelect = {
                        viewModel.deleteArea(sheet.areaId)
                        navController.popBackStack()
                    },
                    danger = true,
                ),
            ),
            onDismiss = onDismiss,
        )
    }
}
