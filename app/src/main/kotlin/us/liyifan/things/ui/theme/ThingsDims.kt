package us.liyifan.things.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sizes. The normal set is the web client's phone layout; the e-ink set is the same layout with
 * everything a finger touches made bigger and every line made thicker.
 *
 * Bigger targets are not about accuracy. A mis-tap on e-paper costs two full screen redraws to
 * undo, which is a second of the display flashing, so the cheapest fix is to miss less often.
 */
@Immutable
data class ThingsDims(
    val rowMinHeight: Dp,
    val homeRowHeight: Dp,
    val rowPadVertical: Dp,
    val rowPadHorizontal: Dp,
    val checkbox: Dp,
    val checkboxStroke: Dp,
    val hairline: Dp,
    val cardRadius: Dp,
    val cardElevation: Dp,
    val cardBorder: Dp,
    val sheetRadius: Dp,
    val sheetBorder: Dp,
    val chipHeight: Dp,
    val iconButton: Dp,
    val popoverItemHeight: Dp,
    val calendarCellWidth: Dp,
    val calendarCellHeight: Dp,
    val fab: Dp,
    val fabElevation: Dp,
    val selectionBar: Dp,
    val pageHorizontal: Dp,
    val iconStrokeScale: Float,
)

val NormalDims = ThingsDims(
    rowMinHeight = 44.dp,
    homeRowHeight = 46.dp,
    rowPadVertical = 8.dp,
    rowPadHorizontal = 4.dp,
    checkbox = 22.dp,
    checkboxStroke = 1.5.dp,
    hairline = 1.dp,
    cardRadius = 16.dp,
    cardElevation = 10.dp,
    cardBorder = 0.dp,
    sheetRadius = 18.dp,
    sheetBorder = 0.dp,
    chipHeight = 21.dp,
    iconButton = 34.dp,
    popoverItemHeight = 40.dp,
    calendarCellWidth = 36.dp,
    calendarCellHeight = 34.dp,
    fab = 58.dp,
    fabElevation = 8.dp,
    selectionBar = 0.dp,
    pageHorizontal = 16.dp,
    iconStrokeScale = 1f,
)

val EinkDims = NormalDims.copy(
    rowMinHeight = 54.dp,
    homeRowHeight = 54.dp,
    rowPadVertical = 10.dp,
    checkbox = 24.dp,
    checkboxStroke = 2.dp,
    cardRadius = 12.dp,
    cardElevation = 0.dp,
    cardBorder = 2.dp,
    sheetRadius = 12.dp,
    sheetBorder = 2.dp,
    chipHeight = 26.dp,
    iconButton = 44.dp,
    popoverItemHeight = 48.dp,
    calendarCellWidth = 44.dp,
    calendarCellHeight = 44.dp,
    fabElevation = 0.dp,
    selectionBar = 3.dp,
    iconStrokeScale = 1.3f,
)
