package ru.eurovafel.guitartuner.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import ru.eurovafel.guitartuner.model.InstrumentTuning
import ru.eurovafel.guitartuner.model.PitchMath
import ru.eurovafel.guitartuner.model.StringTargeting
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Диагностика захвата низких струн: микрофон телефона давит основной тон,
 * и детектор может отдать 2-ю/3-ю/4-ю гармонику. Проверяем, что цель всё
 * равно определяется как настоящая струна.
 */
class LowStringAcquisitionTest {

    private val sampleRate = 48_000
    private val detector = YinPitchDetector(sampleRate)
    private val windowSize = 4096
    private val openStrings = InstrumentTuning.GUITAR_STANDARD.midiNotes

    @Test
    fun `low strings with weak fundamental acquire the right target`() {
        val failures = mutableListOf<String>()
        for (midi in listOf(40, 45, 50, 55)) {
            val frequency = PitchMath.midiToFrequency(midi)
            for (cutoff in listOf(120.0, 180.0, 240.0)) {
                val window = window(harmonicSignal(frequency, cutoff))
                val result = detector.detect(window)
                if (result == null) {
                    failures += "midi=$midi cutoff=$cutoff: detector returned null"
                    continue
                }
                val reading = PitchMath.reading(result.frequency)
                val target = StringTargeting.resolve(openStrings, result.frequency)
                println(
                    "midi=$midi cutoff=$cutoff f=%.2f note=%s clar=%.3f target=%s".format(
                        result.frequency, reading.fullName, result.clarity, target,
                    ),
                )
                if (target != midi) {
                    failures += "midi=$midi cutoff=$cutoff: f=${result.frequency} " +
                        "note=${reading.fullName} target=$target"
                }
            }
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun `detector frequency for low e is close to e2 with weak fundamental`() {
        val signal = harmonicSignal(PitchMath.midiToFrequency(40), cutoff = 180.0)
        val result = detector.detect(window(signal))
        assertNotNull(result)
        assertEquals(82.41, result!!.frequency, 1.0)
    }

    private fun window(signal: FloatArray): FloatArray {
        val start = (sampleRate * 0.12).toInt()
        return signal.copyOfRange(start, start + windowSize)
    }

    /** Струна с гармониками 1..10 и спадом АЧХ микрофона (ФВЧ 2-го порядка). */
    private fun harmonicSignal(frequency: Double, cutoff: Double, seconds: Double = 0.5): FloatArray {
        val random = Random(11)
        val phases = DoubleArray(10) { random.nextDouble() * 2.0 * PI }
        val size = (sampleRate * seconds).toInt()
        val output = FloatArray(size)
        for (i in 0 until size) {
            val time = i.toDouble() / sampleRate
            var value = 0.0
            for (harmonic in 1..10) {
                val f = frequency * harmonic
                val hp = (f / cutoff) * (f / cutoff) / sqrt(1.0 + (f / cutoff).pow4())
                value += hp / harmonic * sin(2.0 * PI * f * time + phases[harmonic - 1])
            }
            val envelope = exp(-2.5 * time) * min(1.0, time / 0.01)
            val noise = (random.nextDouble() - 0.5) * 0.002
            output[i] = (value * 0.15 * envelope + noise).toFloat()
        }
        return output
    }

    private fun Double.pow4(): Double = this * this * this * this
}
