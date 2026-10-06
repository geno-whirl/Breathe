package com.genowhirl.breathe.ui

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.genowhirl.breathe.R
import com.genowhirl.breathe.engine.SessionState
import com.genowhirl.breathe.engine.Status
import com.genowhirl.breathe.model.AppSettings
import com.genowhirl.breathe.model.BreathPattern
import com.genowhirl.breathe.model.Phase
import com.genowhirl.breathe.model.Preset
import com.genowhirl.breathe.model.TimingMode
import com.genowhirl.breathe.ui.theme.PhaseColors
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    session: SessionState,
    pattern: BreathPattern,
    presets: List<Preset>,
    settings: AppSettings,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onPattern: ((BreathPattern) -> BreathPattern) -> Unit,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onSettings { it.copy(soundEnabled = !it.soundEnabled) } }) {
                Icon(
                    painterResource(if (settings.soundEnabled) R.drawable.ic_volume_up else R.drawable.ic_volume_off),
                    contentDescription = stringResource(R.string.sound),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (settings.soundEnabled) 0.9f else 0.45f),
                )
            }
            IconButton(onClick = { onSettings { it.copy(vibrationEnabled = !it.vibrationEnabled) } }) {
                Icon(
                    painterResource(if (settings.vibrationEnabled) R.drawable.ic_vibration else R.drawable.ic_vibration_off),
                    contentDescription = stringResource(R.string.vibration),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (settings.vibrationEnabled) 0.9f else 0.45f),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(R.drawable.ic_tune), contentDescription = stringResource(R.string.settings))
            }
        }

        BreathVisual(
            state = session,
            pattern = pattern,
            settings = settings,
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp),
        )

        StatusLine(session, pattern, presets)

        Spacer(Modifier.height(16.dp))
        Controls(session, onToggle, onStop)
        Spacer(Modifier.height(24.dp))

        PatternPanel(
            pattern = pattern,
            presets = presets,
            settings = settings,
            onPattern = onPattern,
            onSettings = onSettings,
            onApplyPreset = onApplyPreset,
            onSavePreset = onSavePreset,
            onDeletePreset = onDeletePreset,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusLine(session: SessionState, pattern: BreathPattern, presets: List<Preset>) {
    val preset = presets.firstOrNull { it.pattern.sameRhythmAs(pattern) }
    val name = preset?.name ?: stringResource(R.string.custom_pattern)
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(session.status) {
        while (session.status == Status.RUNNING) {
            now = SystemClock.elapsedRealtime()
            delay(1000 - now % 1000)
        }
        now = SystemClock.elapsedRealtime()
    }
    val text = if (session.isActive) {
        stringResource(R.string.status_running, name, session.cycle, formatClock(session.activeMillis(now)))
    } else {
        stringResource(
            R.string.status_idle,
            name,
            formatSeconds(pattern.cycleMillis / 1000.0),
            formatSeconds(pattern.breathsPerMinute),
        )
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Controls(session: SessionState, onToggle: () -> Unit, onStop: () -> Unit) {
    val running = session.status == Status.RUNNING
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val stopAlpha by animateFloatAsState(if (session.isActive) 1f else 0f, label = "stopAlpha")
        Box(Modifier.size(56.dp).alpha(stopAlpha), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onStop,
                enabled = session.isActive,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.stop))
            }
        }
        Spacer(Modifier.size(24.dp))
        val accent by animateColorAsState(
            if (session.isActive) PhaseColors.of(session.phase) else PhaseColors.inhale,
            label = "playAccent",
        )
        Box(
            Modifier
                .size(80.dp)
                .shadow(16.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape)
                .background(accent)
                .clickable(role = Role.Button, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (running) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(if (running) R.string.pause else R.string.start),
                tint = Color(0xFF0B1020),
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.size(24.dp))
        Spacer(Modifier.size(56.dp))
    }
}

