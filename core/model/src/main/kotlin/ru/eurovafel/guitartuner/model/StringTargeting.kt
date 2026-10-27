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

        // 1. Нота сама по себе — открытая струна (в своей октаве).
        val exact = openStrings.minByOrNull { abs(centsFrom(frequency, it, a4)) }
        if (exact != null && abs(centsFrom(frequency, exact, a4)) <= SWITCH_CENTS) {
            return exact
        }

        // 2. Нота — обертон более низкой струны. Детектор на низких струнах
        // часто отдаёт 2-ю/3-ю гармонику, а не основной тон: D3×3 = A4,
        // G3×3 = D5, E2×3 = B3. Берём струну с наименьшим номером гармоники:
        // 4-я гармоника почти не доминирует, 2-я и 3-я — обычное дело.
        val ancestor = openStrings
            .mapNotNull { midi ->
                val target = PitchMath.midiToFrequency(midi, a4)
                val harmonic = PitchMath.harmonicOf(frequency, target) ?: return@mapNotNull null
                Harmonic(midi, harmonic)
            }
            .minWithOrNull(compareBy({ it.number }, { it.midi }))
        if (ancestor != null) return ancestor.midi

        // 3. Запасной путь: та же нота в другой октаве (детектор ошибся октавой).
        val detectedMidi = PitchMath.reading(frequency, a4).midi
        return openStrings
            .filter { it % 12 == detectedMidi % 12 }
            .minByOrNull { abs(it - detectedMidi) }
    }

    /** Открытая струна — источник обертона, найденного детектором. */
    private data class Harmonic(val midi: Int, val number: Int)

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

    /**
     * Кандидат на новой щипковой атаке: после паузы струна могла смениться.
     *
     * Если нота всё ещё объясняется активной струной (она сама, её октава или
     * обертон), цель не трогаем. Иначе берём точную струну/обертон, а когда
     * струна расстроена сильнее, чем на полутон, — ближайшую открытую.
     * Это позволяет переключиться на другую струну, даже если она далека
     * от строя, но не срывает цель на обертоне той же струны.
     */
    fun freshCandidate(
        openStrings: List<Int>,
        frequency: Double,
        a4: Double = PitchMath.DEFAULT_A4,
        active: Int? = null,
    ): Int? {
        if (openStrings.isEmpty() || frequency <= 0.0) return null
        if (active != null) {
            val target = PitchMath.midiToFrequency(active, a4)
            if (PitchMath.foldNearTarget(frequency, target) != frequency) return active
        }
        return resolve(openStrings, frequency, a4, active = null)
            ?: nearestOpen(openStrings, frequency, a4)
    }

    /** Ближайшая открытая струна без ограничения на расстройку. */
    fun nearestOpen(
        openStrings: List<Int>,
        frequency: Double,
        a4: Double = PitchMath.DEFAULT_A4,
    ): Int? = openStrings.minByOrNull { abs(centsFrom(frequency, it, a4)) }
}
