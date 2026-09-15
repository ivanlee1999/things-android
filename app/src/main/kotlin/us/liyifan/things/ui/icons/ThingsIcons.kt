package us.liyifan.things.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * The Things icon set, redrawn from the web client's SVGs.
 *
 * Built in code rather than as vector drawables for two reasons: six of these carry a list
 * colour that changes with the palette (black in monochrome e-ink, the Things hue otherwise),
 * and every outline icon is drawn a third thicker on e-paper, where a 1.3 px stroke disappears.
 * Both are parameters here and would be a fork of every file as XML.
 */

private class VectorSpec(
    val name: String,
    val viewport: Float,
    val paths: List<VectorPath>,
)

private class VectorPath(
    val data: String,
    val fill: Color? = null,
    val stroke: Color? = null,
    val strokeWidth: Float = 0f,
    val cap: StrokeCap = StrokeCap.Butt,
    val join: StrokeJoin = StrokeJoin.Miter,
    val alpha: Float = 1f,
)

private fun build(spec: VectorSpec, size: Float, strokeScale: Float): ImageVector {
    val builder = ImageVector.Builder(
        name = spec.name,
        defaultWidth = size.dp,
        defaultHeight = size.dp,
        viewportWidth = spec.viewport,
        viewportHeight = spec.viewport,
    )
    spec.paths.forEach { p ->
        builder.addPath(
            pathData = PathParser().parsePathString(p.data).toNodes(),
            fill = p.fill?.let { SolidColor(it) },
            fillAlpha = p.alpha,
            stroke = p.stroke?.let { SolidColor(it) },
            strokeAlpha = p.alpha,
            strokeLineWidth = p.strokeWidth * strokeScale,
            strokeLineCap = p.cap,
            strokeLineJoin = p.join,
        )
    }
    return builder.build()
}

/** A rectangle as path data, since the source SVGs use <rect>. */
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 0f): String =
    if (r <= 0f) {
        "M$x ${y}h${w}v${h}h${-w}z"
    } else {
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} " +
            "${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"
    }

enum class ThingsIcon { Inbox, Today, Upcoming, Anytime, Someday, Logbook, Trash, Area }

/**
 * The seven list icons and the area cube: the only icons with colours of their own, which is why
 * they take the palette rather than `currentColor`.
 */
