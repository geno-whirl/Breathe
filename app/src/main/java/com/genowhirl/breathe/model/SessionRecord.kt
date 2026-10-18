package com.genowhirl.breathe.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One breathing session in the history. */
data class SessionRecord(
    /** Wall-clock start, in epoch milliseconds. */
    val startedAt: Long,
    /** Wall-clock end, in epoch milliseconds. */
    val endedAt: Long,
    /** Breathing time, excluding pauses. */
    val activeMillis: Long,
    /** Whole cycles completed. */
    val cycles: Int,
    /** True when the session reached its set length, false when it was stopped early. */
    val completed: Boolean,
    val presetName: String,
    val pattern: BreathPattern,
)

/** A short description of the pattern's timing, e.g. "4-7-8-0, 16 s cycle". */
fun BreathPattern.summary(): String {
    val counts = "$inhale-$holdIn-$exhale-$holdOut"
    val timing = when (mode) {
        TimingMode.SECONDS -> "seconds"
        TimingMode.COUNT -> "${trim(countSeconds)} s per count"
        TimingMode.CYCLE_LENGTH -> "${trim(cycleSeconds)} s cycle"
        TimingMode.PER_MINUTE -> "${trim(cyclesPerMinute)} per minute"
    }
    return "$counts, $timing"
}

private fun trim(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

object SessionCsv {
    val header = listOf(
        "start", "end", "duration_seconds", "cycles", "completed", "preset",
        "inhale", "hold_in", "exhale", "hold_out", "timing_mode", "cycle_seconds",
    )

    /** The sessions as CSV, oldest first, with local times in ISO 8601. */
    fun format(records: List<SessionRecord>, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(zone)
        fun ts(ms: Long) = time.format(Instant.ofEpochMilli(ms).truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
        val rows = records.sortedBy { it.startedAt }.map { r ->
            listOf(
                ts(r.startedAt),
                ts(r.endedAt),
                (r.activeMillis / 1000).toString(),
                r.cycles.toString(),
                if (r.completed) "yes" else "no",
                r.presetName,
                r.pattern.inhale.toString(),
                r.pattern.holdIn.toString(),
                r.pattern.exhale.toString(),
                r.pattern.holdOut.toString(),
                r.pattern.mode.name.lowercase(),
                "%.2f".format(java.util.Locale.ROOT, r.pattern.cycleMillis / 1000.0),
            )
        }
        return (listOf(header) + rows).joinToString("\n", postfix = "\n") { row -> row.joinToString(",") { escape(it) } }
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}

/** Totals over a list of sessions. */
data class HistoryTotals(val sessions: Int, val activeMillis: Long, val cycles: Int)

fun List<SessionRecord>.totals(sinceEpochMs: Long = Long.MIN_VALUE): HistoryTotals {
    val inRange = filter { it.startedAt >= sinceEpochMs }
    return HistoryTotals(inRange.size, inRange.sumOf { it.activeMillis }, inRange.sumOf { it.cycles })
}
