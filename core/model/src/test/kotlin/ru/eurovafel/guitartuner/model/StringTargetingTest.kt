package ru.eurovafel.guitartuner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.pow

class StringTargetingTest {

    private val guitar = InstrumentTuning.GUITAR_STANDARD.midiNotes
    private val e2 = PitchMath.midiToFrequency(40)
    private val a2 = PitchMath.midiToFrequency(45)
    private val g3 = PitchMath.midiToFrequency(55)
    private val e4 = PitchMath.midiToFrequency(64)

    @Test
    fun `third harmonic of A2 does not switch to the first string`() {
        // 3-я гармоника A2 (110 Гц) — 330 Гц, это открытая E4. Цель должна остаться A2.
        val a3rd = a2 * 3.0
        assertEquals(45, StringTargeting.resolve(guitar, a3rd, active = 45))
        assertEquals(45, StringTargeting.hint(guitar, a3rd, active = 45))
    }

    @Test
    fun `third harmonic of E2 does not switch to the second string`() {
        val e3rd = PitchMath.midiToFrequency(40) * 3.0
        assertEquals(40, StringTargeting.resolve(guitar, e3rd, active = 40))
    }

    @Test
    fun `active target does not jump to another octave of the same note`() {
        // Настраиваем G3, детектор поймал A3 (обертон/перебор) — цель остаётся G3.
        assertEquals(55, StringTargeting.resolve(guitar, 220.0, active = 55))
    }

    @Test
    fun `active target survives a detune within a tone`() {
        assertEquals(55, StringTargeting.resolve(guitar, g3 * 1.12, active = 55))
        assertEquals(55, StringTargeting.resolve(guitar, g3 * 0.9, active = 55))
    }

    @Test
    fun `plucking another open string switches the target`() {
        assertEquals(45, StringTargeting.resolve(guitar, a2, active = 40))
        assertEquals(50, StringTargeting.resolve(guitar, PitchMath.midiToFrequency(50), active = 40))
    }

    @Test
    fun `fourth harmonic of E2 does not switch to the first string`() {
        // 4-я гармоника E2 — открытая E4. Цель остаётся E2: та же нота,
        // центы считаются по гармонике, а цель не скачет.
        assertEquals(40, StringTargeting.resolve(guitar, e4, active = 40))
        assertEquals(40, StringTargeting.hint(guitar, e4, active = 40))
    }

    @Test
    fun `target is frozen while a non open note sounds`() {
        assertEquals(55, StringTargeting.resolve(guitar, 300.0, active = 55))
    }

    @Test
    fun `first acquisition tolerates an octave error`() {
        // Детектор отдал E3 вместо E2: та же нота через октаву.
        assertEquals(40, StringTargeting.resolve(guitar, PitchMath.midiToFrequency(52)))

        assertEquals(40, StringTargeting.resolve(guitar, e2))
        assertEquals(45, StringTargeting.resolve(guitar, a2))
    }

    @Test
    fun `detuned harmonic does not switch the target`() {
        // 4–6 струны часто на 30–50 ¢ ниже: 3-я гармоника уезжает вместе с ними,
        // но остаётся гармоникой активной струны.
        val flat = 2.0.pow(-40.0 / 1200.0)
        assertEquals(45, StringTargeting.resolve(guitar, a2 * 3.0 * flat, active = 45))
        assertEquals(40, StringTargeting.resolve(guitar, e2 * 3.0 * flat, active = 40))
        assertEquals(45, StringTargeting.hint(guitar, a2 * 3.0 * flat, active = 45))
    }

    @Test
    fun `hint only points at a real open string`() {
        assertEquals(40, StringTargeting.hint(guitar, e2))
        assertEquals(45, StringTargeting.hint(guitar, a2))
        assertNull(StringTargeting.hint(guitar, 220.0))
    }
}