@Composable
fun listIcon(kind: ThingsIcon, size: Float = 20f): ImageVector {
    val c = ThingsTheme.colors
    val scale = ThingsTheme.dims.iconStrokeScale
    val white = if (c.isEink) c.bg else Color.White
    return remember(kind, size, c, scale) {
        val spec = when (kind) {
            ThingsIcon.Inbox -> VectorSpec("Inbox", 20f, listOf(
                VectorPath(
                    "M3.5 4.5h13a1 1 0 0 1 1 1V11l-2.2 4.2a1 1 0 0 1-.9.55H5.6a1 1 0 0 1-.9-.55L2.5 11V5.5a1 1 0 0 1 1-1Z",
                    fill = c.inbox,
                ),
                VectorPath(
                    "M2.5 11h4.2l.9 1.7a1 1 0 0 0 .9.55h3a1 1 0 0 0 .9-.55l.9-1.7h4.2",
                    stroke = white, strokeWidth = 1.3f, join = StrokeJoin.Round,
                ),
            ))
            ThingsIcon.Today -> VectorSpec("Today", 20f, listOf(
                VectorPath(STAR, fill = c.today),
            ))
            ThingsIcon.Upcoming -> VectorSpec("Upcoming", 20f, listOf(
                VectorPath(rect(2.5f, 3.5f, 15f, 14f, 2.6f), fill = c.upcoming),
                VectorPath(rect(5f, 9.5f, 3f, 3f, 0.6f), fill = white),
                VectorPath(rect(8.5f, 9.5f, 3f, 3f, 0.6f), fill = white),
                VectorPath(rect(12f, 9.5f, 3f, 3f, 0.6f), fill = white),
                VectorPath(rect(5f, 13.2f, 3f, 2.4f, 0.6f), fill = white),
                VectorPath(rect(8.5f, 13.2f, 3f, 2.4f, 0.6f), fill = white),
            ))
            ThingsIcon.Anytime -> VectorSpec("Anytime", 20f, listOf(
                VectorPath("M10 3 2.8 7 10 11l7.2-4L10 3Z", fill = c.anytime),
                VectorPath("M2.8 10.2 10 14.2l7.2-4", stroke = c.anytime, strokeWidth = 2f,
                    cap = StrokeCap.Round, join = StrokeJoin.Round),
                VectorPath("M2.8 13.4 10 17.4l7.2-4", stroke = c.anytime, strokeWidth = 2f,
                    cap = StrokeCap.Round, join = StrokeJoin.Round, alpha = if (c.isEink) 1f else 0.55f),
            ))
            ThingsIcon.Someday -> VectorSpec("Someday", 20f, listOf(
                VectorPath(rect(2.5f, 3.5f, 15f, 4f, 1.2f), fill = c.someday),
                VectorPath("M3.8 8h12.4v7.3a1.7 1.7 0 0 1-1.7 1.7H5.5a1.7 1.7 0 0 1-1.7-1.7V8Z",
                    fill = c.someday, alpha = if (c.isEink) 1f else 0.85f),
                VectorPath(rect(7.6f, 10f, 4.8f, 1.6f, 0.8f), fill = white),
            ))
            ThingsIcon.Logbook -> VectorSpec("Logbook", 20f, listOf(
                VectorPath("M4 3.5h10.5A1.5 1.5 0 0 1 16 5v10.5a1.5 1.5 0 0 1-1.5 1.5H4V3.5Z", fill = c.logbook),
                VectorPath("M7.5 9.5 9.3 11.3 13 7.6", stroke = white, strokeWidth = 1.6f,
                    cap = StrokeCap.Round, join = StrokeJoin.Round),
            ))
            ThingsIcon.Trash -> VectorSpec("Trash", 20f, listOf(
                VectorPath("M4 5.5h12", stroke = c.trash, strokeWidth = 1.8f, cap = StrokeCap.Round),
                VectorPath("M8 3.5h4", stroke = c.trash, strokeWidth = 1.8f, cap = StrokeCap.Round),
                VectorPath("M5.3 7h9.4l-.7 8.6a1.5 1.5 0 0 1-1.5 1.4H7.5A1.5 1.5 0 0 1 6 15.6L5.3 7Z", fill = c.trash),
            ))
            ThingsIcon.Area -> VectorSpec("Area", 20f, listOf(
                VectorPath("M10 2.6 3 6.3v7.4l7 3.7 7-3.7V6.3l-7-3.7Z", stroke = c.area,
                    strokeWidth = 1.6f, join = StrokeJoin.Round),
                VectorPath("M3 6.3 10 10l7-3.7M10 10v7.4", stroke = c.area, strokeWidth = 1.6f,
                    join = StrokeJoin.Round),
            ))
        }
        build(spec, size, scale)
    }
}

private const val STAR =
    "M10 2.4l2.35 4.9 5.35.7-3.9 3.75.98 5.35L10 14.5l-4.78 2.6.98-5.35L2.3 8l5.35-.7L10 2.4Z"

enum class Glyph { Checkmark, Flag, Calendar, Tag, Checklist, Note, Repeat, Move, Plus, Search, Chevron, Heading, Ellipsis, Star, X }

