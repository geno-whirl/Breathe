package com.genowhirl.breathe.data

import android.content.Context
import com.genowhirl.breathe.model.SessionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * The history of breathing sessions, stored on the device as one JSON object per line so a
 * new session is a cheap append.
 */
class SessionLog private constructor(context: Context) {
    private val file = File(context.filesDir, "sessions.jsonl")

    private val _records = MutableStateFlow(read())

    /** All sessions, newest first. */
    val records: StateFlow<List<SessionRecord>> = _records.asStateFlow()

    @Synchronized
    fun add(record: SessionRecord) {
        runCatching { file.appendText(toJson(record).toString() + "\n") }
        _records.value = listOf(record) + _records.value
    }

    @Synchronized
    fun delete(record: SessionRecord) {
        val remaining = _records.value.filterNot { it == record }
        rewrite(remaining)
    }

    @Synchronized
    fun clear() = rewrite(emptyList())

    private fun rewrite(newestFirst: List<SessionRecord>) {
        runCatching {
            file.writeText(newestFirst.reversed().joinToString("") { toJson(it).toString() + "\n" })
        }
        _records.value = newestFirst
    }

    private fun read(): List<SessionRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line -> runCatching { fromJson(JSONObject(line)) }.getOrNull() }
                .sortedByDescending { it.startedAt }
        }.getOrDefault(emptyList())
    }

    private fun toJson(r: SessionRecord) = JSONObject()
        .put("startedAt", r.startedAt)
        .put("endedAt", r.endedAt)
        .put("activeMillis", r.activeMillis)
        .put("cycles", r.cycles)
        .put("completed", r.completed)
        .put("preset", r.presetName)
        .put("pattern", patternToJson(r.pattern))

    private fun fromJson(o: JSONObject) = SessionRecord(
        startedAt = o.getLong("startedAt"),
        endedAt = o.getLong("endedAt"),
        activeMillis = o.getLong("activeMillis"),
        cycles = o.getInt("cycles"),
        completed = o.getBoolean("completed"),
        presetName = o.optString("preset"),
        pattern = patternFromJson(o.getJSONObject("pattern")),
    )

    companion object {
        /** Sessions shorter than this (an accidental tap on start) are not kept. */
        const val MIN_ACTIVE_MILLIS = 10_000L

        @Volatile
        private var instance: SessionLog? = null

        fun get(context: Context): SessionLog =
            instance ?: synchronized(this) {
                instance ?: SessionLog(context.applicationContext).also { instance = it }
            }
    }
}
