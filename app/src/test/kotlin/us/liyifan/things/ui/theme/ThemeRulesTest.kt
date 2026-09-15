package us.liyifan.things.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.SnapSpec
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import us.liyifan.things.data.settings.EinkColorMode

/**
 * The e-ink rules, each of which has exactly one place it is enforced. These tests are that
 * claim written down: a rule with no enforcement point would have to be remembered at every call
 * site, and eventually would not be.
 */
class ThemeRulesTest {

    @Test fun `every animation becomes an instant change`() {
        val eink = ThingsMotion(eink = true)
        assertTrue(eink.spring<Float>() is SnapSpec)
        assertTrue(eink.tween<Float>() is SnapSpec)
        assertTrue(eink.checkSpring() is SnapSpec)
        assertEquals(EnterTransition.None, eink.enter())
        assertEquals(EnterTransition.None, eink.popEnter())
        assertEquals(EnterTransition.None, eink.fadeIn())

        val normal = ThingsMotion(eink = false)
        assertTrue(normal.spring<Float>() !is SnapSpec)
        assertNotEquals(EnterTransition.None, normal.enter())
    }

    @Test fun `dimming is a solid grey rather than an alpha blend`() {
        val faded = LightColors.muted(LightColors.text3, 0.35f)
        assertEquals(0.35f, faded.alpha, 0.001f)

        // On e-paper an alpha blend is a dither pattern that ghosts, so it is a real colour.
        val einked = EinkMonoColors.muted(EinkMonoColors.text3, 0.35f)
        assertEquals(1f, einked.alpha, 0.001f)
        assertEquals(EinkMonoColors.text3, einked)
    }

    @Test fun `the monochrome palette has no colour at all`() {
        val c = EinkMonoColors
        listOf(c.text, c.text2, c.text3, c.line, c.blue, c.today, c.deadline).forEach { colour ->
            assertEquals("everything is ink", colour.red, colour.green, 0.001f)
            assertEquals("everything is ink", colour.green, colour.blue, 0.001f)
        }
        // The text greys stay dark enough for a panel to hold them cleanly.
        assertTrue("text2 is #262626 or darker", c.text2.red <= 0.16f)
        assertTrue("text3 is #333333 or darker", c.text3.red <= 0.21f)
        assertEquals(Color(0xFFFFFFFF), c.bg)
    }

    @Test fun `the colour palette keeps hues but never depends on them`() {
        val c = einkColors(EinkColorMode.COLOR_ACCENTS)
        assertNotEquals(c.today, c.deadline)
        assertEquals("the page is still white", Color(0xFFFFFFFF), c.bg)
        assertEquals("and the text is still black", Color(0xFF000000), c.text)
        assertTrue(c.isEink)
    }

    @Test fun `e-ink trades shadows for borders and gradients for flat fills`() {
        assertEquals(0f, EinkDims.cardElevation.value, 0.001f)
        assertTrue("a border in its place", EinkDims.cardBorder.value >= 1.5f)
        assertEquals(0f, EinkDims.fabElevation.value, 0.001f)
        assertTrue("shadows are for screens that can draw them", NormalDims.cardElevation.value > 0f)
    }

    @Test fun `targets and strokes grow`() {
        assertTrue(EinkDims.rowMinHeight.value > NormalDims.rowMinHeight.value)
        assertTrue(EinkDims.popoverItemHeight.value >= 48f)
        assertTrue(EinkDims.checkboxStroke.value > NormalDims.checkboxStroke.value)
        assertTrue(EinkDims.iconStrokeScale > NormalDims.iconStrokeScale)
    }

    @Test fun `weights step up so thin text does not disappear`() {
        val normal = thingsTypography(eink = false)
        val eink = thingsTypography(eink = true)
        assertTrue(eink.row.fontWeight!!.weight > normal.row.fontWeight!!.weight)
        assertTrue(eink.sub.fontWeight!!.weight > normal.sub.fontWeight!!.weight)
        assertTrue(eink.group.fontWeight!!.weight > normal.group.fontWeight!!.weight)
        // The title is already as heavy as it goes.
        assertEquals(normal.title.fontWeight, eink.title.fontWeight)
    }

    @Test fun `the settle pause is kept on e-ink, where feedback matters more`() {
        assertEquals(1400L, ThingsMotion.SETTLE_MS)
    }
}
