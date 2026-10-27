package com.genowhirl.breathe.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.genowhirl.breathe.engine.SessionState
import com.genowhirl.breathe.engine.Status
import com.genowhirl.breathe.model.AnimationStyle
import com.genowhirl.breathe.model.AppSettings
import com.genowhirl.breathe.model.BreathPattern
import com.genowhirl.breathe.model.BuiltInPresets
import com.genowhirl.breathe.model.Phase
import com.genowhirl.breathe.model.SessionLength
import com.genowhirl.breathe.model.SessionRecord
import com.genowhirl.breathe.model.ThemeMode
import com.genowhirl.breathe.ui.theme.BreatheTheme
import com.genowhirl.breathe.ui.theme.LocalAmbience
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the phone screenshots for the store listings. Excluded from normal test runs; see
 * testOptions in app/build.gradle.kts. A 1080 x 2160 phone, the tallest shape Play accepts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-xxhdpi")
class StoreScreenshots {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val presets = BuiltInPresets.all
    private val noPresetActions = PresetActions({}, {}, {}, { _, _ -> }, {})

    @Test
    fun breathingInDark() = shot("1_breathe_dark", ThemeMode.DARK) {
        val box = presets.first { it.id == "box" }
        Main(
            session = running(Phase.INHALE, elapsed = 2_600, duration = 4_000, cycle = 7, cycleMillis = 16_000),
            pattern = box.pattern,
            settings = AppSettings(theme = ThemeMode.DARK, sessionLength = SessionLength.MINUTES, sessionMinutes = 5),
            lastPresetId = box.id,
        )
    }

    @Test
    fun boxInLight() = shot("2_box_light", ThemeMode.LIGHT) {
        val preset = presets.first { it.id == "478" }
        Main(
            session = running(Phase.EXHALE, elapsed = 3_000, duration = 8_000, cycle = 3, cycleMillis = 19_000),
            pattern = preset.pattern,
            settings = AppSettings(
                theme = ThemeMode.LIGHT,
                animationStyle = AnimationStyle.BOX,
                sessionLength = SessionLength.CYCLES,
                sessionCycles = 8,
            ),
            lastPresetId = preset.id,
        )
    }

    @Test
    fun ringWithCounts() = shot("3_coherent_dark", ThemeMode.DARK) {
        val preset = presets.first { it.id == "coherent" }
        Main(
            session = running(Phase.INHALE, elapsed = 1_200, duration = 5_455, cycle = 4, cycleMillis = 10_909),
            pattern = preset.pattern,
            settings = AppSettings(theme = ThemeMode.DARK, animationStyle = AnimationStyle.MINIMAL),
            lastPresetId = preset.id,
        )
    }

    @Test
    fun history() = shot("4_history_dark", ThemeMode.DARK) {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val box = BreathPattern(4, 4, 4, 4)
        val calm = BreathPattern(4, 0, 6, 0)
        val coherent = presets.first { it.id == "coherent" }.pattern
        fun record(daysAgo: Int, hoursAgo: Int, minutes: Int, cycleSeconds: Int, completed: Boolean, name: String, pattern: BreathPattern): SessionRecord {
            val start = now - daysAgo * day - hoursAgo * 3_600_000L
            val active = minutes * 60_000L
            return SessionRecord(start, start + active, active, (active / 1000 / cycleSeconds).toInt(), completed, name, pattern)
        }
        HistoryScreen(
            records = listOf(
                record(0, 1, 5, 16, true, "Box", box),
                record(1, 2, 10, 11, true, "Coherent", coherent),
                record(1, 9, 3, 10, false, "Calm", calm),
                record(2, 3, 10, 16, true, "Box", box),
                record(4, 2, 8, 19, true, "4-7-8", BreathPattern(4, 7, 8, 0)),
                record(5, 10, 10, 11, true, "Coherent", coherent),
                record(8, 1, 15, 16, true, "Box", box),
                record(12, 4, 5, 10, true, "Calm", calm),
            ),
            onDelete = {},
            onClear = {},
            onBack = {},
        )
    }

    @Test
    fun settings() = shot("5_settings_dark", ThemeMode.DARK) {
        SettingsScreen(
            settings = AppSettings(theme = ThemeMode.DARK),
            onSettings = {},
            onRestorePresets = {},
            onBack = {},
        )
    }

    @Composable
    private fun Main(session: SessionState, pattern: BreathPattern, settings: AppSettings, lastPresetId: String) {
        MainScreen(
            session = session,
            pattern = pattern,
            presets = presets,
            settings = settings,
            onToggle = {},
            onStop = {},
            onPattern = {},
            onSettings = {},
            lastPresetId = lastPresetId,
            presetActions = noPresetActions,
            onOpenSettings = {},
            onOpenHistory = {},
        )
    }

    /** A session [elapsed] ms into [phase] of its [cycle]th cycle. */
    private fun running(phase: Phase, elapsed: Long, duration: Long, cycle: Int, cycleMillis: Long): SessionState {
        val now = SystemClock.elapsedRealtime()
        return SessionState(
            status = Status.RUNNING,
            phase = phase,
            phaseStart = now - elapsed,
            phaseDuration = duration,
            cycle = cycle,
            activeMillis = (cycle - 1) * cycleMillis + elapsed,
            snapshotAt = now,
        )
    }

    private fun shot(name: String, theme: ThemeMode, content: @Composable () -> Unit) {
        val dir = File(System.getProperty("breathe.screenshotDir") ?: error("Run with -Pscreenshots"))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            BreatheTheme(theme) {
                val ambience = LocalAmbience.current
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(ambience.top, ambience.bottom)))
                        // Room for the status and navigation bars the real app draws behind.
                        .padding(top = 24.dp, bottom = 16.dp),
                ) { content() }
            }
        }
        // Let colour transitions settle; the session clock itself stays frozen.
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
