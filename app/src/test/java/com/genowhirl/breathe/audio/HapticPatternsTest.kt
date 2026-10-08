package com.genowhirl.breathe.audio

import com.genowhirl.breathe.model.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPatternsTest {
    @Test
    fun amplitudeStaysWithinWhatMotorsCanRender() {
        assertEquals(HapticPatterns.MIN_AMPLITUDE, HapticPatterns.amplitude(0f, 1f))
        assertEquals(HapticPatterns.MIN_AMPLITUDE, HapticPatterns.amplitude(1f, 0f))
        assertEquals(255, HapticPatterns.amplitude(1f, 1f))
        assertTrue(HapticPatterns.amplitude(0.5f, 0.5f) in HapticPatterns.MIN_AMPLITUDE..255)
    }

    @Test
    fun cuesAreShortTaps() {
        for (cue in Cue.entries) {
            for (vary in listOf(true, false)) {
                val w = HapticPatterns.cue(cue, 0.6f, vary)
                assertEquals(w.timings.size, w.amplitudes.size)
                // Every "on" segment is a tap, never a long buzz.
                w.timings.forEachIndexed { i, t -> if (i % 2 == 1) assertTrue("$cue $t", t <= 130) }
            }
        }
    }

    @Test
    fun inhaleTrainGrowsStrongerAndCloser() {
        val w = HapticPatterns.breathTrain(Phase.INHALE, 4000, 1f, true)!!
        assertTrue(w.durationMs <= 4000)
        val onAmps = w.amplitudes.filterIndexed { i, _ -> i % 2 == 1 }
        val gaps = w.timings.filterIndexed { i, _ -> i % 2 == 0 }.drop(1)
        assertTrue(onAmps.size >= 8)
        assertTrue(onAmps.first() < onAmps.last())
        assertTrue(gaps.first() > gaps.last())
        assertTrue(onAmps.all { it >= HapticPatterns.MIN_AMPLITUDE })
    }

    @Test
    fun exhaleTrainFades() {
        val w = HapticPatterns.breathTrain(Phase.EXHALE, 6000, 0.5f, true)!!
        val onAmps = w.amplitudes.filterIndexed { i, _ -> i % 2 == 1 }
        assertTrue(onAmps.first() > onAmps.last())
        assertTrue(w.durationMs <= 6000)
    }

    @Test
    fun holdsHaveNoTrainAndShortPhasesStillGetATap() {
        assertNull(HapticPatterns.breathTrain(Phase.HOLD_IN, 4000, 1f, true))
        val tiny = HapticPatterns.breathTrain(Phase.INHALE, 50, 1f, false)!!
        assertEquals(2, tiny.timings.size)
    }
}
