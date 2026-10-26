package ru.eurovafel.guitartuner.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InTuneHoldTest {

    private fun inTune(hold: InTuneHold, frames: Int) {
        repeat(frames) { hold.accept(1.0) }
    }

    @Test
    fun `celebrates after the hold window`() {
        val hold = InTuneHold(framesToCelebrate = 30)
        inTune(hold, 29)
        assertFalse(hold.celebrating)
        inTune(hold, 1)
        assertTrue(hold.celebrating)
    }

    @Test
    fun `decay and silence do not reset progress`() {
        val hold = InTuneHold(framesToCelebrate = 30)
        inTune(hold, 20)
        repeat(50) { hold.accept(null) }
        inTune(hold, 9)
        assertFalse(hold.celebrating)
        inTune(hold, 1)
        assertTrue(hold.celebrating)
    }

    @Test
    fun `celebration survives short silence`() {
        val hold = InTuneHold(framesToCelebrate = 30)
        inTune(hold, 30)
        repeat(100) { hold.accept(null) }
        assertTrue(hold.celebrating)
    }

    @Test
    fun `single sharp frame from a pluck does not reset`() {
        val hold = InTuneHold(framesToCelebrate = 30, exitFrames = 3)
        inTune(hold, 30)
        repeat(2) { hold.accept(18.0) }
        assertTrue(hold.celebrating)
    }

    @Test
    fun `sustained miss resets the progress`() {
        val hold = InTuneHold(framesToCelebrate = 30, exitFrames = 3)
        inTune(hold, 30)
        assertTrue(hold.celebrating)
        repeat(3) { hold.accept(18.0) }
        assertFalse(hold.celebrating)
    }

    @Test
    fun `small detune near the edge neither counts nor resets`() {
        val hold = InTuneHold(framesToCelebrate = 30)
        inTune(hold, 20)
        // 8.5 ¢ — между «в строю» (8) и сбросом (9), зазор 1 ¢.
        repeat(20) { hold.accept(8.5) }
        inTune(hold, 10)
        assertTrue(hold.celebrating)
    }

    @Test
    fun `manual reset clears the celebration`() {
        val hold = InTuneHold(framesToCelebrate = 5)
        inTune(hold, 5)
        assertTrue(hold.celebrating)
        hold.reset()
        assertFalse(hold.celebrating)
        inTune(hold, 4)
        assertFalse(hold.celebrating)
        inTune(hold, 1)
        assertTrue(hold.celebrating)
    }
}
