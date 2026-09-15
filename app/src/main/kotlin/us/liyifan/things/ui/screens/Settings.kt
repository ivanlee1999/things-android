package us.liyifan.things.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import us.liyifan.things.data.settings.Appearance
import us.liyifan.things.data.settings.ConnectionConfig
import us.liyifan.things.data.settings.EinkColorMode
import us.liyifan.things.data.settings.ThemeMode
import us.liyifan.things.ui.components.GroupHeader
import us.liyifan.things.ui.components.ListScaffold
import us.liyifan.things.ui.components.PageHeader
import us.liyifan.things.ui.components.TextButton
import us.liyifan.things.ui.theme.ThingsTheme

/** What the connection form is currently saying about itself. */
sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Testing : ConnectionStatus
    data class Ok(val message: String) : ConnectionStatus
    data class Problem(val message: String) : ConnectionStatus
}

/**
 * Settings: where the server is, what the app looks like, and the two buttons that undo things.
 *
 * The same form is the first-run screen, which is why it is a composable taking a config rather
 * than a screen that reaches for one.
 */
@Composable
fun SettingsScreen(
    config: ConnectionConfig,
    onConfigChange: (ConnectionConfig) -> Unit,
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    status: ConnectionStatus,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onResync: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    host: ListHost,
    pendingWrites: Int,
    version: String,
) {
    val colors = ThingsTheme.colors

    ListScaffold(onBack = onBack, listState = host.listState) {
        item { PageHeader(title = "Settings") }

        item { GroupHeader(title = "Connection") }
        item {
            ConnectionForm(
                config = config,
                onChange = onConfigChange,
                status = status,
                onTest = onTest,
                onSave = onSave,
            )
        }

        item { GroupHeader(title = "Appearance") }
        item {
            SettingRow(title = "Theme", subtitle = "Light and dark follow the system unless told otherwise") {
                SegmentedChoice(
                    options = ThemeMode.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) },
                    selected = appearance.theme,
                    onSelect = { onAppearanceChange(appearance.copy(theme = it)) },
                )
            }
        }
        item {
            SettingRow(
                title = "E-ink mode",
                subtitle = "Black on white, no animation, bigger targets, and the volume keys turn pages.",
            ) {
                Switch(
                    checked = appearance.eink.enabled,
                    onCheckedChange = { onAppearanceChange(appearance.copy(eink = appearance.eink.copy(enabled = it))) },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.blue),
                )
            }
        }
        if (appearance.eink.enabled) {
            item {
                SettingRow(title = "Colour", subtitle = "A Kaleido panel can show the list colours, muted.") {
                    SegmentedChoice(
                        options = listOf(
                            EinkColorMode.MONOCHROME to "Mono",
                            EinkColorMode.COLOR_ACCENTS to "Colour",
                        ),
                        selected = appearance.eink.colorMode,
                        onSelect = { onAppearanceChange(appearance.copy(eink = appearance.eink.copy(colorMode = it))) },
                    )
                }
            }
            item {
                SettingRow(
                    title = "Full refresh after navigating",
                    subtitle = "Clears ghosting on BOOX hardware. Ignored elsewhere.",
                ) {
                    Switch(
                        checked = appearance.eink.fullRefresh,
                        onCheckedChange = {
                            onAppearanceChange(appearance.copy(eink = appearance.eink.copy(fullRefresh = it)))
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.blue),
                    )
                }
            }
        }

        item { GroupHeader(title = "Data") }
        item {
            Column(Modifier.padding(vertical = 8.dp)) {
                if (pendingWrites > 0) {
                    Text(
                        "$pendingWrites change${if (pendingWrites == 1) "" else "s"} waiting to be sent.",
                        style = ThingsTheme.type.sub,
                        color = colors.text2,
                    )
                }
                TextButton(text = "Resync from the server", onClick = onResync)
                TextButton(text = "Forget this server", onClick = onSignOut, color = colors.deadline)
            }
        }

        item {
            Text(
                "Things for Android $version",
                style = ThingsTheme.type.toast,
                color = colors.text3,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }
}

/**
 * The connection form.
 *
 * Both Cloudflare fields are asked for because the backend's own /mcp endpoint has no
 * authentication at all: an Access service token in front of the tunnel is what keeps the
 * hostname from being an open door to the account.
 */
@Composable
fun ConnectionForm(
    config: ConnectionConfig,
    onChange: (ConnectionConfig) -> Unit,
    status: ConnectionStatus,
    onTest: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThingsTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabelledField(
            label = "Server",
            value = config.baseUrl,
            placeholder = "https://things.example.com",
            onValueChange = { onChange(config.copy(baseUrl = it)) },
            keyboardType = KeyboardType.Uri,
        )
        LabelledField(
            label = "API key",
            value = config.apiKey,
            placeholder = "the backend's API_KEY",
            onValueChange = { onChange(config.copy(apiKey = it)) },
            secret = true,
        )
        LabelledField(
            label = "Cloudflare Access client ID",
            value = config.cfAccessClientId,
            placeholder = "optional",
            onValueChange = { onChange(config.copy(cfAccessClientId = it)) },
        )
        LabelledField(
            label = "Cloudflare Access client secret",
            value = config.cfAccessClientSecret,
            placeholder = "optional",
            onValueChange = { onChange(config.copy(cfAccessClientSecret = it)) },
            secret = true,
        )

        when (status) {
            ConnectionStatus.Idle -> Unit
            ConnectionStatus.Testing -> StatusText("Trying…", colors.text2)
            is ConnectionStatus.Ok -> StatusText(status.message, colors.logbook)
            is ConnectionStatus.Problem -> StatusText(status.message, colors.deadline)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(text = "Test connection", onClick = onTest)
            TextButton(text = "Save", onClick = onSave)
        }
    }
}

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = ThingsTheme.type.sub, color = color)
}

@Composable
private fun LabelledField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = ThingsTheme.colors
    val shape = RoundedCornerShape(9.dp)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = ThingsTheme.type.popoverLabel, color = colors.text3)
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (colors.isEink) Modifier.border(1.5.dp, colors.line, shape)
                    else Modifier.background(colors.bgInput, shape),
                )
                .padding(horizontal = 10.dp, vertical = 11.dp),
        ) {
            if (value.isEmpty()) {
                Text(placeholder, style = ThingsTheme.type.popoverItem, color = colors.text3)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = ThingsTheme.type.popoverItem.copy(color = colors.text),
                cursorBrush = SolidColor(colors.blue),
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    control: @Composable () -> Unit,
) {
    val colors = ThingsTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = ThingsTheme.type.row, color = colors.text)
            if (subtitle != null) {
                Text(subtitle, style = ThingsTheme.type.sub, color = colors.text2)
            }
        }
        control()
    }
}

@Composable
private fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = ThingsTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .then(
                        if (active) Modifier.background(colors.bgChipActive, shape)
                        else Modifier.border(1.dp, colors.line, shape)
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    label,
                    style = ThingsTheme.type.chip,
                    color = if (active) colors.textChipActive else colors.text2,
                )
            }
        }
    }
}
