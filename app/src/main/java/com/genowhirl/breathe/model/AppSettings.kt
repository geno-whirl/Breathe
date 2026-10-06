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

    /** 0 means the session runs until stopped. */
    val sessionMinutes: Int = 0,
    val keepScreenOn: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
)
