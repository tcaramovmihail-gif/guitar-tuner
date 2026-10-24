package ru.eurovafel.guitartuner.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.eurovafel.guitartuner.model.PitchMath
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class YinPitchDetectorTest {

    private val sampleRate = 48_000
    private val detector = YinPitchDetector(sampleRate)
    private val windowSize = 4096

    @Test
    fun `standard guitar strings are detected within three cents`() {
        val standardTuning = listOf(40, 45, 50, 55, 59, 64) // E2 A2 D3 G3 B3 E4

        for (midi in standardTuning) {
            val frequency = PitchMath.midiToFrequency(midi)
            for (detuneCents in listOf(-15.0, 0.0, 12.0)) {
                val signal = pluckedString(frequency, detuneCents)
                val window = signal.copyOfRange(
                    (sampleRate * 0.12).toInt(),
                    (sampleRate * 0.12).toInt() + windowSize,
                )

                val result = detector.detect(window)
                val message = "midi=$midi detune=$detuneCents"
                assertNotNull(message, result)

                val reading = PitchMath.reading(result!!.frequency)
                assertEquals(message, midi, reading.midi)
                assertEquals(message, detuneCents, reading.cents, 3.0)
            }
        }
    }

    @Test
    fun `silence and white noise are rejected`() {
        assertNull(detector.detect(FloatArray(windowSize)))

        val random = Random(42)
        val noise = FloatArray(windowSize) {
            ((random.nextDouble() - 0.5) * 0.2).toFloat()
        }
        assertNull(detector.detect(noise))
    }

    @Test
    fun `reference pitch a4 442 shifts target frequency`() {
        val reading = PitchMath.reading(442.0, a4 = 442.0)
        assertEquals(69, reading.midi)
        assertEquals(0.0, reading.cents, 0.001)
        assertEquals(442.0, reading.targetFrequency, 0.001)
    }

    @Test
    fun `weak fundamental with strong fourth harmonic is resolved to low e`() {
        // Так выглядит низкая E2 у микрофона телефона: основной тон слабый,
        // а четвёртая гармоника (E4) заметно сильнее.
        val signal = harmonicSignal(
            frequency = PitchMath.midiToFrequency(40),
            amplitudes = doubleArrayOf(0.03, 0.08, 0.15, 0.50, 0.15, 0.08, 0.04, 0.02),
        )
        val window = signal.copyOfRange(
            (sampleRate * 0.12).toInt(),
            (sampleRate * 0.12).toInt() + windowSize,
        )

        val result = detector.detect(window)
        assertNotNull(result)
        assertEquals(40, PitchMath.reading(result!!.frequency).midi)
    }

    @Test
    fun `strong second harmonic is resolved to the fundamental`() {
        val signal = harmonicSignal(
            frequency = PitchMath.midiToFrequency(45),
            amplitudes = doubleArrayOf(0.04, 0.50, 0.15, 0.10, 0.06, 0.03, 0.02, 0.01),
        )
        val window = signal.copyOfRange(
            (sampleRate * 0.12).toInt(),
            (sampleRate * 0.12).toInt() + windowSize,
        )

        val result = detector.detect(window)
        assertNotNull(result)
        assertEquals(45, PitchMath.reading(result!!.frequency).midi)
    }

    @Test
    fun `pure high e is not folded down an octave`() {
        val signal = harmonicSignal(
            frequency = PitchMath.midiToFrequency(64),
            amplitudes = doubleArrayOf(0.50, 0.20, 0.10, 0.05, 0.03, 0.02, 0.01, 0.005),
        )
        val window = signal.copyOfRange(
            (sampleRate * 0.12).toInt(),
            (sampleRate * 0.12).toInt() + windowSize,
        )

        val result = detector.detect(window)
        assertNotNull(result)
        assertEquals(64, PitchMath.reading(result!!.frequency).midi)
    }

    /** Синтетический тон с заданными амплитудами гармоник 1..N. */
    private fun harmonicSignal(
        frequency: Double,
        amplitudes: DoubleArray,
        seconds: Double = 0.5,
    ): FloatArray {
        val size = (sampleRate * seconds).toInt()
        val output = FloatArray(size)
        for (i in 0 until size) {
            val time = i.toDouble() / sampleRate
            var value = 0.0
            amplitudes.forEachIndexed { index, amplitude ->
                value += amplitude * sin(2.0 * PI * frequency * (index + 1) * time)
            }
            val envelope = exp(-2.5 * time) * min(1.0, time / 0.01)
            output[i] = (value * 0.2 * envelope).toFloat()
        }
        return output
    }

    /** Синтетическая щипковая струна: гармоники 1..8, спад и немного шума. */
    private fun pluckedString(
        frequency: Double,
        detuneCents: Double,
        seconds: Double = 0.5,
    ): FloatArray {
        val target = frequency * 2.0.pow(detuneCents / 1200.0)
        val size = (sampleRate * seconds).toInt()
        val random = Random(7)
        val phases = DoubleArray(8) { random.nextDouble() * 2.0 * PI }
        val output = FloatArray(size)

        for (i in 0 until size) {
            val time = i.toDouble() / sampleRate
            var value = 0.0
            for (harmonic in 1..8) {
                value += sin(2.0 * PI * target * harmonic * time + phases[harmonic - 1]) / harmonic
            }
            val envelope = exp(-2.5 * time) * min(1.0, time / 0.01)
            val noise = (random.nextDouble() - 0.5) * 0.001
            output[i] = (value * 0.2 * envelope + noise).toFloat()
        }
        return output
    }
}
