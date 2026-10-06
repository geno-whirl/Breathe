package com.genowhirl.breathe.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genowhirl.breathe.data.Store
import com.genowhirl.breathe.engine.Session
import com.genowhirl.breathe.engine.Status
import com.genowhirl.breathe.model.ThemeMode
import com.genowhirl.breathe.ui.theme.BreatheTheme
import com.genowhirl.breathe.ui.theme.LocalAmbience

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store.get(this)
        setContent {
            val settings by store.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            BreatheTheme(settings.theme) {
                BreatheApp(store)
            }
        }
    }
}

@Composable
private fun BreatheApp(store: Store) {
    val context = LocalContext.current
    val session by Session.state.collectAsStateWithLifecycle()
    val pattern by store.pattern.collectAsStateWithLifecycle()
    val presets by store.presets.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val view = LocalView.current
    val keepOn = settings.keepScreenOn && session.status == Status.RUNNING
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The session works either way; the permission only makes its notification visible.
        Session.start(context)
    }

    fun toggle() {
        if (session.status == Status.FINISHED) Session.dismissFinished()
        val needsPermission = !session.isActive &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Session.toggle(context)
        }
    }

    BackHandler(enabled = showSettings) { showSettings = false }

    val ambience = LocalAmbience.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ambience.top, ambience.bottom))),
    ) {
        AnimatedContent(
            targetState = showSettings,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.safeDrawingPadding(),
            label = "screen",
        ) { settingsVisible ->
            if (settingsVisible) {
                SettingsScreen(
                    settings = settings,
                    onSettings = store::updateSettings,
                    onRestorePresets = store::restoreBuiltInPresets,
                    onBack = { showSettings = false },
                )
            } else {
                MainScreen(
                    session = session,
                    pattern = pattern,
                    presets = presets,
                    settings = settings,
                    onToggle = ::toggle,
                    onStop = { Session.stop(context) },
                    onPattern = store::updatePattern,
                    onSettings = store::updateSettings,
                    onApplyPreset = store::applyPreset,
                    onSavePreset = { store.savePreset(it) },
                    onDeletePreset = { store.deletePreset(it.id) },
                    onOpenSettings = { showSettings = true },
                )
            }
        }
    }
}
