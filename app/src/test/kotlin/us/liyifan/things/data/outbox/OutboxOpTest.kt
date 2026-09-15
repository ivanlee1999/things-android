package us.liyifan.things.data.outbox

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import us.liyifan.things.model.EditFields

/**
 * A queued write has to survive being written to disk and read back, and has to be rewritable
 * when a provisional id turns real. Both are exercised for every op, because an op that forgets
 * one of its id fields fails silently — the write goes out naming an id the server has never
 * heard of, and comes back 400 long after the mistake was made.
 */
class OutboxOpTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun roundTrip(op: OutboxOp): OutboxOp =
        json.decodeFromString(OutboxOp.serializer(), json.encodeToString(OutboxOp.serializer(), op))

    private val all = listOf(
        OutboxOp.CreateTask("tmp-1", "Buy milk", whenValue = "today", project = "tmp-p", heading = "tmp-h", area = "tmp-a", tags = listOf("t1")),
        OutboxOp.EditTask(EditFields(uuid = "tmp-1", title = "Buy oat milk", project = "tmp-p", area = "none")),
        OutboxOp.TaskActionOp("COMPLETE", "tmp-1"),
        OutboxOp.MoveTask("tmp-1", "today"),
        OutboxOp.CreateChecklistItem("tmp-c", "tmp-1", "the fridge one"),
        OutboxOp.ChecklistActionOp("DELETE", "tmp-c"),
        OutboxOp.CreateProject("tmp-p", "Garden", area = "tmp-a"),
        OutboxOp.CreateHeading("tmp-h", "Weeding", "tmp-p"),
        OutboxOp.CreateArea("tmp-a", "Home"),
        OutboxOp.EditArea("tmp-a", "House"),
        OutboxOp.DeleteArea("tmp-a"),
        OutboxOp.CreateTag("tmp-t", "errand"),
    )

    @Test fun `every op survives a round trip through JSON`() {
        all.forEach { op -> assertEquals(op, roundTrip(op)) }
    }

    @Test fun `rewriting replaces every referenced id`() {
        val map = mapOf(
            "tmp-1" to "T1", "tmp-p" to "P", "tmp-h" to "H",
            "tmp-a" to "A", "tmp-c" to "C", "tmp-t" to "TG",
        )
        all.forEach { op ->
            val left = op.rewriteIds(map).referencedIds().filter { it.startsWith("tmp-") }
            // A create's own temp id is resolved by the processor when the server answers, so it
            // is the one id that legitimately survives a rewrite.
            assertEquals("$op left $left unrewritten", listOfNotNull(op.mintsFor), left)
        }
    }

    @Test fun `none is a keyword, not an id to rewrite`() {
        val op = OutboxOp.EditTask(EditFields(uuid = "u", area = "none", project = "none"))
        assertTrue("none" !in op.referencedIds())
        // A naive map lookup would rewrite it, and clearing a field would instead move the to-do
        // into whatever "none" happened to map to.
        val rewritten = op.rewriteIds(mapOf("none" to "WRONG")) as OutboxOp.EditTask
        assertEquals("none", rewritten.fields.area)
    }

    @Test fun `a create names the row it will mint`() {
        assertEquals("tmp-1", OutboxOp.CreateTask("tmp-1", "x").mintsFor)
        assertEquals("tmp-p", OutboxOp.CreateProject("tmp-p", "x").mintsFor)
        assertEquals(null, OutboxOp.MoveTask("u", "today").mintsFor)
    }

    @Test fun `every op can describe itself for a toast`() {
        all.forEach { op -> assertTrue(op.describe.isNotBlank()) }
    }
}
