package ru.eurovafel.guitartuner.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DrumPatternTest {

    @Test
    fun `there are five patterns and each covers a full bar`() {
        assertEquals(5, DrumPattern.entries.size)
        DrumPattern.entries.forEach { pattern ->
            assertEquals(8, pattern.steps.size)
            assertTrue("${pattern.name} has no kick", pattern.steps.any { it.kick })
            assertTrue("${pattern.name} has no snare", pattern.steps.any { it.snare })
        }
    }

    @Test
    fun `rock keeps the backbeat on two and four`() {
        val steps = DrumPattern.ROCK.steps
        assertTrue(steps[0].kick)
        assertTrue(steps[2].snare)
        assertTrue(steps[4].kick)
        assertTrue(steps[6].snare)
    }

    @Test
    fun `step sound is short and does not clip`() {
        val step = DrumSynth.step(48_000, DrumStep(kick = true, snare = true, hat = true))

        assertEquals((48_000 * DrumSynth.STEP_SECONDS).toInt(), step.size)
        assertTrue(step.maxOf { abs(it) } <= 1f)
        assertTrue(step.any { abs(it) > 0.01f })
    }

    @Test
    fun `soft step is quieter than the same loud step`() {
        val loud = DrumSynth.step(48_000, DrumStep(kick = true, hat = true))
        val soft = DrumSynth.step(48_000, DrumStep(kick = true, hat = true, soft = true))

        assertTrue(soft.maxOf { abs(it) } < loud.maxOf { abs(it) })
    }

    @Test
    fun `step fades out instead of cutting the tail`() {
        val step = DrumSynth.step(
            48_000,
            DrumStep(kick = true, snare = true, hat = true),
        )

        // Резкий обрыв хвоста бочки слышен как щелчок на каждой восьмой.
        assertTrue(abs(step.last()) < 0.02f)
        assertTrue(abs(step[step.lastIndex - 40]) < 0.05f)
    }

    @Test
    fun `accent makes the hat louder`() {
        val accent = DrumSynth.step(48_000, DrumStep(hat = true), accent = true)
        val plain = DrumSynth.step(48_000, DrumStep(hat = true))

        assertTrue(accent.maxOf { abs(it) } > plain.maxOf { abs(it) })
    }

    @Test
    fun `hat is quieter than kick in the same step`() {
        val kick = DrumSynth.step(48_000, DrumStep(kick = true))
        val hat = DrumSynth.step(48_000, DrumStep(hat = true))

        assertTrue(hat.maxOf { abs(it) } < kick.maxOf { abs(it) } * 0.5f)
    }
}
