package us.liyifan.things.ui.icons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * A project's progress, drawn as Things draws it: a thin ring with a solid wedge growing inside.
 *
 * The wedge is an arc, not a pie slice, and the trick is the same one the web client uses — an
 * arc of radius R/2 stroked R wide fills exactly the disc of radius R. Stroking a radius-R arc
 * at width 2R instead reaches past the bounds and clips square at the corners.
 */
@Composable
fun ProjectPie(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 17.dp,
    color: Color = ThingsTheme.colors.project,
) {
    val motion = ThingsTheme.motion
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = motion.value(450),
        label = "projectProgress",
    )
    val strokeScale = ThingsTheme.dims.iconStrokeScale
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 20f
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        val ringRadius = 7.6f * unit
        val ringWidth = 1.6f * unit * strokeScale
        drawCircle(color = color, radius = ringRadius, center = centre, style = Stroke(width = ringWidth))

        val r = 5.4f * unit
        if (animated >= 0.999f) {
            drawCircle(color = color, radius = r, center = centre)
        } else if (animated > 0f) {
            val arcRadius = r / 2f
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(centre.x - arcRadius, centre.y - arcRadius),
                size = Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(width = r),
            )
        }
    }
}