/** The monochrome glyphs, which take whatever colour they are drawn in. */
@Composable
fun glyph(kind: Glyph, size: Float = 14f): ImageVector {
    val scale = ThingsTheme.dims.iconStrokeScale
    val ink = Color.Black // tinted at the draw site; the vector only carries shape
    return remember(kind, size, scale) {
        val spec = when (kind) {
            Glyph.Checkmark -> VectorSpec("Checkmark", 12f, listOf(
                VectorPath("M2.2 6.3 4.8 8.8 9.8 3.4", stroke = ink, strokeWidth = 1.8f,
                    cap = StrokeCap.Round, join = StrokeJoin.Round),
            ))
            Glyph.Flag -> VectorSpec("Flag", 12f, listOf(
                VectorPath("M2 1.5v9.5", stroke = ink, strokeWidth = 1.4f, cap = StrokeCap.Round),
                VectorPath("M2.5 2h6.6L7.4 4.4l1.7 2.4H2.5V2Z", fill = ink),
            ))
            Glyph.Calendar -> VectorSpec("Calendar", 14f, listOf(
                VectorPath(rect(1.5f, 2.5f, 11f, 10f, 2f), stroke = ink, strokeWidth = 1.4f),
                VectorPath("M1.5 5.5h11M4.5 1.5v2M9.5 1.5v2", stroke = ink, strokeWidth = 1.4f, cap = StrokeCap.Round),
            ))
            Glyph.Tag -> VectorSpec("Tag", 14f, listOf(
                VectorPath(
                    "M1.5 2.5v4.2c0 .4.15.75.42 1L7.3 13.1a1.4 1.4 0 0 0 2 0l3.8-3.8a1.4 1.4 0 0 0 0-2L7.7 1.92A1.4 1.4 0 0 0 6.7 1.5H2.5a1 1 0 0 0-1 1Z",
                    stroke = ink, strokeWidth = 1.4f,
                ),
                VectorPath("M5.6 4.6a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z", fill = ink),
            ))
            Glyph.Checklist -> VectorSpec("Checklist", 14f, listOf(
                VectorPath("M1.8 3.5 2.9 4.6 5 2.5M1.8 8 2.9 9.1 5 7M1.8 12.3l1.1 1.1L5 11.3",
                    stroke = ink, strokeWidth = 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                VectorPath("M7 3.5h5.5M7 8h5.5M7 12.5h5.5", stroke = ink, strokeWidth = 1.4f, cap = StrokeCap.Round),
            ))
            Glyph.Note -> VectorSpec("Note", 12f, listOf(
                VectorPath("M2.5 3h7M2.5 6h7M2.5 9h4.5", stroke = ink, strokeWidth = 1.4f, cap = StrokeCap.Round),
            ))
            Glyph.Repeat -> VectorSpec("Repeat", 12f, listOf(
                VectorPath("M2 5.2a4 4 0 0 1 6.9-2.3M10 6.8a4 4 0 0 1-6.9 2.3", stroke = ink,
                    strokeWidth = 1.3f, cap = StrokeCap.Round),
                VectorPath("M9 1.3v2.4H6.6M3 10.7V8.3h2.4", stroke = ink, strokeWidth = 1.3f,
                    cap = StrokeCap.Round, join = StrokeJoin.Round),
            ))
            Glyph.Move -> VectorSpec("Move", 14f, listOf(
                VectorPath("M2 4.5h6.5M2 7h10M2 9.5h6.5", stroke = ink, strokeWidth = 1.4f, cap = StrokeCap.Round),
                VectorPath("M10 2.5 12.5 4.5 10 6.5", stroke = ink, strokeWidth = 1.4f,
                    cap = StrokeCap.Round, join = StrokeJoin.Round),
            ))
            Glyph.Plus -> VectorSpec("Plus", 16f, listOf(
                VectorPath("M8 2.5v11M2.5 8h11", stroke = ink, strokeWidth = 2f, cap = StrokeCap.Round),
            ))
            Glyph.Search -> VectorSpec("Search", 13f, listOf(
                VectorPath("M9.5 5.5a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z", stroke = ink, strokeWidth = 1.5f),
                VectorPath("m8.6 8.6 3 3", stroke = ink, strokeWidth = 1.5f, cap = StrokeCap.Round),
            ))
            Glyph.Chevron -> VectorSpec("Chevron", 12f, listOf(
                VectorPath("m4 2 4 4-4 4", stroke = ink, strokeWidth = 1.6f,
                    cap = StrokeCap.Round, join = StrokeJoin.Round),
            ))
            Glyph.Heading -> VectorSpec("Heading", 14f, listOf(
                VectorPath("M2 2.5v9M9 2.5v9M2 7h7", stroke = ink, strokeWidth = 1.6f, cap = StrokeCap.Round),
                VectorPath("M11 9.5v2.5", stroke = ink, strokeWidth = 1.6f, cap = StrokeCap.Round),
            ))
            Glyph.Ellipsis -> VectorSpec("Ellipsis", 14f, listOf(
                VectorPath("M4.3 7a1.3 1.3 0 1 1-2.6 0 1.3 1.3 0 0 1 2.6 0Z", fill = ink),
                VectorPath("M8.3 7a1.3 1.3 0 1 1-2.6 0 1.3 1.3 0 0 1 2.6 0Z", fill = ink),
                VectorPath("M12.3 7a1.3 1.3 0 1 1-2.6 0 1.3 1.3 0 0 1 2.6 0Z", fill = ink),
            ))
            Glyph.Star -> VectorSpec("Star", 20f, listOf(VectorPath(STAR, fill = ink)))
            Glyph.X -> VectorSpec("X", 10f, listOf(
                VectorPath("m2 2 6 6M8 2 2 8", stroke = ink, strokeWidth = 1.5f, cap = StrokeCap.Round),
            ))
        }
        build(spec, size, scale)
    }
}
