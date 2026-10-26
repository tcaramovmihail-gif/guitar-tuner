package ru.eurovafel.guitartuner.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneSynthTest {

    @Test
    fun `reference tone is audible and does not clip`() {
        val samples = ToneSynth.pluck(frequency = 110.0, sampleRate = 48_000, seconds = 0.5)

        assertEquals(24_000, samples.size)
        assertTrue(samples.any { abs(it) > 0.05f })
        assertTrue(samples.maxOf { abs(it) } <= 0.56f)
    }

    @Test
    fun `tone starts and ends near silence`() {
        val samples = ToneSynth.pluck(frequency = 440.0, sampleRate = 48_000, seconds = 0.5)

        assertTrue(abs(samples.first()) < 0.01f)
        assertTrue(abs(samples.last()) < 0.01f)
    }
}
