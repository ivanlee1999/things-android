package us.liyifan.things.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import us.liyifan.things.data.api.ApiException
import us.liyifan.things.data.api.SnapshotDto
import us.liyifan.things.data.api.TaskAction
import us.liyifan.things.data.api.TaskDto
import us.liyifan.things.data.api.ViewsDto
import us.liyifan.things.data.db.AppDatabase
import us.liyifan.things.data.db.SnapshotStore
import us.liyifan.things.data.outbox.OutboxProcessor
import us.liyifan.things.model.NewTaskInit
import us.liyifan.things.model.Opt
import us.liyifan.things.model.Schedule
import us.liyifan.things.model.TaskPatch
import us.liyifan.things.model.ViewId
import us.liyifan.things.model.buildModel
import us.liyifan.things.model.isTempId

/**
 * The offline story, end to end: what the screen shows immediately, what is queued, what the
 * server is eventually told, and what happens when it says no.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HubThingsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var store: SnapshotStore
    private lateinit var api: FakeThingsApi
    private lateinit var repo: HubThingsRepository
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = SnapshotStore(db, json)
        api = FakeThingsApi()
        repo = HubThingsRepository(db = db, api = api, store = store, json = json)
    }

    @After fun tearDown() = db.close()

    private suspend fun seed(
        tasks: List<TaskDto> = emptyList(),
        projects: List<TaskDto> = emptyList(),
        areas: List<us.liyifan.things.data.api.AreaDto> = emptyList(),
        views: ViewsDto = ViewsDto(),
    ) {
        api.snapshotToReturn = SnapshotDto(
            today = TODAY, tasks = tasks, projects = projects, areas = areas, views = views,
        )
        repo.refresh(sync = false, force = true)
    }

    private suspend fun model() = buildModel(store.read())

    // -- optimistic writes --------------------------------------------------------------------

    @Test fun `a new to-do is on screen and in its list before the server hears of it`() = runTest {
        seed()
        api.failAllWith = ApiException.network("offline")

        val tempId = repo.createTask(NewTaskInit(title = "Buy milk", whenValue = "today"))

        assertTrue(isTempId(tempId))
        val m = model()
        assertEquals("Buy milk", m.tasksById.getValue(tempId).title)
        assertEquals(listOf(tempId), m.view(ViewId.TODAY).map { it.id })
        assertEquals(1, repo.processor.pending())
        assertTrue(api.only<FakeThingsApi.Call.CreateTask>().isEmpty())
    }

    @Test fun `edits, completions and checklist items queue up behind the create`() = runTest {
        seed()
        api.failAllWith = ApiException.network("offline")

        val tempId = repo.createTask(NewTaskInit(title = "Draft"))
        repo.updateTask(tempId, TaskPatch(title = Opt.of("Draft the note")))
        repo.addChecklistItem(tempId, "outline")
        repo.moveTask(tempId, "today")

        assertEquals(4, repo.processor.pending())
        val m = model()
        assertEquals("Draft the note", m.tasksById.getValue(tempId).title)
        assertEquals(listOf("outline"), m.checklistByTask.getValue(tempId).map { it.title })
        assertEquals(ViewId.TODAY, m.listOf[tempId])
    }

    @Test fun `draining swaps the temp id everywhere, in the queue included`() = runTest {
        seed()
        api.failAllWith = ApiException.network("offline")
        val tempId = repo.createTask(NewTaskInit(title = "Draft", whenValue = "today"))
        repo.updateTask(tempId, TaskPatch(title = Opt.of("Draft the note")))
        repo.addChecklistItem(tempId, "outline")

        api.failAllWith = null
        val result = repo.processor.drain()

        assertEquals(OutboxProcessor.Result.Drained(sent = 3, needsFullRefresh = false), result)
        val realId = api.only<FakeThingsApi.Call.CreateTask>().single().minted
        // The edit and the checklist item were sent against the real id, not the temp one.
        assertEquals(realId, api.only<FakeThingsApi.Call.EditTask>().single().fields.uuid)
        assertEquals(realId, api.only<FakeThingsApi.Call.CreateChecklist>().single().taskUuid)

        val m = model()
        assertNull(m.tasksById[tempId])
        assertEquals("Draft the note", m.tasksById.getValue(realId).title)
        assertEquals(listOf(realId), m.view(ViewId.TODAY).map { it.id })
        assertEquals(realId, m.checklistByTask.getValue(realId).single().taskId)
        assertFalse(m.tasksById.getValue(realId).provisional)
    }

    @Test fun `a to-do created inside a project is filed with a second call`() = runTest {
        seed(projects = listOf(TaskDto(id = "p1", type = 1, title = "Launch")))
        val tempId = repo.createTask(NewTaskInit(title = "Ship it", project = "p1", heading = "h1"))
        repo.processor.drain()

        val create = api.only<FakeThingsApi.Call.CreateTask>().single()
        assertEquals("p1", create.request.project)
        // The create endpoint takes no heading, so the heading is its own queued write, sent
        // against the real id once the create ahead of it has resolved.
        val edit = api.only<FakeThingsApi.Call.EditTask>().single()
        assertEquals("h1", edit.fields.heading)
        assertEquals(create.minted, edit.fields.uuid)
        assertNotNull(model().tasksById[create.minted])
        assertNull(model().tasksById[tempId])
    }

    @Test fun `a project created offline can hold to-dos that resolve with it`() = runTest {
        seed()
        api.failAllWith = ApiException.network("offline")
        val projectId = repo.createProject("Garden", areaId = null)
        val taskId = repo.createTask(NewTaskInit(title = "Buy seeds", project = projectId))

        assertEquals(listOf(taskId), model().tasksByProject.getValue(projectId).map { it.id })

        api.failAllWith = null
        repo.processor.drain()

        val realProject = api.only<FakeThingsApi.Call.CreateProject>().single().minted
        val create = api.only<FakeThingsApi.Call.CreateTask>().single()
        assertEquals("the to-do must be created against the project's real id", realProject, create.request.project)
        assertEquals(listOf(create.minted), model().tasksByProject.getValue(realProject).map { it.id })
    }

    @Test fun `completing takes the row out of its lists and tells the server after`() = runTest {
        seed(tasks = listOf(TaskDto(id = "t1", title = "Alpha")), views = ViewsDto(today = listOf("t1")))
        repo.completeTask("t1", done = true)

        assertTrue(model().view(ViewId.TODAY).isEmpty())

        repo.processor.drain()
        assertEquals(TaskAction.COMPLETE, api.only<FakeThingsApi.Call.Action>().single().action)
        repo.forgetCompleted("t1")
        assertNull(model().tasksById["t1"])
    }

    @Test fun `an edit moves a to-do between lists at once`() = runTest {
        seed(
            tasks = listOf(TaskDto(id = "t1", title = "Alpha", schedule = Schedule.ANYTIME)),
            views = ViewsDto(anytime = listOf("t1")),
        )
        repo.updateTask("t1", TaskPatch(whenValue = Opt.of("someday")))

        val m = model()
        assertTrue(m.view(ViewId.ANYTIME).isEmpty())
        assertEquals(listOf("t1"), m.view(ViewId.SOMEDAY).map { it.id })
    }

    @Test fun `deleting an area trashes its projects and loose to-dos first`() = runTest {
        seed(
            tasks = listOf(TaskDto(id = "t1", title = "Loose", areaId = "a1")),
            projects = listOf(TaskDto(id = "p1", type = 1, title = "Project", areaId = "a1")),
            areas = listOf(us.liyifan.things.data.api.AreaDto(id = "a1", title = "Home")),
        )
        repo.deleteArea("a1")

        assertNull(model().areasById["a1"])
        assertEquals(3, repo.processor.pending())

        repo.processor.drain()
        val actions = api.only<FakeThingsApi.Call.Action>().map { it.uuid }
        assertEquals(listOf("p1", "t1"), actions)
        assertEquals("a1", api.only<FakeThingsApi.Call.DeleteArea>().single().uuid)
    }

    // -- failure ------------------------------------------------------------------------------

    @Test fun `a refused write is dropped, not retried forever`() = runTest {
        seed(tasks = listOf(TaskDto(id = "old-style-id", title = "Ancient")))
        repo.trashTask("old-style-id")
        api.failNextWith = ApiException(400, "writes to legacy ids are not allowed")

        val result = repo.processor.drain()

        assertEquals(OutboxProcessor.Result.Drained(sent = 0, needsFullRefresh = true), result)
        assertEquals(0, repo.processor.pending())
    }

    @Test fun `a refused create takes its dependants with it`() = runTest {
        seed()
        api.failAllWith = ApiException.network("offline")
        val tempId = repo.createTask(NewTaskInit(title = "Doomed"))
        repo.updateTask(tempId, TaskPatch(title = Opt.of("Still doomed")))
        repo.addChecklistItem(tempId, "never sent")
        assertEquals(3, repo.processor.pending())

        api.failAllWith = null
        api.failNextWith = ApiException(400, "no")
        repo.processor.drain()

        // Nothing is left queued against an id the server never minted, and the row is gone.
        assertEquals(0, repo.processor.pending())
        assertNull(model().tasksById[tempId])
        assertTrue(model().checklistByTask[tempId].isNullOrEmpty())
        assertTrue(api.only<FakeThingsApi.Call.EditTask>().isEmpty())
    }

    @Test fun `a network failure leaves the queue exactly as it was`() = runTest {
        seed()
        api.failAllWith = ApiException.network("offline")
        repo.createTask(NewTaskInit(title = "Later"))

        val result = repo.processor.drain()

        assertTrue(result is OutboxProcessor.Result.Blocked)
        assertEquals(1, (result as OutboxProcessor.Result.Blocked).remaining)
        assertEquals(1, repo.processor.pending())
    }

    @Test fun `a queued write is never overwritten by a snapshot that predates it`() = runTest {
        seed(tasks = listOf(TaskDto(id = "t1", title = "Alpha")), views = ViewsDto(today = listOf("t1")))
        api.failAllWith = ApiException.network("offline")
        repo.completeTask("t1", done = true)
        api.failAllWith = null

        // The server still believes t1 is open; applying that would resurrect it.
        val result = repo.refresh(sync = true)

        assertEquals(RefreshResult.SkippedPendingWrites, result)
        assertTrue(model().view(ViewId.TODAY).isEmpty())
    }

    @Test fun `an un-sent to-do is never dropped by a refresh, and survives to be sent`() = runTest {
        seed()
        api.failAllWith = ApiException.network("offline")
        val tempId = repo.createTask(NewTaskInit(title = "Unsent", whenValue = "today"))
        api.failAllWith = null

        api.snapshotToReturn = SnapshotDto(
            today = TODAY,
            tasks = listOf(TaskDto(id = "t9", title = "From the server")),
            views = ViewsDto(today = listOf("t9")),
        )

        // Not applied at all while the create is queued — even forced, because force means
        // "draw it though someone is reading", not "overwrite work not yet sent".
        assertEquals(RefreshResult.SkippedPendingWrites, repo.refresh(sync = true, force = true))
        assertNotNull(model().tasksById[tempId])

        repo.processor.drain()
        val realId = api.only<FakeThingsApi.Call.CreateTask>().single().minted
        api.snapshotToReturn = SnapshotDto(
            today = TODAY,
            tasks = listOf(
                TaskDto(id = "t9", title = "From the server"),
                TaskDto(id = realId, title = "Unsent", scheduledDate = TODAY),
            ),
            views = ViewsDto(today = listOf("t9", realId)),
        )
        repo.refresh(sync = true, force = true)

        val m = model()
        assertEquals(setOf("t9", realId), m.view(ViewId.TODAY).map { it.id }.toSet())
        assertNull("the provisional row is gone, not duplicated", m.tasksById[tempId])
    }

    // -- staging ------------------------------------------------------------------------------

    @Test fun `a snapshot arriving while the user reads is held until they act`() = runTest {
        var reading = true
        val quiet = HubThingsRepository(
            db = db, api = api, store = store, json = json, readyToDraw = { !reading },
        )
        api.snapshotToReturn = SnapshotDto(today = TODAY, tasks = listOf(TaskDto(id = "t1", title = "One")))
        quiet.refresh(sync = false, force = true)

        api.snapshotToReturn = SnapshotDto(
            today = TODAY,
            tasks = listOf(TaskDto(id = "t1", title = "One"), TaskDto(id = "t2", title = "Two")),
        )
        assertEquals(RefreshResult.Staged, quiet.refresh(sync = true))
        assertEquals("the list has not moved under the reader", 1, model().snapshot.tasks.size)
        assertTrue(quiet.syncState.value.stagedAvailable)

        reading = false
        quiet.applyStagedIfAny()
        assertEquals(2, model().snapshot.tasks.size)
        assertFalse(quiet.syncState.value.stagedAvailable)
    }

    @Test fun `resync throws everything away and starts again`() = runTest {
        seed(tasks = listOf(TaskDto(id = "t1", title = "Alpha")))
        api.failAllWith = ApiException.network("offline")
        repo.createTask(NewTaskInit(title = "Unsent"))
        api.failAllWith = null

        api.snapshotToReturn = SnapshotDto(today = TODAY, tasks = listOf(TaskDto(id = "t2", title = "Fresh")))
        repo.resync()

        assertEquals(0, repo.processor.pending())
        assertEquals(listOf("t2"), model().snapshot.tasks.map { it.id })
    }

    @Test fun `a write made while a read is in flight is not undone by the answer`() = runTest {
        seed(tasks = listOf(TaskDto(id = "t1", title = "Alpha")), views = ViewsDto(today = listOf("t1")))

        // The server is about to answer a read that still believes t1 is open. The completion
        // happens between the request and the answer, which is the race this guards.
        api.snapshotToReturn = SnapshotDto(
            today = TODAY,
            tasks = listOf(TaskDto(id = "t1", title = "Alpha")),
            views = ViewsDto(today = listOf("t1")),
        )
        repo.completeTask("t1", done = true)

        val result = repo.refresh(sync = true)

        assertEquals(RefreshResult.SkippedPendingWrites, result)
        assertTrue("a completed to-do must not come back", model().view(ViewId.TODAY).isEmpty())
    }

    @Test fun `an unexpected failure drops the row rather than wedging the queue behind it`() = runTest {
        seed()
        // Not an ApiException at all — a bug turning the op into a request, say. The queue is
        // drained strictly in order, so a row that throws forever at the head would stop every
        // later write from ever being sent.
        api.failAllWith = null
        val hostile = object : FakeThingsApi() {
            override suspend fun createTask(request: us.liyifan.things.data.api.CreateTaskRequest): String =
                throw IllegalStateException("something unforeseen")
        }
        val repo2 = HubThingsRepository(db = db, api = hostile, store = store, json = json)
        repo2.createTask(NewTaskInit(title = "doomed"))
        repo2.createTask(NewTaskInit(title = "also queued"))
        assertEquals(2, repo2.processor.pending())

        val result = repo2.processor.drain()

        assertTrue(result is OutboxProcessor.Result.Drained)
        assertEquals("both rows were dealt with, not left blocking", 0, repo2.processor.pending())
    }

    @Test fun `an offline stretch never counts towards giving up`() = runTest {
        seed()
        api.failAllWith = ApiException.network("offline")
        repo.createTask(NewTaskInit(title = "written on a train"))

        // Far more attempts than the give-up threshold, all of them network failures.
        repeat(25) { repo.processor.drain() }

        assertEquals("the to-do is still queued", 1, repo.processor.pending())
        api.failAllWith = null
        repo.processor.drain()
        assertEquals(1, api.only<FakeThingsApi.Call.CreateTask>().size)
    }

    @Test fun `un-ticking from the Logbook actually does something`() = runTest {
        seed()
        api.logbookToReturn = listOf(
            TaskDto(id = "done1", title = "Was finished", status = 3, completedAt = "2026-09-15T10:00:00Z"),
        )
        repo.loadLogbook()

        repo.completeTask("done1", done = false)

        // The row is not in the open world when it is reopened — it is in the cached Logbook,
        // which is what an earlier version failed to look at, so the tick did nothing at all.
        val m = model()
        assertNotNull(m.tasksById["done1"])
        assertEquals(us.liyifan.things.model.Status.OPEN, m.tasksById.getValue("done1").status)
        repo.processor.drain()
        assertEquals(TaskAction.UNCOMPLETE, api.only<FakeThingsApi.Call.Action>().single().action)
    }

    @Test fun `a tick is queued before the settle pause, not after it`() = runTest {
        seed(tasks = listOf(TaskDto(id = "t1", title = "Alpha")), views = ViewsDto(today = listOf("t1")))
        repo.completeTask("t1", done = true)

        // Durable immediately: killing the app during the pause cannot lose it.
        assertEquals(1, repo.processor.pending())
        // And still on screen, ticked, so the pause has something to show.
        assertEquals(us.liyifan.things.model.Status.COMPLETED, model().tasksById.getValue("t1").status)
        assertTrue(model().view(ViewId.TODAY).isEmpty())

        repo.forgetCompleted("t1")
        assertNull(model().tasksById["t1"])
    }

    @Test fun `a queued write survives a forced refresh`() = runTest {
        seed(tasks = listOf(TaskDto(id = "t1", title = "Alpha")), views = ViewsDto(today = listOf("t1")))
        api.failAllWith = ApiException.network("offline")
        repo.trashTask("t1")
        api.failAllWith = null

        // force means "draw it even though someone is reading", never "overwrite work that has
        // not been sent" — pull-to-refresh passes force, and used to be able to undo a delete.
        assertEquals(RefreshResult.SkippedPendingWrites, repo.refresh(sync = true, force = true))
        assertNull(model().tasksById["t1"])
    }

    @Test fun `a refused create takes the rows of everything beneath it`() = runTest {
        seed()
        api.failAllWith = ApiException.network("offline")
        val projectId = repo.createProject("Doomed", areaId = null)
        val headingId = repo.createHeading("Beneath it", projectId)
        val taskId = repo.createTask(NewTaskInit(title = "Beneath that", project = projectId))
        api.failAllWith = null
        api.failNextWith = ApiException(400, "no")

        repo.processor.drain()

        // Nothing queued, and no orphans left on screen that could never be sent.
        assertEquals(0, repo.processor.pending())
        val m = model()
        assertNull(m.projectsById[projectId])
        assertNull(m.headingsById[headingId])
        assertNull(m.tasksById[taskId])
    }

    private companion object {
        const val TODAY = "2026-09-15"
    }
}
