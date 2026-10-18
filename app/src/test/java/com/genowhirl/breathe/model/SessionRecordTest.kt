package com.genowhirl.breathe.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class SessionRecordTest {
    private val box = BreathPattern(4, 4, 4, 4)

    @Test
    fun csvHasAHeaderAndOneRowPerSessionOldestFirst() {
        val later = SessionRecord(1_700_000_600_000, 1_700_000_900_000, 300_000, 18, true, "Box", box)
        val earlier = SessionRecord(1_700_000_000_000, 1_700_000_120_500, 120_500, 7, false, "Box", box)
        val lines = SessionCsv.format(listOf(later, earlier), ZoneOffset.UTC).trimEnd().lines()
        assertEquals(3, lines.size)
        assertEquals(SessionCsv.header.joinToString(","), lines[0])
        assertEquals("2023-11-14T22:13:20,2023-11-14T22:15:20,120,7,no,Box,4,4,4,4,seconds,16.00", lines[1])
        assertEquals("2023-11-14T22:23:20,2023-11-14T22:28:20,300,18,yes,Box,4,4,4,4,seconds,16.00", lines[2])
    }

    @Test
    fun csvQuotesNamesWithCommasOrQuotes() {
        val r = SessionRecord(0, 1000, 1000, 1, true, "Calm, \"slow\"", box)
        val row = SessionCsv.format(listOf(r), ZoneOffset.UTC).trimEnd().lines()[1]
        assertEquals(true, row.contains(",\"Calm, \"\"slow\"\"\","))
    }

    @Test
    fun totalsCanBeLimitedToARecentPeriod() {
        val records = listOf(
            SessionRecord(1_000, 2_000, 60_000, 4, true, "Box", box),
            SessionRecord(5_000, 6_000, 120_000, 8, false, "Box", box),
        )
        assertEquals(HistoryTotals(2, 180_000, 12), records.totals())
        assertEquals(HistoryTotals(1, 120_000, 8), records.totals(sinceEpochMs = 3_000))
    }

    @Test
    fun summaryDescribesTheTiming() {
        assertEquals("4-7-8-0, seconds", BreathPattern(4, 7, 8, 0).summary())
        assertEquals("1-4-2-0, 21 s cycle", BuiltInPresets.all.single { it.id == "142" }.pattern.summary())
        assertEquals("4-4-4-4, 0.8 s per count", box.copy(mode = TimingMode.COUNT, countSeconds = 0.8).summary())
    }
}
