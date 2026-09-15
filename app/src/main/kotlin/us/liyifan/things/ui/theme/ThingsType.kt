package us.liyifan.things.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import us.liyifan.things.R

/**
 * The iPhone type scale from things-web's phone block, in Inter.
 *
 * Inter is bundled rather than downloaded: a BOOX may have no Play Services at all, and a font
 * that sometimes fails to arrive would make the whole app sometimes look wrong.
 *
 * E-ink raises every weight one step. A 400 at this size is a grey smear on a panel that has no
 * subpixels to make it with; 500 is where the same text becomes solid.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

@Immutable
data class ThingsTypography(
    val title: TextStyle,
    val row: TextStyle,
    val rowDone: TextStyle,
    val sub: TextStyle,
    val group: TextStyle,
    val dayNum: TextStyle,
    val dayName: TextStyle,
    val note: TextStyle,
    val chip: TextStyle,
    val homeRow: TextStyle,
    val homeArea: TextStyle,
    val nav: TextStyle,
    val popoverItem: TextStyle,
    val popoverLabel: TextStyle,
    val button: TextStyle,
    val toast: TextStyle,
    val empty: TextStyle,
)

private fun weight(base: FontWeight, eink: Boolean): FontWeight = if (!eink) base else when (base) {
    FontWeight.Normal -> FontWeight.Medium
    FontWeight.Medium -> FontWeight.SemiBold
    FontWeight.SemiBold -> FontWeight.Bold
    else -> base
}

fun thingsTypography(eink: Boolean): ThingsTypography {
    fun style(
        size: Int,
        lineHeight: Int,
        w: FontWeight = FontWeight.Normal,
        letterSpacing: Float = 0f,
        decoration: TextDecoration? = null,
    ) = TextStyle(
        fontFamily = Inter,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight(w, eink),
        letterSpacing = letterSpacing.sp,
        textDecoration = decoration,
    )

    return ThingsTypography(
        title = style(34, 41, FontWeight.Bold, letterSpacing = -0.3f),
        row = style(17, 22),
        rowDone = style(17, 22, decoration = TextDecoration.LineThrough),
        sub = style(13, 16),
        group = style(15, 19, FontWeight.SemiBold),
        dayNum = style(22, 26, FontWeight.Bold),
        dayName = style(17, 21, FontWeight.Medium),
        note = style(16, 21),
        chip = style(12, 15, FontWeight.Medium),
        homeRow = style(17, 22, FontWeight.Medium),
        homeArea = style(17, 22, FontWeight.SemiBold),
        nav = style(17, 22),
        popoverItem = style(16, 20),
        popoverLabel = style(11, 14, FontWeight.SemiBold, letterSpacing = 0.4f),
        button = style(17, 22, FontWeight.SemiBold),
        toast = style(13, 17),
        empty = style(14, 18),
    )
}
