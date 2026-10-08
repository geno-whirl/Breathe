# Breathe

A calm, four-stage breathing app for Android: **inhale · hold · exhale · hold**. It keeps guiding you with sound and vibration while the phone is locked, and it has a home-screen widget that does everything the app does.

## Features

**Pattern**
- Four integer stages: inhale, hold, exhale, hold (a stage can be 0 to skip it).
- Three timing modes:
  - **Seconds**: each number is the stage's length in seconds.
  - **Cycle**: the numbers are relative weights and you set the total cycle length.
  - **Per minute**: the numbers are relative weights and you set breaths per minute, which sets the cycle length.
- Built-in presets (Box, 4-7-8, Coherent 5.5/min, Calm 4-6, Triangle, Deep box). You can save your own and long-press a preset to delete it.
- Session length: endless, a number of minutes, or a number of cycles.
- Edits made during a session take effect from the next stage.

**Sound**: Bowl, Chime, Soft, Wood (marimba) or spoken Voice cues. The tones are synthesised on the device like the real instruments (struck, decaying partials with a soft mallet and a small stereo room), in A major pentatonic so every cue sounds good with the others, with a volume setting, an option to cue the holds, and an optional faint tick every second.

**Vibration**
- **Pulses**: a rising double tap to breathe in, a falling one to breathe out, a soft tap for holds. Uses the phone's factory-tuned haptic effects when available.
- **Wave**: soft taps that grow stronger and closer together through the inhale, then fade and slow through the exhale, so you can follow with your eyes closed.
- Adjustable strength.

**Animation**
- **Orb**: grows and shrinks with the breath, inside a ring showing the four stages.
- **Box**: a dot travels around a square while it fills.
- **Ring**: the stage ring only.
- Can be turned off. Natural (eased) or linear pacing, optional countdown.

**Locked phone**: a foreground service with a partial wake lock keeps the cues going with the screen off. The notification shows the stage and a countdown, with Pause/Resume and Stop.

**Widget** (resizable)
- Large size: current stage, countdown, a breath-level progress bar, start/pause, stop, previous/next preset, and sound and vibration toggles.
- Small size: start/pause, the stage and the progress bar.

## Install

Every push builds a debug APK in GitHub Actions. Open the latest **Android** workflow run, download the `breathe-debug-apk` artifact, unzip it and install `app-debug.apk` (allow installs from unknown sources). Builds are signed with the committed `app/debug.keystore`, so a newer build installs over an older one.

To add the widget, long-press the home screen, choose **Widgets**, then **Breathe**.

If sessions stop while the phone is locked, set the app's battery usage to **Unrestricted**. Some manufacturers stop background apps aggressively.

## Build locally

Requires JDK 17 and the Android SDK (API 36).

```
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest     # timing-model unit tests
```

## Code map

| Path | What it does |
|---|---|
| `model/BreathPattern.kt` | Pattern, timing modes, how the cycle is split into stages, presets |
| `model/BreathRunner.kt` | Pure session timekeeping (stages, cycles, pause, session limit) |
| `engine/BreathingService.kt` | Foreground service: runs the session, cues, wake lock, notification, widget updates |
| `audio/` | Tone synthesis, cue playback, text-to-speech, vibration patterns |
| `widget/BreathWidget.kt` | Home-screen widget rendering and its buttons |
| `ui/` | Compose screens, breathing visual, theme |
| `data/Store.kt` | Saved pattern, presets and settings |
