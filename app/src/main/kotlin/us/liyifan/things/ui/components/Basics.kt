package us.liyifan.things.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import us.liyifan.things.ui.icons.Glyph
import us.liyifan.things.ui.icons.glyph
import us.liyifan.things.ui.theme.ThingsTheme

/** A plain text action, the blue verb Things uses instead of a button. */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = ThingsTheme.colors.blue,
) {
    Text(
        text = text,
        style = ThingsTheme.type.sub,
        color = color,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** An icon that can be tapped, sized so a finger finds it. */
@Composable
fun IconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ThingsTheme.colors.text3,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier
            .size(ThingsTheme.dims.iconButton)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** What a list says when it has nothing in it. */
@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val colors = ThingsTheme.colors
    Column(
        modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            // Off e-ink the icon is faded back; on it, alpha is a dither, so it is simply grey.
            tint = colors.muted(colors.text3, 0.35f),
            modifier = Modifier.size(56.dp),
        )
        Text(text, style = ThingsTheme.type.empty, color = colors.text3, textAlign = TextAlign.Center)
    }
}

/** The blue button that makes a new to-do. */
@Composable
fun ThingsFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ThingsTheme.colors
    val dims = ThingsTheme.dims
    Box(
        modifier
            .size(dims.fab)
            .then(
                if (colors.isEink) {
                    // No shadow and no gradient; a ring keeps it separate from whatever is under it.
                    Modifier.border(2.dp, colors.bg, CircleShape)
                } else {
                    Modifier.shadow(dims.fabElevation, CircleShape, spotColor = colors.blue)
                },
            )
            .background(colors.fabBrush(), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = glyph(Glyph.Plus, 24f),
            contentDescription = "New To-Do",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** A section header above a group of rows. */
@Composable
fun GroupHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    underline: Boolean = true,
) {
    val colors = ThingsTheme.colors
    Column(modifier.fillMaxWidth().padding(top = 24.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = ThingsTheme.type.group, color = colors.text, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        if (underline) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
                    .height(ThingsTheme.dims.hairline)
                    .background(colors.bgHeadingLine),
            )
        }
    }
}

/** Upcoming's day header: the date large, the weekday beside it. */
@Composable
fun DayHeader(day: Int, name: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(day.toString(), style = ThingsTheme.type.dayNum, color = ThingsTheme.colors.text)
        Text(
            name,
            style = ThingsTheme.type.dayName,
            color = ThingsTheme.colors.text2,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

/** A hairline between rows. */
@Composable
fun RowDivider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Spacer(
        modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(ThingsTheme.dims.hairline)
            .background(ThingsTheme.colors.line),
    )
}
