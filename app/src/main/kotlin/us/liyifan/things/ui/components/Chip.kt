package us.liyifan.things.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import us.liyifan.things.model.Tag
import us.liyifan.things.ui.theme.ThingsTheme

/** A tag pill. Filled dark when it is the active filter, as in Things' own tag bar. */
@Composable
fun Chip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = ThingsTheme.colors
    val dims = ThingsTheme.dims
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .defaultMinSize(minHeight = dims.chipHeight)
            .then(
                // On e-ink a pale grey fill is indistinguishable from the page, so an inactive
                // chip is drawn as an outline instead.
                if (colors.isEink && !active) Modifier.border(1.5.dp, colors.line, shape)
                else Modifier.background(if (active) colors.bgChipActive else colors.bgChip, shape)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ThingsTheme.type.chip,
            color = if (active) colors.textChipActive else colors.textChip,
        )
    }
}

/** The filter bar above a list: "All", then every tag actually present on its rows. */
@Composable
fun TagBar(
    tags: List<Tag>,
    active: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        item {
            Chip(label = "All", active = active == null, onClick = { onChange(null) })
        }
        items(tags, key = { it.id }) { tag ->
            Chip(
                label = tag.title,
                active = active == tag.id,
                onClick = { onChange(if (active == tag.id) null else tag.id) },
            )
        }
    }
}
