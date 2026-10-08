package com.genowhirl.breathe.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathPatternTest {
    @Test
    fun secondsModeUsesUnitsAsSeconds() {
        val p = BreathPattern(4, 7, 8, 0)
        assertEquals(19_000L, p.cycleMillis)
        assertEquals(listOf(4000L, 7000L, 8000L, 0L), p.phaseMillis().toList())
    }

    @Test
    fun cycleLengthModeSplitsProportionally() {
        val p = BreathPattern(1, 2, 3, 4, mode = TimingMode.CYCLE_LENGTH, cycleSeconds = 20.0)
        assertEquals(listOf(2000L, 4000L, 6000L, 8000L), p.phaseMillis().toList())
    }

    @Test
    fun perMinuteModeDerivesCycleLength() {
        val p = BreathPattern(1, 0, 1, 0, mode = TimingMode.PER_MINUTE, cyclesPerMinute = 5.5)
        assertEquals(10_909L, p.cycleMillis)
        val parts = p.phaseMillis()
        assertEquals(p.cycleMillis, parts.sum())
        assertEquals(0L, parts[Phase.HOLD_IN.ordinal])
        assertEquals(0L, parts[Phase.HOLD_OUT.ordinal])
        assertTrue(kotlin.math.abs(parts[0] - parts[2]) <= 1)
    }

    @Test
    fun roundingAlwaysAddsUpToTheCycle() {
        for (cpm in listOf(1.0, 3.5, 7.0, 13.0, 59.5)) {
            val p = BreathPattern(3, 1, 5, 2, mode = TimingMode.PER_MINUTE, cyclesPerMinute = cpm)
            assertEquals(p.cycleMillis, p.phaseMillis().sum())
        }
    }

    @Test
    fun cannotZeroEveryPhase() {
        val p = BreathPattern(1, 0, 0, 0)
        assertEquals(p, p.withUnits(Phase.INHALE, 0))
        assertEquals(5, p.withUnits(Phase.EXHALE, 5).exhale)
    }
}

class BreathRunnerTest {
    @Test
    fun walksThroughPhasesAndCountsCycles() {
        val runner = BreathRunner({ BreathPattern(2, 1, 2, 1) })
        val start = runner.start(1000)
        assertEquals(RunnerEvent.PhaseStarted(Phase.INHALE, 2000, 1), start.single())

        assertTrue(runner.update(2999).isEmpty())
        assertEquals(RunnerEvent.PhaseStarted(Phase.HOLD_IN, 1000, 1), runner.update(3000).single())
        assertEquals(RunnerEvent.PhaseStarted(Phase.EXHALE, 2000, 1), runner.update(4000).single())
        assertEquals(RunnerEvent.PhaseStarted(Phase.HOLD_OUT, 1000, 1), runner.update(6000).single())
        assertEquals(RunnerEvent.PhaseStarted(Phase.INHALE, 2000, 2), runner.update(7000).single())
    }

    @Test
    fun skipsEmptyPhases() {
        val runner = BreathRunner({ BreathPattern(4, 7, 8, 0) })
        runner.start(0)
        runner.update(4000)
        runner.update(11_000)
        val wrap = runner.update(19_000).single() as RunnerEvent.PhaseStarted
        assertEquals(Phase.INHALE, wrap.phase)
        assertEquals(2, wrap.cycle)
    }

    @Test
    fun pauseShiftsTheTimeline() {
        val runner = BreathRunner({ BreathPattern(4, 0, 4, 0) })
        runner.start(0)
        runner.pause(1000)
        assertTrue(runner.update(10_000).isEmpty())
        assertEquals(1000L, runner.activeMillis(10_000))
        runner.resume(10_000)
        assertTrue(runner.update(12_999).isEmpty())
        assertEquals(Phase.EXHALE, (runner.update(13_000).single() as RunnerEvent.PhaseStarted).phase)
    }

    @Test
    fun finishesAtTheEndOfTheCycleThatReachesTheLimit() {
        val runner = BreathRunner({ BreathPattern(3, 0, 3, 0) }, { SessionLimit(millis = 10_000L) })
        runner.start(0)
        val events = runner.update(12_000)
        // Cycle 1 ends at 6 s (below the limit), cycle 2 ends at 12 s (limit reached).
        assertEquals(RunnerEvent.Finished, events.last())
        assertTrue(runner.isFinished)
    }

    @Test
    fun finishesAfterTheRequestedNumberOfCycles() {
        val runner = BreathRunner({ BreathPattern(2, 0, 2, 0) }, { SessionLimit(cycles = 3) })
        runner.start(0)
        assertTrue(runner.update(11_999).none { it is RunnerEvent.Finished })
        assertEquals(3, runner.cycle)
        assertEquals(RunnerEvent.Finished, runner.update(12_000).last())
        assertEquals(12_000L, runner.activeMillis(12_000))
    }

    @Test
    fun sessionLengthSettingMapsToALimit() {
        assertEquals(SessionLimit(), AppSettings().sessionLimit)
        assertEquals(SessionLimit(millis = 300_000L), AppSettings(sessionLength = SessionLength.MINUTES, sessionMinutes = 5).sessionLimit)
        assertEquals(SessionLimit(cycles = 8), AppSettings(sessionLength = SessionLength.CYCLES, sessionCycles = 8).sessionLimit)
    }

    @Test
    fun patternChangesApplyFromTheNextPhase() {
        var pattern = BreathPattern(4, 0, 4, 0)
        val runner = BreathRunner({ pattern })
        runner.start(0)
        pattern = BreathPattern(2, 0, 6, 0)
        val next = runner.update(4000).single() as RunnerEvent.PhaseStarted
        assertEquals(Phase.EXHALE, next.phase)
        assertEquals(6000L, next.durationMs)
    }

    @Test
    fun breathLevelFollowsTheLungs() {
        assertEquals(0f, breathLevel(Phase.INHALE, 0f), 1e-6f)
        assertEquals(1f, breathLevel(Phase.INHALE, 1f), 1e-6f)
        assertEquals(0.5f, breathLevel(Phase.EXHALE, 0.5f), 1e-6f)
        assertEquals(1f, breathLevel(Phase.HOLD_IN, 0.3f), 1e-6f)
        assertEquals(0f, breathLevel(Phase.HOLD_OUT, 0.3f), 1e-6f)
    }
}
