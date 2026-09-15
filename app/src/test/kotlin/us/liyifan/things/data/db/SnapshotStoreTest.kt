package us.liyifan.things.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import us.liyifan.things.data.api.TaskDto
import us.liyifan.things.model.Area
import us.liyifan.things.model.ChecklistItem
import us.liyifan.things.model.Item
import us.liyifan.things.model.ItemType
import us.liyifan.things.model.Schedule
import us.liyifan.things.model.Snapshot
import us.liyifan.things.model.Tag
import us.liyifan.things.model.ViewId
import us.liyifan.things.model.Views

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SnapshotStoreTest {

    private lateinit var db: AppDatabase
    private lateinit var store: SnapshotStore

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = SnapshotStore(db, Json { ignoreUnknownKeys = true; explicitNulls = false })
    }

    @After fun tearDown() = db.close()

    private fun snapshot(
        tasks: List<Item> = emptyList(),
        projects: List<Item> = emptyList(),
        views: Views = Views(),
    ) = Snapshot(
        today = TODAY,
        timezone = "America/Los_Angeles",
        generatedAt = "2026-09-15T12:00:00Z",
        areas = listOf(Area("a1", "Home", 0)),
        tags = listOf(Tag("t1", "errand", index = 0)),
        projects = projects,
        tasks = tasks,
        checklist = listOf(ChecklistItem("c1", "t1", "step", index = 0)),
        views = views,
        projectDone = mapOf("p1" to 4),
    )

    private fun task(id: String, title: String = id, tags: List<String> = emptyList()) =
        Item(id = id, type = ItemType.TASK, title = title, tagIds = tags, schedule = Schedule.ANYTIME)

    @Test fun `a snapshot round trips through the database`() = runTest {
        val original = snapshot(
            tasks = listOf(task("t1", "Alpha", tags = listOf("t1"))),
            projects = listOf(Item(id = "p1", type = ItemType.PROJECT, title = "Launch")),
            views = Views(anytime = listOf("t1")),
        )
        store.apply(original)
        val read = store.read()

        assertEquals(TODAY, read.today)
        assertEquals("America/Los_Angeles", read.timezone)
        assertEquals(listOf("t1"), read.tasks.map { it.id })
        assertEquals(listOf("t1"), read.tasks.single().tagIds)
        assertEquals(listOf("p1"), read.projects.map { it.id })
        assertEquals(listOf("t1"), read.views.anytime)
        assertEquals(mapOf("p1" to 4), read.projectDone)
        assertEquals(listOf("c1"), read.checklist.map { it.id })
    }

    @Test fun `view order is the server's, not the database's`() = runTest {
        store.apply(
            snapshot(
                tasks = listOf(task("a"), task("b"), task("c")),
                views = Views(today = listOf("c", "a", "b")),
            ),
        )
        assertEquals(listOf("c", "a", "b"), store.read().views.today)
    }

    @Test fun `a provisional row outlives the snapshot that does not contain it`() = runTest {
        store.apply(snapshot(tasks = listOf(task("server1")), views = Views(today = listOf("server1"))))

        val mine = task("tmp-mine", "Typed on a train").copy(provisional = true)
        db.taskDao().put(TaskEntity.from(mine))
        db.metaDao().bumpGeneration()

        // The server still knows nothing about it.
        store.apply(snapshot(tasks = listOf(task("server1")), views = Views(today = listOf("server1"))))

        val read = store.read()
        assertEquals(setOf("server1", "tmp-mine"), read.tasks.map { it.id }.toSet())
        assertTrue(read.tasks.single { it.id == "tmp-mine" }.provisional)
        // And it is filed into a list, by the same rule the server would have used.
        assertTrue("tmp-mine" in read.views[ViewId.ANYTIME])
    }

    @Test fun `applying a snapshot emits exactly once`() = runTest {
        store.snapshots().test {
            assertNull(awaitItem()) // nothing stored yet
            store.apply(snapshot(tasks = listOf(task("a"))))
            assertEquals(listOf("a"), awaitItem()?.tasks?.map { it.id })
            store.apply(snapshot(tasks = listOf(task("a"), task("b"))))
            assertEquals(listOf("a", "b"), awaitItem()?.tasks?.map { it.id }?.sorted())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `the logbook and trash are cached apart from the open world`() = runTest {
        store.apply(snapshot(tasks = listOf(task("open1"))))
        store.putLogbook(
            listOf(TaskDto(id = "done1", title = "Done", status = 3, completedAt = "2026-09-15T10:00:00Z")),
            scope = null,
            replace = true,
        )
        store.putTrash(listOf(TaskDto(id = "gone1", title = "Gone", inTrash = true)))

        // A completed row must not turn up in a list; that is the whole reason they live apart.
        assertEquals(listOf("open1"), store.read().tasks.map { it.id })
        assertEquals(listOf("done1"), store.logbook().map { it.id })
        assertEquals(listOf("gone1"), store.trash().map { it.id })
    }

    @Test fun `a project's logged items are scoped to it`() = runTest {
        store.putLogbook(listOf(TaskDto(id = "main")), scope = null, replace = true)
        store.putLogbook(listOf(TaskDto(id = "inproject")), scope = "p1", replace = true)
        assertEquals(listOf("main"), store.logbook().map { it.id })
        assertEquals(listOf("inproject"), store.logbook("p1").map { it.id })
    }

    @Test fun `wipe forgets everything, un-sent writes included`() = runTest {
        store.apply(snapshot(tasks = listOf(task("a")), views = Views(today = listOf("a"))))
        db.taskDao().put(TaskEntity.from(task("tmp-x").copy(provisional = true)))
        store.putTrash(listOf(TaskDto(id = "gone")))

        store.wipe()

        val read = store.read()
        assertEquals(emptyList<String>(), read.tasks.map { it.id })
        assertEquals(emptyList<String>(), read.views.today)
        assertEquals(emptyList<String>(), store.trash().map { it.id })
        assertNotNull(read.today) // falls back to the device's day rather than being empty
    }

    private companion object {
        const val TODAY = "2026-09-15"
    }
}
