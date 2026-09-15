package us.liyifan.things.sync

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quiet-screen rule, which is the difference between an e-ink app that can be read and one
 * that rearranges itself while you are looking at it.
 */
class UiActivityTest {

    @After fun reset() {
        UiActivity.setEditorOpen(false)
        UiActivity.touched()
    }

    @Test fun `a screen just touched may be redrawn`() {
        UiActivity.touched()
        assertTrue(UiActivity.readyToDraw())
    }

    @Test fun `a screen that has been still is left alone`() {
        UiActivity.touched()
        val later = System.currentTimeMillis() + UiActivity.QUIET_AFTER_MS + 1
        assertFalse("the reader is mid-page", UiActivity.readyToDraw(now = later))
    }

    @Test fun `an open card is never redrawn under, however recent the touch`() {
        UiActivity.setEditorOpen(true)
        UiActivity.touched()
        assertFalse("a snapshot landing mid-edit would replace a half-typed title", UiActivity.readyToDraw())
        UiActivity.setEditorOpen(false)
        assertTrue(UiActivity.readyToDraw())
    }

    @Test fun `navigating counts as a touch`() {
        val stale = System.currentTimeMillis() + UiActivity.QUIET_AFTER_MS + 1
        assertFalse(UiActivity.readyToDraw(now = stale))
        UiActivity.onNavigate()
        assertTrue(UiActivity.readyToDraw())
    }
}
