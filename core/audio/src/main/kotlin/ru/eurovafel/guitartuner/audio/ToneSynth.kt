package ru.eurovafel.guitartuner.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Эталонный тон для настройки на слух.
 *
 * Это не чистый синус: лёгкие обертоны и короткая атака делают звук ближе
 * к щипку струны и помогают услышать ноту на маленьком динамике телефона.
 */
object ToneSynth {
    fun pluck(
        frequency: Double,
        sampleRate: Int,
        seconds: Double = 2.0,
    ): FloatArray {
        require(frequency > 0.0)
        require(sampleRate > 0)
        require(seconds > 0.0)

        val count = (sampleRate * seconds).toInt().coerceAtLeast(1)
        val attackSeconds = 0.004
        val releaseSeconds = 0.08
        val releaseStart = (seconds - releaseSeconds).coerceAtLeast(0.0)
        val raw = DoubleArray(count) { index ->
            val time = index.toDouble() / sampleRate
            val attack = if (time < attackSeconds) time / attackSeconds else 1.0
            val release = if (time > releaseStart) {
                ((seconds - time) / releaseSeconds).coerceIn(0.0, 1.0)
            } else {
                1.0
            }
            val envelope = attack * release * exp(-time * 1.8)
            val wave = sin(2.0 * PI * frequency * time) +
                0.32 * sin(2.0 * PI * frequency * 2.0 * time) +
                0.14 * sin(2.0 * PI * frequency * 3.0 * time)
            wave * envelope
        }

        val peak = raw.maxOfOrNull { abs(it) } ?: 0.0
        val scale = if (peak > 0.0) 0.55 / peak else 0.0
        return FloatArray(count) { index ->
            (raw[index] * scale).toFloat().coerceIn(-1f, 1f)
        }
    }
}
