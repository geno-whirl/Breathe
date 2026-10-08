package com.genowhirl.breathe.data

import android.content.Context
import android.content.SharedPreferences
import com.genowhirl.breathe.model.AppSettings
import com.genowhirl.breathe.model.BreathPattern
import com.genowhirl.breathe.model.BuiltInPresets
import com.genowhirl.breathe.model.Preset
import com.genowhirl.breathe.model.SessionLength
import com.genowhirl.breathe.widget.BreathWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Process-wide holder of the user's pattern, presets and settings, persisted in
 * SharedPreferences so the widget receiver can read them synchronously.
 */
class Store private constructor(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("breathe", Context.MODE_PRIVATE)

    private val _pattern = MutableStateFlow(readPattern())
    val pattern: StateFlow<BreathPattern> = _pattern.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _presets = MutableStateFlow(readPresets())
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    fun updatePattern(transform: (BreathPattern) -> BreathPattern) {
        val updated = transform(_pattern.value)
        if (updated == _pattern.value) return
        _pattern.value = updated
        prefs.edit().putString(KEY_PATTERN, patternToJson(updated).toString()).apply()
        BreathWidget.refresh(context)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        prefs.edit().putString(KEY_SETTINGS, settingsToJson(updated).toString()).apply()
        BreathWidget.refresh(context)
    }

    /** The preset whose rhythm matches the current pattern, if any. */
    fun matchingPreset(pattern: BreathPattern = _pattern.value): Preset? =
        _presets.value.firstOrNull { it.pattern.sameRhythmAs(pattern) }

    fun applyPreset(preset: Preset) = updatePattern { preset.pattern }

    /** Moves to the previous (-1) or next (+1) preset, wrapping around. */
    fun cyclePreset(delta: Int) {
        val list = _presets.value
        if (list.isEmpty()) return
        val current = list.indexOfFirst { it.pattern.sameRhythmAs(_pattern.value) }
        val index = when {
            current >= 0 -> Math.floorMod(current + delta, list.size)
            delta > 0 -> 0
            else -> list.lastIndex
        }
        applyPreset(list[index])
    }

    fun savePreset(name: String): Preset {
        val preset = Preset(UUID.randomUUID().toString(), name.trim().ifEmpty { "My pattern" }, _pattern.value)
        setPresets(_presets.value + preset)
        return preset
    }

    fun deletePreset(id: String) = setPresets(_presets.value.filterNot { it.id == id })

    fun restoreBuiltInPresets() {
        val existing = _presets.value.map { it.id }.toSet()
        setPresets(BuiltInPresets.all.filterNot { it.id in existing } + _presets.value)
    }

    private fun setPresets(list: List<Preset>) {
        _presets.value = list
        val array = JSONArray()
        list.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("builtIn", p.builtIn)
                    .put("pattern", patternToJson(p.pattern)),
            )
        }
        prefs.edit().putString(KEY_PRESETS, array.toString()).apply()
        BreathWidget.refresh(context)
    }

    private fun readPattern(): BreathPattern =
        prefs.getString(KEY_PATTERN, null)?.let { runCatching { patternFromJson(JSONObject(it)) }.getOrNull() }
            ?: BuiltInPresets.all.first().pattern

    private fun readPresets(): List<Preset> {
        val raw = prefs.getString(KEY_PRESETS, null) ?: return BuiltInPresets.all
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Preset(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    pattern = patternFromJson(o.getJSONObject("pattern")),
                    builtIn = o.optBoolean("builtIn"),
                )
            }
        }.getOrElse { BuiltInPresets.all }
    }

    private fun readSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching {
            val o = JSONObject(raw)
            val d = AppSettings()
            AppSettings(
                soundEnabled = o.optBoolean("soundEnabled", d.soundEnabled),
                soundStyle = enumOr(o.optString("soundStyle"), d.soundStyle),
                soundVolume = o.optDouble("soundVolume", d.soundVolume.toDouble()).toFloat(),
                soundOnHolds = o.optBoolean("soundOnHolds", d.soundOnHolds),
                tickSeconds = o.optBoolean("tickSeconds", d.tickSeconds),
                vibrationEnabled = o.optBoolean("vibrationEnabled", d.vibrationEnabled),
                vibrationMode = enumOr(o.optString("vibrationMode"), d.vibrationMode),
                vibrationStrength = o.optDouble("vibrationStrength", d.vibrationStrength.toDouble()).toFloat(),
                vibrationOnHolds = o.optBoolean("vibrationOnHolds", d.vibrationOnHolds),
                animationEnabled = o.optBoolean("animationEnabled", d.animationEnabled),
                animationStyle = enumOr(o.optString("animationStyle"), d.animationStyle),
                naturalEasing = o.optBoolean("naturalEasing", d.naturalEasing),
                showCountdown = o.optBoolean("showCountdown", d.showCountdown),
                // Older versions stored only minutes, with 0 meaning endless.
                sessionLength = if (o.has("sessionLength")) {
                    enumOr(o.optString("sessionLength"), d.sessionLength)
                } else if (o.optInt("sessionMinutes", 0) > 0) {
                    SessionLength.MINUTES
                } else {
                    SessionLength.ENDLESS
                },
                sessionMinutes = o.optInt("sessionMinutes", d.sessionMinutes).takeIf { it > 0 } ?: d.sessionMinutes,
                sessionCycles = o.optInt("sessionCycles", d.sessionCycles).takeIf { it > 0 } ?: d.sessionCycles,
                keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
                theme = enumOr(o.optString("theme"), d.theme),
            )
        }.getOrElse { AppSettings() }
    }

    private fun settingsToJson(s: AppSettings) = JSONObject()
        .put("soundEnabled", s.soundEnabled)
        .put("soundStyle", s.soundStyle.name)
        .put("soundVolume", s.soundVolume.toDouble())
        .put("soundOnHolds", s.soundOnHolds)
        .put("tickSeconds", s.tickSeconds)
        .put("vibrationEnabled", s.vibrationEnabled)
        .put("vibrationMode", s.vibrationMode.name)
        .put("vibrationStrength", s.vibrationStrength.toDouble())
        .put("vibrationOnHolds", s.vibrationOnHolds)
        .put("animationEnabled", s.animationEnabled)
        .put("animationStyle", s.animationStyle.name)
        .put("naturalEasing", s.naturalEasing)
        .put("showCountdown", s.showCountdown)
        .put("sessionLength", s.sessionLength.name)
        .put("sessionMinutes", s.sessionMinutes)
        .put("sessionCycles", s.sessionCycles)
        .put("keepScreenOn", s.keepScreenOn)
        .put("theme", s.theme.name)

    private fun patternToJson(p: BreathPattern) = JSONObject()
        .put("inhale", p.inhale)
        .put("holdIn", p.holdIn)
        .put("exhale", p.exhale)
        .put("holdOut", p.holdOut)
        .put("mode", p.mode.name)
        .put("cycleSeconds", p.cycleSeconds)
        .put("cyclesPerMinute", p.cyclesPerMinute)

    private fun patternFromJson(o: JSONObject): BreathPattern {
        val d = BreathPattern()
        val p = BreathPattern(
            inhale = o.optInt("inhale", d.inhale),
            holdIn = o.optInt("holdIn", d.holdIn),
            exhale = o.optInt("exhale", d.exhale),
            holdOut = o.optInt("holdOut", d.holdOut),
            mode = enumOr(o.optString("mode"), d.mode),
            cycleSeconds = o.optDouble("cycleSeconds", d.cycleSeconds),
            cyclesPerMinute = o.optDouble("cyclesPerMinute", d.cyclesPerMinute),
        )
        return if (p.unitSum == 0) d else p
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    companion object {
        private const val KEY_PATTERN = "pattern"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_PRESETS = "presets"

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}
