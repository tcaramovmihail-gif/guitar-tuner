package ru.eurovafel.guitartuner.audio

import kotlin.math.abs

/**
 * Синтез аккорда перебором: каждая струна звучит со своим сдвигом,
 * как при обычном бое или переборе. Отдельные щипки складываются и
 * нормализуются, чтобы шесть струн не перегружали динамик.
 *
 * Функция чистая и проверяется JVM-тестом.
 */
object ChordSynth {

    fun strum(
        frequencies: List<Double>,
        sampleRate: Int,
        seconds: Double = 2.0,
        strumDelaySeconds: Double = 0.022,
    ): FloatArray {
        require(sampleRate > 0)
        require(seconds > 0.0)
        if (frequencies.isEmpty()) return FloatArray(0)

        val count = (sampleRate * seconds).toInt().coerceAtLeast(1)
        val mix = DoubleArray(count)
        val delaySamples = (sampleRate * strumDelaySeconds).toInt().coerceAtLeast(0)

        frequencies.forEachIndexed { index, frequency ->
            if (frequency <= 0.0) return@forEachIndexed
            val pluck = ToneSynth.pluck(frequency, sampleRate, seconds)
            val offset = index * delaySamples
            val available = (count - offset).coerceAtMost(pluck.size)
            for (sample in 0 until available) {
                mix[offset + sample] += pluck[sample]
            }
        }

        val peak = mix.maxOfOrNull { abs(it) } ?: 0.0
        if (peak <= 0.0) return FloatArray(count)
        val scale = 0.85 / peak
        return FloatArray(count) { index ->
            (mix[index] * scale).toFloat().coerceIn(-1f, 1f)
        }
    }
}