@Composable
private fun PatternPanel(
    pattern: BreathPattern,
    presets: List<Preset>,
    settings: AppSettings,
    onPattern: ((BreathPattern) -> BreathPattern) -> Unit,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
) {
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Preset?>(null) }

    SectionCard(stringResource(R.string.pattern)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            presets.forEach { preset ->
                PillButton(
                    text = preset.name,
                    selected = preset.pattern.sameRhythmAs(pattern),
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = { onApplyPreset(preset) },
                    onLongClick = { deleting = preset },
                )
            }
            IconButton(onClick = { saving = true }) {
                Icon(painterResource(R.drawable.ic_bookmark_add), contentDescription = stringResource(R.string.save_preset))
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Phase.entries.forEach { phase ->
                PhaseStepper(phase, pattern, onPattern, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(18.dp))
        ChoiceRow(
            options = TimingMode.entries,
            selected = pattern.mode,
            label = {
                stringResource(
                    when (it) {
                        TimingMode.SECONDS -> R.string.mode_seconds
                        TimingMode.CYCLE_LENGTH -> R.string.mode_cycle
                        TimingMode.PER_MINUTE -> R.string.mode_per_minute
                    },
                )
            },
            onSelect = { mode ->
                onPattern { p ->
                    // Carry the current rhythm over so switching modes does not change the timing.
                    val seconds = p.cycleMillis / 1000.0
                    p.copy(
                        mode = mode,
                        cycleSeconds = if (mode == TimingMode.CYCLE_LENGTH) {
                            roundHalf(seconds).coerceIn(BreathPattern.MIN_CYCLE_SECONDS, BreathPattern.MAX_CYCLE_SECONDS)
                        } else {
                            p.cycleSeconds
                        },
                        cyclesPerMinute = if (mode == TimingMode.PER_MINUTE) {
                            roundHalf(60.0 / seconds).coerceIn(BreathPattern.MIN_PER_MINUTE, BreathPattern.MAX_PER_MINUTE)
                        } else {
                            p.cyclesPerMinute
                        },
                    )
                }
            },
        )

        Spacer(Modifier.height(8.dp))
        when (pattern.mode) {
            TimingMode.SECONDS -> Text(
                stringResource(R.string.mode_seconds_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            )
            TimingMode.CYCLE_LENGTH -> ValueStepper(
                title = stringResource(R.string.cycle_length),
                value = pattern.cycleSeconds,
                display = stringResource(R.string.seconds_short, formatSeconds(pattern.cycleSeconds)),
                step = 0.5,
                min = BreathPattern.MIN_CYCLE_SECONDS,
                max = BreathPattern.MAX_CYCLE_SECONDS,
                decimals = true,
                onChange = { v -> onPattern { it.copy(cycleSeconds = v) } },
            )
            TimingMode.PER_MINUTE -> ValueStepper(
                title = stringResource(R.string.breaths_per_minute),
                value = pattern.cyclesPerMinute,
                display = formatSeconds(pattern.cyclesPerMinute),
                step = 0.5,
                min = BreathPattern.MIN_PER_MINUTE,
                max = BreathPattern.MAX_PER_MINUTE,
                decimals = true,
                onChange = { v -> onPattern { it.copy(cyclesPerMinute = v) } },
            )
        }

        ValueStepper(
            title = stringResource(R.string.session_length),
            value = settings.sessionMinutes.toDouble(),
            display = if (settings.sessionMinutes == 0) {
                stringResource(R.string.endless)
            } else {
                stringResource(R.string.minutes_short, settings.sessionMinutes)
            },
            step = 1.0,
            min = 0.0,
            max = 180.0,
            decimals = false,
            onChange = { v -> onSettings { it.copy(sessionMinutes = v.toInt()) } },
        )
    }

    if (saving) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text(stringResource(R.string.save_preset)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.preset_name_hint)) },
                )
            },
            confirmButton = {
                TextButton(onClick = { onSavePreset(name); saving = false }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_preset_title, preset.name)) },
            text = { Text(stringResource(R.string.delete_preset_text)) },
            confirmButton = {
                TextButton(onClick = { onDeletePreset(preset); deleting = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PhaseStepper(
    phase: Phase,
    pattern: BreathPattern,
    onPattern: ((BreathPattern) -> BreathPattern) -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = pattern.units(phase)
    val latest by rememberUpdatedState(pattern)
    var editing by remember { mutableStateOf(false) }
    val color = PhaseColors.of(phase)
    val label = stringResource(
        when (phase) {
            Phase.INHALE -> R.string.short_inhale
            Phase.HOLD_IN -> R.string.short_hold
            Phase.EXHALE -> R.string.short_exhale
            Phase.HOLD_OUT -> R.string.short_hold
        },
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.size(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        StepButton(R.drawable.ic_add, stringResource(R.string.increase, label), enabled = value < BreathPattern.MAX_UNITS) {
            onPattern { latest.withUnits(phase, latest.units(phase) + 1) }
        }
        Box(
            Modifier
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { editing = true }
                .padding(horizontal = 12.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = if (value == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
            )
        }
        StepButton(R.drawable.ic_remove, stringResource(R.string.decrease, label), enabled = value > 0) {
            onPattern { latest.withUnits(phase, latest.units(phase) - 1) }
        }
        Spacer(Modifier.height(6.dp))
        val seconds = pattern.phaseMillis(phase) / 1000.0
        Text(
            if (pattern.mode == TimingMode.SECONDS) {
                stringResource(R.string.seconds_short, value.toString())
            } else {
                stringResource(R.string.seconds_short, formatSeconds(seconds, 1))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (editing) {
        NumberDialog(
            title = label,
            initial = value.toString(),
            decimals = false,
            onDismiss = { editing = false },
            onConfirm = { v ->
                onPattern { it.withUnits(phase, v.toInt()) }
                editing = false
            },
        )
    }
}

private fun roundHalf(v: Double): Double = Math.round(v * 2) / 2.0
