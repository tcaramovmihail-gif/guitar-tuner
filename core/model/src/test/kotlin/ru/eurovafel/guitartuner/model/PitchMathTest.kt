package ru.eurovafel.guitartuner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.pow

class PitchMathTest {

    private val a2 = PitchMath.midiToFrequency(45)

    @Test
    fun `third harmonic folds to the same string`() {
        val cents = PitchMath.centsToTarget(a2 * 3.0, a2)
        assertEquals(0.0, cents, 0.5)
        assertEquals(a2, PitchMath.foldToTarget(a2 * 3.0, a2), 0.001)
    }

    @Test
    fun `octave harmonics fold to the same string`() {
        assertEquals(0.0, PitchMath.centsToTarget(a2 * 2.0, a2), 0.5)
        assertEquals(0.0, PitchMath.centsToTarget(a2 * 4.0, a2), 0.5)
        assertEquals(0.0, PitchMath.centsToTarget(a2 / 2.0, a2), 0.5)
    }

    @Test
    fun `detuned string keeps its real offset`() {
        // +17 центов не должны приниматься за гармонику.
        val cents = PitchMath.centsToTarget(a2 * 1.01, a2)
        assertEquals(17.2, cents, 0.5)
    }

    @Test
    fun `detuned third harmonic still folds to the same string`() {
        // Струна на 40 ¢ ниже: 3-я гармоника уехала ровно на те же 40 ¢.
        val flat = 2.0.pow(-40.0 / 1200.0)
        val harmonic = a2 * 3.0 * flat
        assertEquals(3, PitchMath.harmonicOf(harmonic, a2))
        assertEquals(-40.0, PitchMath.centsToTarget(harmonic, a2), 0.5)
    }

    @Test
    fun `harmonic detection tolerates a few cents`() {
        assertEquals(3, PitchMath.harmonicOf(a2 * 3.0 * 1.01, a2))
        // 84 ¢ — типичный детюн 4–6 струны, гармоника всё ещё «своя».
        assertEquals(3, PitchMath.harmonicOf(a2 * 3.0 * 1.05, a2))
        assertNull(PitchMath.harmonicOf(a2 * 1.5, a2))
        assertNull(PitchMath.harmonicOf(a2 * 1.01, a2))
    }

    @Test
    fun `neighbouring open string is not mistaken for a harmonic`() {
        // E2 против D3 — ближайшая негармоническая пара: −200 ¢, за пределом допуска.
        val e2 = PitchMath.midiToFrequency(40)
        val d3 = PitchMath.midiToFrequency(50)
        assertNull(PitchMath.harmonicOf(d3, e2))
    }
}
