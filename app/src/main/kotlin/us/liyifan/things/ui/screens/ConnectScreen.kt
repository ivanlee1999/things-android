package us.liyifan.things.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import us.liyifan.things.data.settings.ConnectionConfig
import us.liyifan.things.ui.components.PageHeader
import us.liyifan.things.ui.theme.ThingsTheme

/**
 * First run. The app has no account of its own and nothing to offer until it knows where the
 * server is, so this is the whole screen rather than a banner over an empty list.
 */
@Composable
fun ConnectScreen(
    config: ConnectionConfig,
    onConfigChange: (ConnectionConfig) -> Unit,
    status: ConnectionStatus,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = ThingsTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ThingsTheme.dims.pageHorizontal),
    ) {
        PageHeader(title = "Connect")
        Text(
            "This app reads and writes your Things account through your own things-cloud server. " +
                "Point it at that server and give it the API key.",
            style = ThingsTheme.type.sub,
            color = colors.text2,
            modifier = Modifier.padding(bottom = 18.dp),
        )
        ConnectionForm(
            config = config,
            onChange = onConfigChange,
            status = status,
            onTest = onTest,
            onSave = onSave,
        )
    }
}
