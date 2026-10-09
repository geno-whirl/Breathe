package com.genowhirl.breathe.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.genowhirl.breathe.R
import com.genowhirl.breathe.audio.Cue
import com.genowhirl.breathe.audio.CuePlayer
import com.genowhirl.breathe.audio.Haptics
import com.genowhirl.breathe.model.AnimationStyle
import com.genowhirl.breathe.model.AppSettings
import com.genowhirl.breathe.model.Phase
import com.genowhirl.breathe.model.SoundStyle
import com.genowhirl.breathe.model.ThemeMode
import com.genowhirl.breathe.model.VibrationMode

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onRestorePresets: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { CuePlayer(context) }
    val haptics = remember { Haptics(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    // Re-check when returning from the system settings screen.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val notificationsEnabled = remember(lifecycleState) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun previewSound(s: AppSettings = settings) {
        if (s.soundEnabled) player.play(s.soundStyle, Cue.INHALE, s.soundVolume)
    }

    fun previewVibration(s: AppSettings = settings) {
        if (!s.vibrationEnabled) return
        if (s.vibrationMode == VibrationMode.WAVE) {
            haptics.wave(Phase.INHALE, 2500, s.vibrationStrength)
        } else {
            haptics.cue(Cue.INHALE, s.vibrationStrength)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))

        val gap = Modifier.height(14.dp)

        SectionCard(stringResource(R.string.sound)) {
            ToggleRow(stringResource(R.string.sound_cues), checked = settings.soundEnabled) { on ->
                onSettings { it.copy(soundEnabled = on) }
            }
            if (settings.soundEnabled) {
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = SoundStyle.entries,
                    selected = settings.soundStyle,
                    label = {
                        stringResource(
                            when (it) {
                                SoundStyle.BOWL -> R.string.sound_bowl
                                SoundStyle.CHIME -> R.string.sound_chime
                                SoundStyle.SOFT -> R.string.sound_soft
                                SoundStyle.WOOD -> R.string.sound_wood
                                SoundStyle.VOICE -> R.string.sound_voice
                            },
                        )
                    },
                    onSelect = { style ->
                        onSettings { it.copy(soundStyle = style) }
                        player.prepare(style)
                        previewSound(settings.copy(soundStyle = style))
                    },
                )
                var volume by remember(settings.soundVolume) { mutableFloatStateOf(settings.soundVolume) }
                SliderRow(
                    title = stringResource(R.string.volume),
                    value = volume,
                    onChange = { volume = it },
                    onDone = {
                        onSettings { it.copy(soundVolume = volume) }
                        previewSound(settings.copy(soundVolume = volume))
                    },
                )
                ToggleRow(stringResource(R.string.cue_on_holds), checked = settings.soundOnHolds) { on ->
                    onSettings { it.copy(soundOnHolds = on) }
                }
                ToggleRow(
                    stringResource(R.string.tick_seconds),
                    stringResource(R.string.tick_seconds_hint),
                    checked = settings.tickSeconds,
                ) { on -> onSettings { it.copy(tickSeconds = on) } }
            }
        }
        Spacer(gap)

        SectionCard(stringResource(R.string.vibration)) {
            ToggleRow(stringResource(R.string.vibration_cues), checked = settings.vibrationEnabled) { on ->
                onSettings { it.copy(vibrationEnabled = on) }
            }
            if (settings.vibrationEnabled) {
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = VibrationMode.entries,
                    selected = settings.vibrationMode,
                    label = {
                        stringResource(if (it == VibrationMode.CUES) R.string.vibration_pulses else R.string.vibration_wave)
                    },
                    onSelect = { mode ->
                        onSettings { it.copy(vibrationMode = mode) }
                        previewVibration(settings.copy(vibrationMode = mode))
                    },
                )
                Text(
                    stringResource(
                        if (settings.vibrationMode == VibrationMode.CUES) R.string.vibration_pulses_hint else R.string.vibration_wave_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                )
                var strength by remember(settings.vibrationStrength) { mutableFloatStateOf(settings.vibrationStrength) }
                SliderRow(
                    title = stringResource(R.string.strength),
                    value = strength,
                    enabled = haptics.canVaryStrength,
                    onChange = { strength = it },
                    onDone = {
                        onSettings { it.copy(vibrationStrength = strength) }
                        previewVibration(settings.copy(vibrationStrength = strength))
                    },
                )
                ToggleRow(stringResource(R.string.cue_on_holds), checked = settings.vibrationOnHolds) { on ->
                    onSettings { it.copy(vibrationOnHolds = on) }
                }
            }
        }
        Spacer(gap)

        SectionCard(stringResource(R.string.animation)) {
            ToggleRow(stringResource(R.string.animation_enabled), checked = settings.animationEnabled) { on ->
                onSettings { it.copy(animationEnabled = on) }
            }
            if (settings.animationEnabled) {
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = AnimationStyle.entries,
                    selected = settings.animationStyle,
                    label = {
                        stringResource(
                            when (it) {
                                AnimationStyle.ORB -> R.string.animation_orb
                                AnimationStyle.BOX -> R.string.animation_box
                                AnimationStyle.MINIMAL -> R.string.animation_ring
                            },
                        )
                    },
                    onSelect = { style -> onSettings { it.copy(animationStyle = style) } },
                )
                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    stringResource(R.string.natural_easing),
                    stringResource(R.string.natural_easing_hint),
                    checked = settings.naturalEasing,
                ) { on -> onSettings { it.copy(naturalEasing = on) } }
            }
            ToggleRow(stringResource(R.string.show_countdown), checked = settings.showCountdown) { on ->
                onSettings { it.copy(showCountdown = on) }
            }
        }
        Spacer(gap)

        SectionCard(stringResource(R.string.session)) {
            ToggleRow(
                stringResource(R.string.keep_screen_on),
                stringResource(R.string.keep_screen_on_hint),
                checked = settings.keepScreenOn,
            ) { on -> onSettings { it.copy(keepScreenOn = on) } }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            ChoiceRow(
                options = ThemeMode.entries,
                selected = settings.theme,
                label = {
                    stringResource(
                        when (it) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        },
                    )
                },
                onSelect = { mode -> onSettings { it.copy(theme = mode) } },
            )
        }
        Spacer(gap)

        SectionCard(stringResource(R.string.locked_phone)) {
            Text(
                stringResource(R.string.locked_phone_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!notificationsEnabled) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.notifications_off),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!notificationsEnabled) {
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text(stringResource(R.string.notifications_allow)) }
                }
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.open_app_settings)) }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRestorePresets) { Text(stringResource(R.string.restore_presets)) }
        }
        Spacer(gap)

        SectionCard(stringResource(R.string.about)) {
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
            }
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.about_license),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text(stringResource(R.string.about_source)) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private const val SOURCE_URL = "https://github.com/geno-whirl/Breathe"
