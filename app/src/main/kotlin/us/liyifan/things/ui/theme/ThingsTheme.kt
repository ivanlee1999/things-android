package us.liyifan.things.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import us.liyifan.things.data.settings.Appearance
import us.liyifan.things.data.settings.EinkConfig
import us.liyifan.things.data.settings.ThemeMode

val LocalThingsColors = staticCompositionLocalOf { LightColors }
val LocalThingsTypography = staticCompositionLocalOf { thingsTypography(eink = false) }
val LocalThingsDims = staticCompositionLocalOf { NormalDims }
val LocalThingsMotion = staticCompositionLocalOf { ThingsMotion(eink = false) }
val LocalEink = staticCompositionLocalOf { EinkConfig() }

/**
 * The app's look.
 *
 * Material 3 is underneath only because three of its components are worth having — the bottom
 * sheet, the switch, the dialog — and they read the MaterialTheme colours. Everything else is
 * drawn from the tokens here, because Things does not look like Material and pretending
 * otherwise would mean fighting a design system on every screen.
 */
@Composable
fun ThingsTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val eink = appearance.eink
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // E-ink ignores dark mode: driving a whole e-paper panel black is slow, ghosts badly, and
    // is nobody's idea of a readable page.
    val colors = when {
        eink.enabled -> einkColors(eink.colorMode)
        dark -> DarkColors
        else -> LightColors
    }
    val typography = remember(eink.enabled) { thingsTypography(eink.enabled) }
    val dims = if (eink.enabled) EinkDims else NormalDims
    val motion = remember(eink.enabled) { ThingsMotion(eink.enabled) }
    val indication = if (eink.enabled) remember(colors.text) { EinkPressIndication(colors.text) } else ripple()

    CompositionLocalProvider(
        LocalThingsColors provides colors,
        LocalThingsTypography provides typography,
        LocalThingsDims provides dims,
        LocalThingsMotion provides motion,
        LocalEink provides eink,
        LocalIndication provides indication,
    ) {
        MaterialTheme(
            colorScheme = if (colors.isDark) {
                darkColorScheme(
                    primary = colors.blue,
                    background = colors.bg,
                    surface = colors.bgPopover,
                    onSurface = colors.text,
                    surfaceContainerLow = colors.bgPopover,
                    surfaceContainerHigh = colors.bgPopover,
                )
            } else {
                lightColorScheme(
                    primary = colors.blue,
                    background = colors.bg,
                    surface = colors.bgPopover,
                    onSurface = colors.text,
                    surfaceContainerLow = colors.bgPopover,
                    surfaceContainerHigh = colors.bgPopover,
                )
            },
            typography = Typography(
                bodyLarge = typography.row,
                bodyMedium = typography.sub,
                labelLarge = typography.button,
            ),
            content = content,
        )
    }
}

/** The tokens, as one short name at every call site. */
object ThingsTheme {
    val colors: ThingsColors
        @Composable @ReadOnlyComposable get() = LocalThingsColors.current

    val type: ThingsTypography
        @Composable @ReadOnlyComposable get() = LocalThingsTypography.current

    val dims: ThingsDims
        @Composable @ReadOnlyComposable get() = LocalThingsDims.current

    val motion: ThingsMotion
        @Composable @ReadOnlyComposable get() = LocalThingsMotion.current

    val eink: EinkConfig
        @Composable @ReadOnlyComposable get() = LocalEink.current
}

/** Text in a given style and colour, without repeating the pair at every call. */
@Composable
fun textStyle(style: TextStyle, color: androidx.compose.ui.graphics.Color): TextStyle =
    style.copy(color = color)
