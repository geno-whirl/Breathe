package com.genowhirl.breathe.model

enum class SoundStyle { BOWL, CHIME, SOFT, WOOD, VOICE }

enum class VibrationMode {
    /** A short pattern at the start of each stage. */
    CUES,

    /** Vibration that swells during the inhale and fades during the exhale. */
    WAVE,
}

enum class AnimationStyle { ORB, BOX, MINIMAL }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How long a session lasts. */
enum class SessionLength { ENDLESS, MINUTES, CYCLES }

data class AppSettings(
    val soundEnabled: Boolean = true,
    val soundStyle: SoundStyle = SoundStyle.BOWL,
    val soundVolume: Float = 0.7f,
    val soundOnHolds: Boolean = true,
    val tickSeconds: Boolean = false,

    val vibrationEnabled: Boolean = true,
    val vibrationMode: VibrationMode = VibrationMode.CUES,
    val vibrationStrength: Float = 0.6f,
    val vibrationOnHolds: Boolean = true,

    val animationEnabled: Boolean = true,
    val animationStyle: AnimationStyle = AnimationStyle.ORB,
    val naturalEasing: Boolean = true,
    val showCountdown: Boolean = true,

    val sessionLength: SessionLength = SessionLength.ENDLESS,
    val sessionMinutes: Int = 10,
    val sessionCycles: Int = 20,
    val keepScreenOn: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

/** The end condition the runner checks at the end of every cycle; zero means no limit. */
data class SessionLimit(val millis: Long = 0L, val cycles: Int = 0)

val AppSettings.sessionLimit: SessionLimit
    get() = when (sessionLength) {
        SessionLength.ENDLESS -> SessionLimit()
        SessionLength.MINUTES -> SessionLimit(millis = sessionMinutes * 60_000L)
        SessionLength.CYCLES -> SessionLimit(cycles = sessionCycles)
    }
