package us.liyifan.things.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import us.liyifan.things.model.ItemType
import us.liyifan.things.model.Schedule
import us.liyifan.things.model.ViewId
import us.liyifan.things.model.buildModel

/**
 * Decoding a snapshot shaped exactly like the real server's.
 *
 * The shape is taken from a live `GET /api/snapshot` — which fields are always present, which
 * are omitted entirely (`note`), and which arrive as an explicit null (every id and date). The
 * content is invented, because the real one is somebody's to-do list. Those two behaviours are
 * different and both have to survive: an omitted field must take its default, and a null one
 * must stay null rather than becoming an empty string.
 */
class SnapshotDecodingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
    }

    private val wire = """
    {
      "generatedAt": "2026-09-15T19:26:54Z",
      "timezone": "America/Los_Angeles",
      "today": "2026-09-15",
      "areas": [{"id": "a1", "title": "Home", "index": 0}],
      "tags": [{"id": "g1", "title": "errand", "parentId": null, "index": 0}],
      "projects": [
        {"id": "p1", "type": 1, "title": "Repaint the hall", "status": 0, "schedule": 1,
         "scheduledDate": null, "deadline": null, "areaId": "a1", "projectId": null,
         "headingId": null, "tagIds": [], "index": -2263, "todayIndex": 0, "repeating": false,
         "inTrash": false, "createdAt": "2026-01-02T10:00:00Z", "modifiedAt": null,
         "completedAt": null}
      ],
      "headings": [
        {"id": "h1", "type": 2, "title": "Prep", "status": 0, "schedule": 1,
         "scheduledDate": null, "deadline": null, "areaId": null, "projectId": "p1",
         "headingId": null, "tagIds": [], "index": 1, "todayIndex": 0, "repeating": false,
         "inTrash": false, "createdAt": "2026-01-02T10:01:00Z", "modifiedAt": null,
         "completedAt": null}
      ],
      "tasks": [
        {"id": "t1", "type": 0, "title": "Buy masking tape", "note": "the wide one",
         "status": 0, "schedule": 1, "scheduledDate": "2026-09-15", "deadline": "2026-09-20",
         "areaId": null, "projectId": "p1", "headingId": "h1", "tagIds": ["g1"],
         "index": 12, "todayIndex": 3, "repeating": false, "inTrash": false,
         "createdAt": "2026-09-01T08:00:00Z", "modifiedAt": "2026-09-14T09:00:00Z",
         "completedAt": null},
        {"id": "t2", "type": 0, "title": "Think about the garden", "status": 0, "schedule": 2,
         "scheduledDate": null, "deadline": null, "areaId": "a1", "projectId": null,
         "headingId": null, "tagIds": [], "index": 40, "todayIndex": 0, "repeating": true,
         "inTrash": false, "createdAt": "2026-08-01T08:00:00Z", "modifiedAt": null,
         "completedAt": null}
      ],
      "checklist": [{"id": "c1", "taskId": "t1", "title": "measure first", "status": 0, "index": 0}],
      "views": {"inbox": [], "today": ["t1"], "upcoming": [], "anytime": [], "someday": ["t2"]},
      "projectDone": {"p1": 4}
    }
    """.trimIndent()

    @Test fun `the real shape decodes into the model`() {
        val snapshot = json.decodeFromString<SnapshotDto>(wire).toModel()

        assertEquals("2026-09-15", snapshot.today)
        assertEquals("America/Los_Angeles", snapshot.timezone)
        assertEquals(1, snapshot.areas.size)
        assertEquals(1, snapshot.projects.size)
        assertEquals(1, snapshot.headings.size)
        assertEquals(2, snapshot.tasks.size)
        assertEquals(mapOf("p1" to 4), snapshot.projectDone)

        val withNote = snapshot.tasks.single { it.id == "t1" }
        assertEquals("the wide one", withNote.note)
        assertEquals(ItemType.TASK, withNote.type)
        assertEquals(listOf("g1"), withNote.tagIds)
        assertEquals("2026-09-20", withNote.deadline)
        assertEquals("h1", withNote.headingId)
        // An index from the real mirror is an arbitrary signed integer, not a position.
        assertEquals(-2263, snapshot.projects.single().index)

        val withoutNote = snapshot.tasks.single { it.id == "t2" }
        assertEquals("an omitted note is empty, not null", "", withoutNote.note)
        assertNull("an explicit null stays null", withoutNote.scheduledDate)
        assertEquals(Schedule.SOMEDAY, withoutNote.schedule)
        assertTrue(withoutNote.repeating)
    }

    @Test fun `the model built from it answers the questions the screens ask`() {
        val model = buildModel(json.decodeFromString<SnapshotDto>(wire).toModel())

        assertEquals(listOf("t1"), model.view(ViewId.TODAY).map { it.id })
        assertEquals(listOf("t2"), model.view(ViewId.SOMEDAY).map { it.id })
        assertEquals(listOf("p1"), model.projectsByArea.getValue("a1").map { it.id })
        assertEquals(listOf("h1"), model.headingsByProject.getValue("p1").map { it.id })
        // A to-do under a heading carries both its project and its heading, and the crumb
        // reads project first — checked against what the live server actually sends, which is
        // both fields set rather than the heading alone.
        assertEquals(
            listOf("Repaint the hall", "Prep"),
            model.breadcrumb(model.tasksById.getValue("t1")),
        )
        assertEquals(0.8f, model.projectProgress("p1"), 0.001f) // 4 done, 1 open
        assertEquals(listOf("c1"), model.checklistByTask.getValue("t1").map { it.id })
    }

    @Test fun `a field the server has not invented yet is not fatal`() {
        val future = wire.replace(
            """"today": "2026-09-15",""",
            """"today": "2026-09-15", "eveningFlag": true, "somethingElse": {"a": 1},""",
        )
        assertEquals("2026-09-15", json.decodeFromString<SnapshotDto>(future).today)
    }
}
