package ru.eurovafel.guitartuner.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChordSynthTest {

    @Test
    fun `strum has the requested length and does not clip`() {
        val samples = ChordSynth.strum(
            frequencies = listOf(110.0, 164.81, 220.0, 277.18, 329.63),
            sampleRate = 48_000,
            seconds = 1.0,
        )

        assertEquals(48_000, samples.size)
        assertTrue(samples.any { abs(it) > 0.05f })
        assertTrue(samples.maxOf { abs(it) } <= 0.86f)
    }

    @Test
    fun `empty chord returns silence`() {
        val samples = ChordSynth.strum(emptyList(), 48_000, 0.5)

        assertTrue(samples.isEmpty())
    }

    @Test
    fun `later strings start after the first one`() {
        val samples = ChordSynth.strum(
            frequencies = listOf(82.41, 110.0),
            sampleRate = 48_000,
            seconds = 0.5,
            strumDelaySeconds = 0.02,
        )

        // Первая струна уже звучит, а вторая вступает через 20 мс: после
        // вступления энергии становится больше, как при реальном переборе.
        val beforeSecond = samples.take(900).sumOf { abs(it.toDouble()) }
        val afterSecond = samples.drop(1_000).take(900).sumOf { abs(it.toDouble()) }
        assertTrue(beforeSecond > 0.0)
        assertTrue(afterSecond > beforeSecond)
    }
}
