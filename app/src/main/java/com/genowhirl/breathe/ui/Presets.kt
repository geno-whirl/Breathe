package com.genowhirl.breathe.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.genowhirl.breathe.R
import com.genowhirl.breathe.model.BreathPattern
import com.genowhirl.breathe.model.Preset

/** What the preset row can do; the screen wires these to the store. */
class PresetActions(
    val apply: (Preset) -> Unit,
    val saveNew: (String) -> Unit,
    val update: (Preset) -> Unit,
    val rename: (Preset, String) -> Unit,
    val delete: (Preset) -> Unit,
)

private sealed interface PresetDialog {
    data object SaveNew : PresetDialog
    data class SaveChanges(val preset: Preset) : PresetDialog
    data class Options(val preset: Preset) : PresetDialog
    data class Rename(val preset: Preset) : PresetDialog
    data class ConfirmDelete(val preset: Preset) : PresetDialog
}

/**
 * The presets as chips. The last preset chosen is marked when the pattern has been changed
 * since, and the save button then offers to save the changes into it. Long-press a preset to
 * update, rename or delete it.
 */
@Composable
fun PresetRow(
    pattern: BreathPattern,
    presets: List<Preset>,
    lastPresetId: String?,
    actions: PresetActions,
) {
    var dialog by remember { mutableStateOf<PresetDialog?>(null) }
    val last = presets.firstOrNull { it.id == lastPresetId }
    val lastModified = last != null && !last.pattern.sameRhythmAs(pattern)

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { preset ->
            val modified = lastModified && preset.id == last?.id
            PillButton(
                text = if (modified) stringResource(R.string.preset_modified, preset.name) else preset.name,
                selected = preset.pattern.sameRhythmAs(pattern) &&
                    (preset.id == lastPresetId || presets.none { it.id == lastPresetId && it.pattern.sameRhythmAs(pattern) }),
                accent = MaterialTheme.colorScheme.primary,
                onClick = { actions.apply(preset) },
                onLongClick = { dialog = PresetDialog.Options(preset) },
            )
        }
        IconButton(
            onClick = {
                dialog = if (lastModified && last?.builtIn == false) PresetDialog.SaveChanges(last) else PresetDialog.SaveNew
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(painterResource(R.drawable.ic_bookmark_add), contentDescription = stringResource(R.string.save_preset))
        }
    }

    when (val d = dialog) {
        null -> Unit
        PresetDialog.SaveNew -> NameDialog(
            title = stringResource(R.string.save_preset),
            initial = "",
            confirm = stringResource(R.string.save),
            onDismiss = { dialog = null },
            onConfirm = { actions.saveNew(it); dialog = null },
        )
        is PresetDialog.SaveChanges -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.save_changes_title)) },
            text = { Text(stringResource(R.string.save_changes_text, d.preset.name)) },
            confirmButton = {
                TextButton(onClick = { actions.update(d.preset); dialog = null }) {
                    Text(stringResource(R.string.update_preset, d.preset.name))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = PresetDialog.SaveNew }) { Text(stringResource(R.string.save_as_new)) }
            },
        )
        is PresetDialog.Options -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(d.preset.name) },
            text = {
                Column {
                    if (d.preset.builtIn) {
                        Text(
                            stringResource(R.string.built_in_preset_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        if (!d.preset.pattern.sameRhythmAs(pattern)) {
                            TextButton(onClick = { actions.update(d.preset); dialog = null }) {
                                Text(stringResource(R.string.update_with_current))
                            }
                        }
                        TextButton(onClick = { dialog = PresetDialog.Rename(d.preset) }) {
                            Text(stringResource(R.string.rename))
                        }
                    }
                    TextButton(onClick = { dialog = PresetDialog.ConfirmDelete(d.preset) }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        is PresetDialog.Rename -> NameDialog(
            title = stringResource(R.string.rename),
            initial = d.preset.name,
            confirm = stringResource(R.string.save),
            onDismiss = { dialog = null },
            onConfirm = { actions.rename(d.preset, it); dialog = null },
        )
        is PresetDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.delete_preset_title, d.preset.name)) },
            text = { Text(stringResource(R.string.delete_preset_text)) },
            confirmButton = {
                TextButton(onClick = { actions.delete(d.preset); dialog = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.preset_name_hint)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
