package us.liyifan.things.ui.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import us.liyifan.things.model.DateStr
import us.liyifan.things.model.dayOfMonth
import us.liyifan.things.model.localToday
import us.liyifan.things.model.monthGrid
import us.liyifan.things.model.monthTitle
import us.liyifan.things.model.shiftMonth
import us.liyifan.things.ui.components.IconButton
import us.liyifan.things.ui.icons.Glyph
import us.liyifan.things.ui.icons.glyph
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * A month, Monday first, as Things draws it.
 *
 * Days before [min] are refused rather than faded: a deadline in the past is not a thing the
 * backend will accept, and on e-ink a faded day is indistinguishable from an available one.
 */
@Composable
fun CalendarGrid(
    value: DateStr?,
    onPick: (DateStr) -> Unit,
    modifier: Modifier = Modifier,
    min: DateStr? = null,
) {
    val colors = ThingsTheme.colors
    val dims = ThingsTheme.dims
    val today = remember { localToday() }
    var cursor by rememberSaveable(value) { mutableStateOf(value ?: today) }
    val cells = remember(cursor) { monthGrid(cursor) }

    Column(modifier.padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                icon = glyph(Glyph.Chevron, 12f),
                contentDescription = "Previous month",
                onClick = { cursor = shiftMonth(cursor, -1) },
                iconSize = 12.dp,
                modifier = Modifier.rotate(180f),
            )
            Text(
                monthTitle(cursor),
                style = ThingsTheme.type.sub.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(
                icon = glyph(Glyph.Chevron, 12f),
                contentDescription = "Next month",
                onClick = { cursor = shiftMonth(cursor, 1) },
                iconSize = 12.dp,
            )
        }

        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Box(Modifier.weight(1f).height(18.dp), contentAlignment = Alignment.Center) {
                    Text(d, style = ThingsTheme.type.popoverLabel, color = colors.text3)
                }
            }
        }

        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val blocked = min != null && cell.cursor < min
                    val selected = cell.cursor == value
                    val isToday = cell.cursor == today
                    Box(
                        Modifier
                            .weight(1f)
                            .height(dims.calendarCellHeight)
                            .padding(1.dp)
                            .then(if (selected) Modifier.background(colors.blue, CircleShape) else Modifier)
                            .clickable(enabled = !blocked) { onPick(cell.cursor) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dayOfMonth(cell.cursor).toString(),
                            style = ThingsTheme.type.sub.copy(
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = when {
                                selected -> colors.bg
                                blocked -> colors.text3
                                isToday -> colors.blue
                                !cell.inMonth -> colors.text3
                                else -> colors.text
                            },
                        )
                    }
                }
            }
        }
    }
}
