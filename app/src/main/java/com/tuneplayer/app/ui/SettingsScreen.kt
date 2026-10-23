package com.tuneplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuneplayer.app.R
import com.tuneplayer.app.data.prefs.AppTheme
import com.tuneplayer.app.log.LogCatcher

@Composable
fun SettingsScreen(
    theme: AppTheme,
    equalizerEnabled: Boolean,
    bassBoostLevel: Int,
    virtualizerLevel: Int,
    onThemeChange: (AppTheme) -> Unit,
    onEqualizerChange: (Boolean, Int, Int) -> Unit,
) {
    var showLogs by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SettingsSectionTitle("Appearance")
        AppTheme.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onThemeChange(option) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(option.label(), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                if (theme == option) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SettingsSectionTitle(stringResource(R.string.settings_diagnostics))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLogs = true }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.logs_view), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = stringResource(R.string.logs_crash_installed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        SettingsSectionTitle("Audio effects")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Enable effects", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = equalizerEnabled,
                onCheckedChange = { onEqualizerChange(it, bassBoostLevel, virtualizerLevel) },
            )
        }
        EffectSlider(
            label = "Bass boost",
            value = bassBoostLevel,
            enabled = equalizerEnabled,
            onValueChange = { onEqualizerChange(equalizerEnabled, it, virtualizerLevel) },
        )
        EffectSlider(
            label = "Virtualizer",
            value = virtualizerLevel,
            enabled = equalizerEnabled,
            onValueChange = { onEqualizerChange(equalizerEnabled, bassBoostLevel, it) },
        )
        Text(
            text = "Effects apply to the active playback session and reset when it ends.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    if (showLogs) {
        LogViewerDialog(
            onDismiss = { showLogs = false },
            onShare = { text ->
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                context.startActivity(
                    android.content.Intent.createChooser(
                        intent,
                        context.getString(R.string.logs_share),
                    ),
                )
            },
        )
    }
}

@Composable
private fun LogViewerDialog(
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
) {
    val entries = LogCatcher.snapshot()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logs_title)) },
        text = {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.logs_empty))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    entries.asReversed().forEach { entry ->
                        Text(
                            text = entry.render(),
                            style = MaterialTheme.typography.bodySmall,
                            color = when (entry.level) {
                                com.tuneplayer.app.log.LogLevel.E -> MaterialTheme.colorScheme.error
                                com.tuneplayer.app.log.LogLevel.W -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onShare(LogCatcher.export()) },
                enabled = entries.isNotEmpty(),
            ) { Text(stringResource(R.string.logs_share)) }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { LogCatcher.clear() },
                    enabled = entries.isNotEmpty(),
                ) { Text(stringResource(R.string.logs_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.logs_close)) }
            }
        },
    )
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun EffectSlider(
    label: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value%", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            enabled = enabled,
        )
    }
}

private fun AppTheme.label(): String = when (this) {
    AppTheme.System -> "Follow system"
    AppTheme.Light -> "Light"
    AppTheme.Dark -> "Dark"
}
