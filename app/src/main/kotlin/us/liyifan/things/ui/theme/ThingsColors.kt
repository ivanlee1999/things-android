package us.liyifan.things.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import us.liyifan.things.data.settings.EinkColorMode

/**
 * The Things palette, measured off the Mac app and carried over from things-web's tokens.css.
 *
 * Four palettes rather than two: light, dark, and the two e-ink ones. E-ink is not a tint of the
 * light theme — every value that leans on a mid grey has to become something an electrophoretic
 * panel can actually hold still, and that is a different set of numbers, not an opacity.
 */
@Immutable
data class ThingsColors(
    val bg: Color,
    val bgCard: Color,
    val bgPopover: Color,
    val bgInput: Color,
    val bgChip: Color,
    val bgChipActive: Color,
    val bgRowSelected: Color,
    val bgHeadingLine: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val textChip: Color,
    val textChipActive: Color,
    val line: Color,
    val lineStrong: Color,
    val checkboxBorder: Color,
    val blue: Color,
    val blueDeep: Color,
    val inbox: Color,
    val today: Color,
    val upcoming: Color,
    val anytime: Color,
    val someday: Color,
    val logbook: Color,
    val trash: Color,
    val area: Color,
    val project: Color,
    val deadline: Color,
    val fabTop: Color,
    val fabBottom: Color,
    val toastBg: Color,
    val toastText: Color,
    val isDark: Boolean,
    val isEink: Boolean,
) {
    /** The FAB is a gradient normally and a flat fill on e-ink, where a gradient dithers. */
    fun fabBrush(): Brush =
        if (isEink) SolidColor(fabBottom) else Brush.verticalGradient(listOf(fabTop, fabBottom))

    /**
     * The only sanctioned way to dim something.
     *
     * Off e-ink it is what it looks like. On e-ink an alpha blend becomes a dither pattern that
     * ghosts, so the answer is a solid grey the panel has a level for.
     */
    fun muted(color: Color, alpha: Float): Color =
        if (isEink) text3 else color.copy(alpha = alpha)
}

val LightColors = ThingsColors(
    bg = Color(0xFFFFFFFF),
    bgCard = Color(0xFFFFFFFF),
    bgPopover = Color(0xFFFFFFFF),
    bgInput = Color(0xFFF2F2F2),
    bgChip = Color(0xFFECECEC),
    bgChipActive = Color(0xFF4A4A4A),
    bgRowSelected = Color(0xFFE8EEF9),
    bgHeadingLine = Color(0xFFD5D5D5),
    text = Color(0xFF1C1C1E),
    text2 = Color(0xFF6E6E73),
    text3 = Color(0xFFA1A1A6),
    textChip = Color(0xFF4D4D4D),
    textChipActive = Color(0xFFFFFFFF),
    line = Color(0xFFE5E5EA),
    lineStrong = Color(0xFFD1D1D6),
    checkboxBorder = Color(0xFFB9B9BE),
    blue = Color(0xFF4A7CF5),
    blueDeep = Color(0xFF2F6DE9),
    inbox = Color(0xFF5B9BF8),
    today = Color(0xFFF9C53C),
    upcoming = Color(0xFFEE6A6A),
    anytime = Color(0xFF34BFC1),
    someday = Color(0xFFC8A36C),
    logbook = Color(0xFF5FC35E),
    trash = Color(0xFFA3A3A8),
    area = Color(0xFF6F7D92),
    project = Color(0xFF4A7CF5),
    deadline = Color(0xFFE5484D),
    fabTop = Color(0xFF5F8DFF),
    fabBottom = Color(0xFF3C6FF0),
    toastBg = Color(0xFF2A2A2D),
    toastText = Color(0xFFFFFFFF),
    isDark = false,
    isEink = false,
)

val DarkColors = LightColors.copy(
    bg = Color(0xFF1C1C1E),
    bgCard = Color(0xFF2A2A2D),
    bgPopover = Color(0xFF2C2C2F),
    bgInput = Color(0xFF303033),
    bgChip = Color(0xFF38383C),
    bgChipActive = Color(0xFFE0E0E4),
    bgRowSelected = Color(0xFF2A3550),
    bgHeadingLine = Color(0xFF3C3C40),
    text = Color(0xFFF2F2F5),
    text2 = Color(0xFF9B9BA3),
    text3 = Color(0xFF6B6B73),
    textChip = Color(0xFFD0D0D6),
    textChipActive = Color(0xFF1C1C1E),
    line = Color(0xFF303034),
    lineStrong = Color(0xFF3F3F45),
    checkboxBorder = Color(0xFF6A6A72),
    blue = Color(0xFF5B8CFF),
    blueDeep = Color(0xFF4A7CF5),
    area = Color(0xFF9AA5B8),
    isDark = true,
)

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)

/**
 * E-ink, monochrome.
 *
 * text2 and text3 are #262626 and #333333 rather than the light theme's greys: those are levels
 * a Carta panel holds cleanly, where #A1A1A6 becomes a dither that smears on the next update.
 * Hierarchy on e-ink is carried by size and weight, with colour barely helping at all.
 *
 * There is no dark e-ink palette. Dark mode on e-paper means driving the whole panel black,
 * which is slow, ghosts badly, and is not what anybody wants to read.
 */
val EinkMonoColors = LightColors.copy(
    bg = White,
    bgCard = White,
    bgPopover = White,
    bgInput = White,
    bgChip = White,
    bgChipActive = Black,
    bgRowSelected = White, // selection is a bar down the side, not a wash
    bgHeadingLine = Black,
    text = Black,
    text2 = Color(0xFF262626),
    text3 = Color(0xFF333333),
    textChip = Black,
    textChipActive = White,
    line = Black,
    lineStrong = Black,
    checkboxBorder = Black,
    blue = Black,
    blueDeep = Black,
    inbox = Black,
    today = Black,
    upcoming = Black,
    anytime = Black,
    someday = Black,
    logbook = Black,
    trash = Black,
    area = Black,
    project = Black,
    deadline = Black,
    fabTop = Black,
    fabBottom = Black,
    toastBg = Black,
    toastText = White,
    isDark = false,
    isEink = true,
)

/**
 * E-ink with the list colours kept, for a Kaleido panel.
 *
 * The colour layer costs resolution and mutes everything, so these are the darker, more saturated
 * end of the palette, and nothing anywhere depends on colour to be understood.
 */
val EinkColorColors = EinkMonoColors.copy(
    blue = Color(0xFF2F6DE9),
    blueDeep = Color(0xFF2F6DE9),
    inbox = Color(0xFF2F6DE9),
    today = Color(0xFFB07A00),
    upcoming = Color(0xFFC0392B),
    anytime = Color(0xFF127C7E),
    someday = Color(0xFF8A6A3C),
    logbook = Color(0xFF2E7D32),
    trash = Color(0xFF555555),
    area = Color(0xFF4A5666),
    project = Color(0xFF2F6DE9),
    deadline = Color(0xFFC0392B),
    fabTop = Color(0xFF2F6DE9),
    fabBottom = Color(0xFF2F6DE9),
)

fun einkColors(mode: EinkColorMode): ThingsColors = when (mode) {
    EinkColorMode.MONOCHROME -> EinkMonoColors
    EinkColorMode.COLOR_ACCENTS -> EinkColorColors
}
