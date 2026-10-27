package ru.eurovafel.guitartuner.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PitchSmootherTest {

    private fun pitch(frequency: Double, clarity: Double = 0.9, rms: Double = 0.05) =
        PitchResult(frequency = frequency, clarity = clarity, rms = rms)

    @Test
    fun `single outlier does not move median`() {
        val smoother = PitchSmoother()
        repeat(5) { smoother.accept(pitch(82.4)) }

        smoother.accept(pitch(164.8))

        assertEquals(82.4, smoother.smoothedFrequency!!, 0.01)
    }

    @Test
    fun `ema snaps to a different note instead of blending through`() {
        val smoother = PitchSmoother()
        repeat(5) { smoother.accept(pitch(82.4)) }

        repeat(3) { smoother.accept(pitch(110.0)) }
        assertEquals(110.0, smoother.smoothedFrequency!!, 0.1)
    }

    @Test
    fun `octave jumps never leave the ema between octaves`() {
        val smoother = PitchSmoother()
        repeat(5) { smoother.accept(pitch(82.4)) }

        repeat(10) {
            smoother.accept(pitch(41.2))
            smoother.accept(pitch(164.8))
        }

        assertEquals(82.4, smoother.smoothedFrequency!!, 1.0)
    }

    @Test
    fun `real lower octave wins over a harmonic seen first`() {
        val smoother = PitchSmoother()
        smoother.accept(pitch(329.6))
        repeat(4) { smoother.accept(pitch(82.4)) }

        assertEquals(82.4, smoother.smoothedFrequency!!, 0.5)
    }

    @Test
    fun `low clarity frames are gated and reset after silence`() {
        val smoother = PitchSmoother()
        repeat(5) { smoother.accept(pitch(82.4)) }

        assertNotNull(smoother.accept(pitch(500.0, clarity = 0.2)))
        assertNotNull(smoother.accept(pitch(500.0, clarity = 0.2)))
        assertNull(smoother.accept(pitch(500.0, clarity = 0.2)))
    }

    @Test
    fun `missing result resets the history`() {
        val smoother = PitchSmoother()
        repeat(5) { smoother.accept(pitch(82.4)) }

        assertNotNull(smoother.accept(null))
        assertNotNull(smoother.accept(null))
        assertNull(smoother.accept(null))
        assertNull(smoother.smoothedFrequency)
    }

    @Test
    fun `reset clears the smoothed value`() {
        val smoother = PitchSmoother()
        smoother.accept(pitch(82.4))
        smoother.reset()

        assertNull(smoother.smoothedFrequency)
        assertEquals(110.0, smoother.accept(pitch(110.0))!!, 0.01)
    }
}
