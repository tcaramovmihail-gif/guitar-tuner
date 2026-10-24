package ru.eurovafel.guitartuner.dsp

/**
 * Сглаживание частоты между кадрами.
 *
 * Медиана по последним кадрам убирает одиночные выбросы (щелчки, обертоны),
 * EMA делает движение стрелки плавным. Кадры с низкой чёткостью или тишиной
 * не попадают в историю: иначе шум «утаскивает» медиану.
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
        history.addLast(result.frequency)
        while (history.size > medianWindow) history.removeFirst()

        val median = history.sorted()[history.size / 2]
        val previous = ema
        val next = if (previous == null) median else previous + emaAlpha * (median - previous)
        ema = next
        return next
    }

    fun reset() {
        history.clear()
        ema = null
        quietFrames = 0
    }

    val smoothedFrequency: Double? get() = ema
}
