package us.liyifan.things.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * When the app last heard from the server, and whether it is holding something back.
 *
 * "Updated — tap to refresh" is the visible half of the quiet-screen rule: a snapshot that
 * arrived while the list was being read is not drawn until the reader asks for it, and this is
 * how they find out there is something to ask for.
 */
@Composable
fun SyncStatusLine(
    label: String,
    stagedAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThingsTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (stagedAvailable) "Updated — tap to refresh" else label,
            style = ThingsTheme.type.toast,
            color = if (stagedAvailable) colors.blue else colors.text3,
            textAlign = TextAlign.Center,
        )
    }
}

/** A message that says itself once and goes away. */
@Composable
fun ThingsToast(message: String, modifier: Modifier = Modifier) {
    val colors = ThingsTheme.colors
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = ThingsTheme.type.toast,
            color = colors.toastText,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .background(colors.toastBg, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}
