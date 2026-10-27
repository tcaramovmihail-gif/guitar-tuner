package ru.eurovafel.guitartuner.dsp

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Сглаживание частоты между кадрами.
 *
 * Медиана по последним кадрам убирает одиночные выбросы (щелчки, обертоны),
 * EMA делает движение стрелки плавным. Кадры с низкой чёткостью или тишиной
 * не попадают в историю: иначе шум «утаскивает» медиану.
 *
 * Октавы складываются к текущему значению до попадания в историю. Без этого
 * EMA между E2 (82 Гц) и её октавой (165 Гц) проходит через 120 Гц — частота
 * не соответствует ни одной ноте, и стрелка улетает на край шкалы.
 *
 * Класс не зависит от Android и проверяется JVM-тестами.
 */
class PitchSmoother(
    private val medianWindow: Int = 5,
    private val emaAlpha: Double = 0.25,
    private val minClarity: Double = 0.5,
    private val minRms: Double = 0.002,
    private val silenceFramesToReset: Int = 3,
) {
    private val history = ArrayDeque<Double>()
    private var ema: Double? = null
    private var quietFrames = 0

    /**
     * @return сглаженная частота или null, если сигнал пропал и история сброшена.
     *         В первые кадры тишины возвращается последнее значение, чтобы стрелка
     *         не дёргалась на паузе между щипками.
     */
    fun accept(result: PitchResult?): Double? {
        if (result == null || result.clarity < minClarity || result.rms < minRms) {
            quietFrames++
            if (quietFrames >= silenceFramesToReset) reset()
            return ema
        }

        quietFrames = 0
        val reference = ema ?: history.lastOrNull() ?: result.frequency
        val value = foldOctave(result.frequency, reference)
        history.addLast(value)
        while (history.size > medianWindow) history.removeFirst()

        val median = history.sorted()[history.size / 2]
        val previous = ema
        val next = if (previous == null) {
            median
        } else {
            val shift = abs(1200.0 * log2(median / previous))
            if (shift > SNAP_CENTS) {
                // Это уже другая нота, а не дрожание: не тянем EMA через
                // промежуточные частоты, а сразу принимаем новую.
                history.clear()
                history.addLast(median)
                median
            } else {
                previous + emaAlpha * (median - previous)
            }
        }
        ema = next
        return next
    }

    fun reset() {
        history.clear()
        ema = null
        quietFrames = 0
    }

    val smoothedFrequency: Double? get() = ema

    /**
     * Складывает октавы к [reference], если частота действительно рядом
     * с октавой. Вверх (когда частота ниже) — только на одну октаву:
     * два и больше — это, скорее, настоящая нижняя струна, а не ошибка.
     */
    private fun foldOctave(frequency: Double, reference: Double): Double {
        if (frequency <= 0.0 || reference <= 0.0) return frequency
        val octaves = log2(frequency / reference).roundToInt()
        if (octaves == 0 || octaves < -1) return frequency
        val folded = frequency / 2.0.pow(octaves)
        val error = abs(1200.0 * log2(folded / reference))
        return if (error <= OCTAVE_FOLD_CENTS) folded else frequency
    }

    private companion object {
        /** Расхождение, после которого это уже другая нота, а не дрожание. */
        const val SNAP_CENTS = 250.0

        /** Допуск, с которым частота считается октавой текущего значения. */
        const val OCTAVE_FOLD_CENTS = 100.0
    }
}
