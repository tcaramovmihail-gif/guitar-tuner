package ru.eurovafel.guitartuner.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DrumSynthTest {

    private val sampleRate = 48_000

    @Test
    fun `every beat contains audible samples`() {
        for (index in 0..3) {
            val samples = DrumSynth.beat(sampleRate, index)

            assertEquals((sampleRate * DrumSynth.BEAT_SECONDS).toInt(), samples.size)
            assertTrue(samples.any { abs(it) > 0.01f })
            assertTrue(samples.maxOf { abs(it) } <= 1f)
        }
    }

    @Test
    fun `kick and snare have different character`() {
        val kick = DrumSynth.kick(sampleRate)
        val snare = DrumSynth.snare(sampleRate)

        assertTrue(kick.any { it > 0.1f })
        assertTrue(snare.any { it > 0.1f })
        assertTrue(kick.maxOf { abs(it) } <= 1f)
        assertTrue(snare.maxOf { abs(it) } <= 1f)
    }
}
