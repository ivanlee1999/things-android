package us.liyifan.things.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * A raised surface: the editing card, a sheet, a menu, the button.
 *
 * The one rule this enforces is that e-ink gets a border where everything else gets a shadow. A
 * shadow is a soft alpha gradient, which on e-paper is a dither pattern that ghosts and reads as
 * dirt; a hairline of real black says "this is a separate surface" in one refresh. Nothing draws
 * its own shadow, so the rule cannot be forgotten in a corner of the app.
 */
@Composable
fun Modifier.thingsSurface(
    shape: Shape,
    background: Color = ThingsTheme.colors.bgCard,
    elevation: androidx.compose.ui.unit.Dp = ThingsTheme.dims.cardElevation,
    borderColor: Color = ThingsTheme.colors.line,
): Modifier {
    val dims = ThingsTheme.dims
    val base = if (ThingsTheme.colors.isEink) {
        this.border(dims.cardBorder, borderColor, shape)
    } else {
        this.shadow(elevation, shape, clip = false)
    }
    return base.background(background, shape)
}
