package ru.eurovafel.guitartuner.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * YIN (de Cheveigné & Kawahara, 2002) — устойчивый к обертонам детектор основного тона.
 *
 * Для гитары берём окно 4096 сэмплов при 48 кГц: это ~85 мс и минимум 7 периодов
 * самой низкой струны (E2, 82.4 Гц). Окно меньше 2048 начинает путать октавы на басу.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val minFrequency: Double = 60.0,
    private val maxFrequency: Double = 1500.0,
    private val threshold: Double = 0.15,
    private val minRms: Double = 0.003,
) : PitchDetector {

    override fun detect(samples: FloatArray): PitchResult? {
        if (samples.size < 1024) return null

        var mean = 0.0
        for (sample in samples) mean += sample
        mean /= samples.size

        val x = DoubleArray(samples.size) { samples[it] - mean }
        var energy = 0.0
        for (value in x) energy += value * value
        val rms = sqrt(energy / x.size)
        if (rms < minRms) return null

        val minLag = max(2, (sampleRate / maxFrequency).toInt())
        val maxLag = min(x.size / 2, (sampleRate / minFrequency).toInt())
        if (maxLag <= minLag + 2) return null

        val frame = x.size - maxLag
        val difference = DoubleArray(maxLag + 1)
        for (tau in 1..maxLag) {
            var sum = 0.0
            var i = 0
            while (i < frame) {
                val delta = x[i] - x[i + tau]
                sum += delta * delta
                i++
            }
            difference[tau] = sum
        }

        difference[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..maxLag) {
            runningSum += difference[tau]
            difference[tau] = if (runningSum == 0.0) 1.0 else difference[tau] * tau / runningSum
        }

        var bestLag = -1
        var tau = minLag
        while (tau <= maxLag) {
            if (difference[tau] < threshold) {
                while (tau + 1 <= maxLag && difference[tau + 1] < difference[tau]) tau++
                bestLag = tau
                break
            }
            tau++
        }

        if (bestLag == -1) {
            var minValue = Double.MAX_VALUE
            for (candidate in minLag..maxLag) {
                if (difference[candidate] < minValue) {
                    minValue = difference[candidate]
                    bestLag = candidate
                }
            }
            if (minValue > 0.35) return null
        }

        val refinedLag = parabolicInterpolation(difference, bestLag, minLag, maxLag)
        if (refinedLag <= 0.0) return null

        val candidateFrequency = sampleRate / refinedLag
        if (candidateFrequency < minFrequency || candidateFrequency > maxFrequency) return null

        val candidateClarity = (1.0 - difference[bestLag]).coerceIn(0.0, 1.0)
        val corrected = preferFundamental(
            samples = x,
            candidateFrequency = candidateFrequency,
            candidateClarity = candidateClarity,
            candidateLag = refinedLag,
            difference = difference,
            minLag = minLag,
            maxLag = maxLag,
        )
        return PitchResult(
            frequency = corrected.frequency,
            clarity = corrected.clarity,
            rms = rms,
        )
    }

    /**
     * YIN иногда выбирает не основной тон, а сильную гармонику: низкая E2
     * с доминирующей 4-й гармоникой читается как E4. Если у кандидата есть
     * заметная энергия на f/2, f/3 или f/4, возвращаем самый низкий такой тон.
     * Чистая высокая нота не сворачивается: энергии на её субгармониках нет.
     */
    private fun preferFundamental(
        samples: DoubleArray,
        candidateFrequency: Double,
        candidateClarity: Double,
        candidateLag: Double,
        difference: DoubleArray,
        minLag: Int,
        maxLag: Int,
    ): CorrectedPitch {
        val candidateMagnitude = magnitude(samples, candidateFrequency)
        if (candidateMagnitude <= 1e-12) {
            return CorrectedPitch(candidateFrequency, candidateClarity)
        }

        for (divisor in 4 downTo 2) {
            val subLag = (candidateLag * divisor).roundToInt()
            if (subLag <= minLag || subLag >= maxLag) continue

            val subFrequency = sampleRate / subLag.toDouble()
            if (subFrequency < minFrequency) continue

            if (magnitude(samples, subFrequency) < candidateMagnitude * FUNDAMENTAL_ENERGY_RATIO) {
                continue
            }

            val refinedSubLag = parabolicInterpolation(difference, subLag, minLag, maxLag)
            if (refinedSubLag <= 0.0) continue

            val frequency = sampleRate / refinedSubLag
            if (frequency < minFrequency || frequency > maxFrequency) continue

            val clarity = (1.0 - difference[subLag]).coerceIn(0.0, 1.0)
            return CorrectedPitch(frequency = frequency, clarity = clarity)
        }

        return CorrectedPitch(candidateFrequency, candidateClarity)
    }

    /** Амплитуда синусоиды на частоте [frequency] (алгоритм Гёрцеля). */
    private fun magnitude(samples: DoubleArray, frequency: Double): Double {
        val omega = 2.0 * PI * frequency / sampleRate
        val coefficient = 2.0 * cos(omega)
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        for (sample in samples) {
            s0 = sample + coefficient * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = s1 * s1 + s2 * s2 - coefficient * s1 * s2
        return sqrt(power.coerceAtLeast(0.0))
    }

    private fun parabolicInterpolation(
        values: DoubleArray,
        lag: Int,
        minLag: Int,
        maxLag: Int,
    ): Double {
        if (lag <= minLag || lag >= maxLag) return lag.toDouble()
        val left = values[lag - 1]
        val center = values[lag]
        val right = values[lag + 1]
        val denominator = left - 2.0 * center + right
        if (abs(denominator) < 1e-12) return lag.toDouble()
        val delta = 0.5 * (left - right) / denominator
        return lag + delta.coerceIn(-1.0, 1.0)
    }

    private data class CorrectedPitch(
        val frequency: Double,
        val clarity: Double,
    )

    private companion object {
        /** Порог энергии субгармоники относительно найденного кандидата. */
        const val FUNDAMENTAL_ENERGY_RATIO = 0.05
    }
}
