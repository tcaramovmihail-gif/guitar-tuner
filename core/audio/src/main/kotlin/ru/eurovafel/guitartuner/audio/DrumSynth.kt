package ru.eurovafel.guitartuner.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Синтез простой барабанной партии без сэмплов.
 *
 * На одну долю приходится либо бочка, либо малый барабан, плюс тихая
 * тарелка. Получается понятный backbeat: бочка — 1 и 3, малый — 2 и 4.
 * Функции чистые, чтобы партию можно было проверить JVM-тестом.
 */
object DrumSynth {
    const val BEAT_SECONDS = 0.18

    fun beat(sampleRate: Int, index: Int): FloatArray {
        require(sampleRate > 0)
        val length = (sampleRate * BEAT_SECONDS).toInt().coerceAtLeast(1)
        val mix = FloatArray(length)

        when (((index % 4) + 4) % 4) {
            0 -> add(mix, kick(sampleRate))
            1 -> add(mix, snare(sampleRate))
            2 -> add(mix, kick(sampleRate))
            3 -> add(mix, snare(sampleRate))
        }
        add(mix, hat(sampleRate))

        normalize(mix)
        return mix
    }

    fun kick(sampleRate: Int): FloatArray {
        require(sampleRate > 0)
        val length = (sampleRate * 0.18).toInt().coerceAtLeast(1)
        var phase = 0.0

        return FloatArray(length) { index ->
            val time = index.toDouble() / sampleRate
            // Быстрый спад частоты даёт характерный «удар в грудь».
            val frequency = 48.0 + 95.0 * exp(-time * 28.0)
            phase += 2.0 * PI * frequency / sampleRate
            val body = sin(phase) * exp(-time * 18.0)
            val click = sin(2.0 * PI * 1_300.0 * time) * exp(-time * 90.0) * 0.14
            ((body + click) * 0.9).toFloat().coerceIn(-1f, 1f)
        }
    }

    fun snare(sampleRate: Int): FloatArray {
        require(sampleRate > 0)
        val length = (sampleRate * 0.18).toInt().coerceAtLeast(1)
        val random = Random(0x5EED)
        var previousNoise = 0.0

        return FloatArray(length) { index ->
            val time = index.toDouble() / sampleRate
            val noise = random.nextDouble(-1.0, 1.0)
            // Разность соседних отсчётов убирает низы и оставляет «песок».
            val highNoise = noise - previousNoise * 0.85
            previousNoise = noise
            val body = sin(2.0 * PI * 185.0 * time) * exp(-time * 35.0) * 0.45
            val envelope = exp(-time * 26.0)
            ((highNoise * 0.55 * envelope + body) * 0.9)
                .toFloat()
                .coerceIn(-1f, 1f)
        }
    }

    fun hat(sampleRate: Int): FloatArray {
        require(sampleRate > 0)
        val length = (sampleRate * 0.07).toInt().coerceAtLeast(1)
        val random = Random(0x0A7)
        var previousNoise = 0.0

        return FloatArray(length) { index ->
            val time = index.toDouble() / sampleRate
            val noise = random.nextDouble(-1.0, 1.0)
            val highNoise = noise - previousNoise
            previousNoise = noise
            (highNoise * exp(-time * 70.0) * 0.30)
                .toFloat()
                .coerceIn(-1f, 1f)
        }
    }

    private fun add(target: FloatArray, source: FloatArray) {
        val count = minOf(target.size, source.size)
        for (index in 0 until count) {
            target[index] += source[index]
        }
    }

    private fun normalize(samples: FloatArray) {
        val peak = samples.maxOfOrNull { abs(it) } ?: return
        if (peak <= 0.9f) {
            for (index in samples.indices) {
                samples[index] = samples[index].coerceIn(-1f, 1f)
            }
            return
        }

        val scale = 0.9f / peak
        for (index in samples.indices) {
            samples[index] = (samples[index] * scale).coerceIn(-1f, 1f)
        }
    }
}
