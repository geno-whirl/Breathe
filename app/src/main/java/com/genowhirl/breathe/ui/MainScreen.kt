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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
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
import com.genowhirl.breathe.model.SessionLength
import com.genowhirl.breathe.model.TimingMode
import com.genowhirl.breathe.ui.theme.PhaseColors
import kotlinx.coroutines.delay

/** Everything on one screen: the breathing visual takes whatever height is left. */
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
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(settings, onSettings, onOpenSettings)

        BreathVisual(
            state = session,
            pattern = pattern,
            settings = settings,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 120.dp)
                .fillMaxWidth(),
        )

        StatusLine(session, pattern, presets, settings)
        Spacer(Modifier.height(10.dp))
        Controls(session, settings, onToggle, onStop, onSettings)
        Spacer(Modifier.height(14.dp))

        PatternPanel(
            pattern = pattern,
            presets = presets,
            onPattern = onPattern,
            onApplyPreset = onApplyPreset,
            onSavePreset = onSavePreset,
            onDeletePreset = onDeletePreset,
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun TopBar(
    settings: AppSettings,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp),
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
}

@Composable
private fun StatusLine(session: SessionState, pattern: BreathPattern, presets: List<Preset>, settings: AppSettings) {
    val context = LocalContext.current
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
        val elapsed = formatClock(session.activeMillis(now))
        val time = if (settings.sessionLength == SessionLength.MINUTES) {
            stringResource(R.string.elapsed_of, elapsed, formatClock(settings.sessionMinutes * 60_000L))
        } else {
            elapsed
        }
        stringResource(R.string.status_running, name, cycleLabel(context.resources, session.cycle, settings), time)
    } else {
        stringResource(
            R.string.status_idle,
            name,
            formatSeconds(pattern.cycleMillis / 1000.0),
            formatSeconds(pattern.breathsPerMinute),
        )
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

@Composable
private fun Controls(
    session: SessionState,
    settings: AppSettings,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
    val running = session.status == Status.RUNNING
    var editingLength by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val stopAlpha by animateFloatAsState(if (session.isActive) 1f else 0f, label = "stopAlpha")
        Box(Modifier.width(72.dp).alpha(stopAlpha), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onStop,
                enabled = session.isActive,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.stop))
            }
        }
        Spacer(Modifier.width(20.dp))
        val accent by animateColorAsState(
            if (session.isActive) PhaseColors.of(session.phase) else PhaseColors.inhale,
            label = "playAccent",
        )
        Box(
            Modifier
                .size(72.dp)
                .shadow(14.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape)
                .background(accent)
                .clickable(role = Role.Button, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (running) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(if (running) R.string.pause else R.string.start),
                tint = Color(0xFF0B1020),
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.width(20.dp))
        SessionLengthButton(settings, Modifier.width(72.dp)) { editingLength = true }
    }
    if (editingLength) {
        SessionLengthDialog(settings, onSettings) { editingLength = false }
    }
}

/** Shows how long the session lasts (endless, minutes or cycles); tap to change it. */
@Composable
private fun SessionLengthButton(settings: AppSettings, modifier: Modifier, onClick: () -> Unit) {
    val (value, unit) = when (settings.sessionLength) {
        SessionLength.ENDLESS -> "∞" to stringResource(R.string.endless_short)
        SessionLength.MINUTES -> settings.sessionMinutes.toString() to stringResource(R.string.unit_minutes)
        SessionLength.CYCLES -> settings.sessionCycles.toString() to stringResource(R.string.unit_cycles)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun SessionLengthDialog(
    settings: AppSettings,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_length)) },
        text = {
            Column {
                ChoiceRow(
                    options = SessionLength.entries,
                    selected = settings.sessionLength,
                    label = {
                        stringResource(
                            when (it) {
                                SessionLength.ENDLESS -> R.string.endless
                                SessionLength.MINUTES -> R.string.length_minutes
                                SessionLength.CYCLES -> R.string.length_cycles
                            },
                        )
                    },
                    onSelect = { length -> onSettings { it.copy(sessionLength = length) } },
                )
                Spacer(Modifier.height(12.dp))
                when (settings.sessionLength) {
                    SessionLength.ENDLESS -> Text(
                        stringResource(R.string.endless_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                    SessionLength.MINUTES -> ValueStepper(
                        title = stringResource(R.string.length_minutes),
                        value = settings.sessionMinutes.toDouble(),
                        display = stringResource(R.string.minutes_short, settings.sessionMinutes),
                        step = 1.0,
                        min = 1.0,
                        max = 240.0,
                        decimals = false,
                        onChange = { v -> onSettings { it.copy(sessionMinutes = v.toInt()) } },
                    )
                    SessionLength.CYCLES -> ValueStepper(
                        title = stringResource(R.string.length_cycles),
                        value = settings.sessionCycles.toDouble(),
                        display = settings.sessionCycles.toString(),
                        step = 1.0,
                        min = 1.0,
                        max = 999.0,
                        decimals = false,
                        onChange = { v -> onSettings { it.copy(sessionCycles = v.toInt()) } },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun PatternPanel(
    pattern: BreathPattern,
    presets: List<Preset>,
    onPattern: ((BreathPattern) -> BreathPattern) -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
) {
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Preset?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
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
                IconButton(onClick = { saving = true }, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ic_bookmark_add), contentDescription = stringResource(R.string.save_preset))
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Phase.entries.forEach { phase ->
                    PhaseStepper(phase, pattern, onPattern, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(10.dp))
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

            Spacer(Modifier.height(4.dp))
            when (pattern.mode) {
                TimingMode.SECONDS -> Row(
                    Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.cycle_summary,
                            formatSeconds(pattern.cycleMillis / 1000.0),
                            formatSeconds(pattern.breathsPerMinute),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        }
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
        Spacer(Modifier.height(4.dp))
        StepButton(R.drawable.ic_add, stringResource(R.string.increase, label), enabled = value < BreathPattern.MAX_UNITS, size = 32.dp) {
            onPattern { latest.withUnits(phase, latest.units(phase) + 1) }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { editing = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Light,
                color = if (value == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
            )
        }
        StepButton(R.drawable.ic_remove, stringResource(R.string.decrease, label), enabled = value > 0, size = 32.dp) {
            onPattern { latest.withUnits(phase, latest.units(phase) - 1) }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.seconds_short, formatSeconds(pattern.phaseMillis(phase) / 1000.0, 1)),
            style = MaterialTheme.typography.labelSmall,
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
