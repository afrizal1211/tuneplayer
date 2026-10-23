package com.tuneplayer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import com.tuneplayer.app.data.backup.BackupManager
import com.tuneplayer.app.data.prefs.AppTheme
import com.tuneplayer.app.log.LogCatcher

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    theme: AppTheme,
    equalizerEnabled: Boolean,
    bassBoostLevel: Int,
    virtualizerLevel: Int,
    skipSilence: Boolean,
    keepScreenOn: Boolean,
    hapticsEnabled: Boolean,
    dynamicColor: Boolean,
    pureBlack: Boolean,
    eqBands: List<Int>,
    eqBandFreqs: List<Int>,
    eqBandRange: IntRange?,
    eqPreset: Int,
    onThemeChange: (AppTheme) -> Unit,
    onEqualizerChange: (Boolean, Int, Int) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
    onEqBandChange: (Int, Int) -> Unit,
    onEqPresetChange: (Int) -> Unit,
    onReloadFromStores: () -> Unit,
) {
    var showLogs by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            BackupManager.export(context, uri)
                .onSuccess { count ->
                    backupStatus = context.getString(R.string.backup_exported, count)
                }
                .onFailure { error ->
                    backupStatus = context.getString(
                        R.string.backup_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            BackupManager.restore(context, uri)
                .onSuccess { count ->
                    onReloadFromStores()
                    backupStatus = context.getString(R.string.backup_imported, count)
                }
                .onFailure { error ->
                    backupStatus = context.getString(
                        R.string.backup_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SettingsSectionTitle(stringResource(R.string.settings_appearance))
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
                        contentDescription = stringResource(R.string.state_selected),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        SettingsToggleRow(
            label = stringResource(R.string.settings_dynamic_color),
            checked = dynamicColor,
            onCheckedChange = onDynamicColorChange,
        )
        SettingsToggleRow(
            label = stringResource(R.string.settings_pure_black),
            checked = pureBlack,
            onCheckedChange = onPureBlackChange,
        )

        Spacer(Modifier.height(24.dp))
        SettingsSectionTitle(stringResource(R.string.settings_playback))
        SettingsToggleRow(
            label = stringResource(R.string.settings_keep_screen_on),
            checked = keepScreenOn,
            onCheckedChange = onKeepScreenOnChange,
        )
        SettingsToggleRow(
            label = stringResource(R.string.settings_haptics),
            checked = hapticsEnabled,
            onCheckedChange = onHapticsChange,
        )

        Spacer(Modifier.height(24.dp))
        SettingsSectionTitle(stringResource(R.string.settings_backup))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { exportLauncher.launch(backupFileName(context)) }) {
                Text(stringResource(R.string.backup_export))
            }
            TextButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                Text(stringResource(R.string.backup_import))
            }
        }
        backupStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        SettingsSectionTitle(stringResource(R.string.settings_audio_effects))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.settings_enable_effects),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = equalizerEnabled,
                onCheckedChange = { onEqualizerChange(it, bassBoostLevel, virtualizerLevel) },
            )
        }
        EffectSlider(
            label = stringResource(R.string.settings_bass_boost),
            value = bassBoostLevel,
            enabled = equalizerEnabled,
            onValueChange = { onEqualizerChange(equalizerEnabled, it, virtualizerLevel) },
        )
        EffectSlider(
            label = stringResource(R.string.settings_virtualizer),
            value = virtualizerLevel,
            enabled = equalizerEnabled,
            onValueChange = { onEqualizerChange(equalizerEnabled, bassBoostLevel, it) },
        )
        Text(
            text = stringResource(R.string.settings_effects_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        SettingsToggleRow(
            label = stringResource(R.string.settings_skip_silence),
            checked = skipSilence,
            onCheckedChange = onSkipSilenceChange,
        )
        Text(
            text = stringResource(R.string.settings_skip_silence_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (equalizerEnabled && eqBandFreqs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SettingsSectionTitle(stringResource(R.string.settings_equalizer))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EQ_PRESET_LABELS.forEachIndexed { index, labelRes ->
                    FilterChip(
                        selected = eqPreset == index,
                        onClick = { onEqPresetChange(index) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            val min = eqBandRange?.first ?: -1500
            val max = eqBandRange?.last ?: 1500
            // A degenerate range makes Slider throw; skip the sliders instead.
            val rangeUsable = min < max
            if (rangeUsable) {
                eqBandFreqs.forEachIndexed { index, freq ->
                    val level = eqBands.getOrNull(index) ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatBandFreq(freq),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(56.dp),
                        )
                        Slider(
                            value = level.toFloat(),
                            valueRange = min.toFloat()..max.toFloat(),
                            onValueChange = { onEqBandChange(index, it.toInt()) },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "%d dB".format(level / 100),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(48.dp),
                        )
                    }
                }
            }
        }
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

private fun backupFileName(context: android.content.Context): String =
    context.getString(R.string.backup_export_title)

private val EQ_PRESET_LABELS = listOf(
    R.string.eq_preset_flat,
    R.string.eq_preset_bass,
    R.string.eq_preset_vocal,
    R.string.eq_preset_rock,
)

private fun formatBandFreq(freqHz: Int): String =
    if (freqHz >= 1000) "%.1fk".format(freqHz / 1000f) else "$freqHz Hz"

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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
