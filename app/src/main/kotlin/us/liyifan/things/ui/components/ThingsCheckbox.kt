package us.liyifan.things.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import us.liyifan.things.ui.icons.Glyph
import us.liyifan.things.ui.icons.glyph
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * Things' checkbox: a rounded square that fills blue and draws a tick, a circle for a project,
 * and a cross for something cancelled.
 *
 * The tick springs in off e-ink and simply appears on it. What does not change is that the box
 * is drawn filled the instant it is tapped — the row itself waits 1.4 seconds before leaving,
 * which is the confirmation, not the animation.
 */
@Composable
fun ThingsCheckbox(
    checked: Boolean,
    onChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = ThingsTheme.dims.checkbox,
    round: Boolean = false,
    cancelled: Boolean = false,
) {
    val colors = ThingsTheme.colors
    val dims = ThingsTheme.dims
    val motion = ThingsTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed && onChange != null) 0.86f else 1f,
        animationSpec = motion.spring(),
        label = "checkboxPress",
    )
    val tick by animateFloatAsState(
        targetValue = if (checked) 1f else 0.4f,
        animationSpec = motion.checkSpring(),
        label = "checkboxTick",
    )
    val shape = if (round) CircleShape else RoundedCornerShape(percent = 22)
    val fill = if (checked) colors.blue else Color.Transparent
    val border = if (checked) colors.blue else colors.checkboxBorder

    Box(
        modifier
            .size(size)
            .scale(press)
            .border(dims.checkboxStroke, border, shape)
            .background(fill, shape)
            .then(
                if (onChange == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Checkbox,
                        onClick = { onChange(!checked) },
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = glyph(if (cancelled) Glyph.X else Glyph.Checkmark, size.value * 0.62f),
                contentDescription = null,
                tint = colors.bg,
                modifier = Modifier.scale(tick),
            )
        }
    }
}
