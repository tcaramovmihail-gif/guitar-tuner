package ru.eurovafel.guitartuner.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Синтез барабанной партии без сэмплов.
 *
 * На шаг сетки (восьмую) приходится бочка, малый и/или тарелка. Инструменты
 * складываются с фиксированными уровнями, а не нормализуются каждый по
 * отдельности: иначе тарелка звучит так же громко, как бочка, и рисунок
 * превращается в шум. В конце шага — короткое затухание, чтобы обрезанный
 * хвост не щёлкал.
 *
 * Функции чистые, чтобы партию можно было проверить JVM-тестом.
 */
object DrumSynth {
    const val BEAT_SECONDS = 0.18

    /** Длина одного шага сетки: короче, чтобы восьмые не наезжали друг на друга. */
    const val STEP_SECONDS = 0.11

    /** Уровни инструментов в миксе. Тарелка намеренно тише бочки. */
    private const val KICK_GAIN = 0.92f
    private const val SNARE_GAIN = 0.46f
    private const val HAT_GAIN = 0.15f

    /** Тарелка на сильной доле звучит чуть громче. */
    private const val HAT_ACCENT = 1.4f

    private const val FADE_SECONDS = 0.004

    /**
     * Звук одного шага рисунка.
     *
     * [accent] — шаг на сильной доле: тарелка звучит громче, рисунок перестаёт
     * быть ровной «машинкой».
     */
    fun step(sampleRate: Int, step: DrumStep, accent: Boolean = false): FloatArray {
        require(sampleRate > 0)
        val length = (sampleRate * STEP_SECONDS).toInt().coerceAtLeast(1)
        val mix = FloatArray(length)

        if (step.kick) add(mix, kick(sampleRate), KICK_GAIN)
        if (step.snare) add(mix, snare(sampleRate), SNARE_GAIN)
        if (step.hat) {
            val gain = if (accent) HAT_GAIN * HAT_ACCENT else HAT_GAIN
            add(mix, hat(sampleRate), gain)
        }

        val level = if (step.soft) 0.5f else 1f
        for (index in mix.indices) {
            mix[index] = softClip(mix[index] * level)
        }
        fadeOut(mix, sampleRate, FADE_SECONDS)
        return mix
    }

    fun beat(sampleRate: Int, index: Int): FloatArray {
        require(sampleRate > 0)
        val length = (sampleRate * BEAT_SECONDS).toInt().coerceAtLeast(1)
        val mix = FloatArray(length)

        when (((index % 4) + 4) % 4) {
            0 -> add(mix, kick(sampleRate), KICK_GAIN)
            1 -> add(mix, snare(sampleRate), SNARE_GAIN)
            2 -> add(mix, kick(sampleRate), KICK_GAIN)
            3 -> add(mix, snare(sampleRate), SNARE_GAIN)
        }
        add(mix, hat(sampleRate), HAT_GAIN * HAT_ACCENT)

        return FloatArray(length) { softClip(mix[it]) }
    }

    /** Бочка: низкий тон с падением частоты и коротким щелчком битера. */
    fun kick(sampleRate: Int): FloatArray {
        require(sampleRate > 0)
        val length = (sampleRate * 0.22).toInt().coerceAtLeast(1)
        val random = Random(0x1A2B)
        var phase = 0.0
        var click = 0.0

        return FloatArray(length) { index ->
            val time = index.toDouble() / sampleRate
            val frequency = 47.0 + 78.0 * exp(-time * 32.0)
            phase += 2.0 * PI * frequency / sampleRate
            val attack = (time / 0.002).coerceAtMost(1.0)
            val body = sin(phase) * exp(-time * 11.0) * attack

            click += 0.4 * (random.nextDouble(-1.0, 1.0) - click)
            val beater = click * exp(-time * 110.0) * 0.55

            ((body * 0.95 + beater) * 0.9).toFloat().coerceIn(-1f, 1f)
        }
    }

    /**
     * Малый барабан: шумовая «пружина» и два тона корпуса.
     * Высокие шумовые частоты подчёркнуты, низкая середина не даёт звуку
     * превратиться в сухой щелчок.
     */
    fun snare(sampleRate: Int): FloatArray {
        require(sampleRate > 0)
        val length = (sampleRate * 0.2).toInt().coerceAtLeast(1)
        val random = Random(0x5EED)
        var previous = 0.0
        var low = 0.0

        return FloatArray(length) { index ->
            val time = index.toDouble() / sampleRate
            val noise = random.nextDouble(-1.0, 1.0)
            val bright = noise - previous * 0.92
            previous = noise
            low += 0.18 * (noise - low)

            val body = sin(2.0 * PI * 185.0 * time) * exp(-time * 26.0) * 0.42 +
                sin(2.0 * PI * 320.0 * time) * exp(-time * 38.0) * 0.16
            val wires = (bright * 0.34 + low * 0.18) * exp(-time * 20.0)

            ((wires + body) * 0.9).toFloat().coerceIn(-1f, 1f)
        }
    }

    /** Закрытая тарелка: короткий металлический всплеск, а не белый шум. */
    fun hat(sampleRate: Int): FloatArray {
        require(sampleRate > 0)
        val length = (sampleRate * 0.06).toInt().coerceAtLeast(1)
        val random = Random(0x0A7)
        var previous = 0.0
        val partials = doubleArrayOf(5_900.0, 7_100.0, 8_300.0, 9_700.0)

        return FloatArray(length) { index ->
            val time = index.toDouble() / sampleRate
            val noise = random.nextDouble(-1.0, 1.0)
            val bright = noise - previous
            previous = noise

            var metal = 0.0
            partials.forEachIndexed { partial, frequency ->
                metal += sin(2.0 * PI * frequency * time + partial) / (partial + 1)
            }

            ((bright * 0.7 + metal * 0.35) * exp(-time * 68.0) * 0.55)
                .toFloat()
                .coerceIn(-1f, 1f)
        }
    }

    private fun add(target: FloatArray, source: FloatArray, gain: Float) {
        val count = minOf(target.size, source.size)
        for (index in 0 until count) {
            target[index] += source[index] * gain
        }
    }

    /** Мягкое ограничение вместо жёсткой нормализации: сохраняет баланс. */
    private fun softClip(value: Float): Float = tanh(value.toDouble()).toFloat()

    /** Короткое затухание в конце шага: обрезанный хвост не должен щёлкать. */
    private fun fadeOut(samples: FloatArray, sampleRate: Int, seconds: Double) {
        val count = (sampleRate * seconds).toInt().coerceIn(1, samples.size)
        for (index in 0 until count) {
            val position = samples.size - count + index
            samples[position] *= 1f - index.toFloat() / count
        }
    }
}
