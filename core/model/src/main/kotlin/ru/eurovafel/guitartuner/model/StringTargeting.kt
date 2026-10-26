package ru.eurovafel.guitartuner.model

import kotlin.math.abs
import kotlin.math.log2

/**
 * Решает, какую открытую струну показывать целью.
 *
 * Главное правило: если струна уже определена, цель зафиксирована. Пока нота
 * звучит, она не меняется — даже если детектор поймал обертон (A3 вместо A2)
 * или струна уехала по высоте. Смена возможна только когда новая нота точно
 * попала в другую открытую струну, а после паузы струна определяется заново.
 *
 * Класс не зависит от Android и проверяется JVM-тестами.
 */
object StringTargeting {
    /** Допуск «это точно другая открытая струна», центы. */
    const val SWITCH_CENTS = 100.0

    /** Допуск для подсказки «Слышу …», центы. */
    const val HINT_CENTS = 100.0

    fun centsFrom(frequency: Double, midi: Int, a4: Double = PitchMath.DEFAULT_A4): Double =
        1200.0 * log2(frequency / PitchMath.midiToFrequency(midi, a4))

    /**
     * @param active струна, которая уже выбрана и должна оставаться целью.
     * @return midi открытой струны либо null, если звук не похож на открытую.
     */
    fun resolve(
        openStrings: List<Int>,
        frequency: Double,
        a4: Double = PitchMath.DEFAULT_A4,
        active: Int? = null,
    ): Int? {
        if (openStrings.isEmpty() || frequency <= 0.0) return null

        if (active != null) {
            // Обертон активной струны (2×, 3×, 4× …) — не повод менять цель:
            // у A2 3-я гармоника совпадает с открытой E4, у E2 — с B3.
            if (PitchMath.harmonicOf(frequency, PitchMath.midiToFrequency(active, a4)) != null) {
                return active
            }
            val other = openStrings
                .filter { it != active }
                .minByOrNull { abs(centsFrom(frequency, it, a4)) }
            if (other != null && abs(centsFrom(frequency, other, a4)) <= SWITCH_CENTS) {
                return other
            }
            // Цель зафиксирована: обертон, промах и шум не переключают шкалу.
            return active
        }

        // Первый захват: нота в любой октаве. Детектор на низких струнах иногда
        // отдаёт вторую гармонику (E3 вместо E2), поэтому сравниваем по классу.
        val detectedMidi = PitchMath.reading(frequency, a4).midi
        return openStrings
            .filter { it % 12 == detectedMidi % 12 }
            .minByOrNull { abs(it - detectedMidi) }
    }

    /** Для подсказки «Слышу X»: только точное попадание в открытую струну. */
    fun hint(
        openStrings: List<Int>,
        frequency: Double,
        a4: Double = PitchMath.DEFAULT_A4,
        active: Int? = null,
    ): Int? {
        if (active != null &&
            PitchMath.harmonicOf(frequency, PitchMath.midiToFrequency(active, a4)) != null
        ) {
            return active
        }
        return openStrings
            .filter { abs(centsFrom(frequency, it, a4)) <= HINT_CENTS }
            .minByOrNull { abs(centsFrom(frequency, it, a4)) }
    }
}
