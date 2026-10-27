package ru.eurovafel.guitartuner.model

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Результат определения высоты тона с привязкой к ближайшей ноте. */
data class NoteReading(
    val midi: Int,
    val name: String,
    val octave: Int,
    val frequency: Double,
    val targetFrequency: Double,
    val cents: Double,
) {
    val fullName: String get() = "$name$octave"
}

/** Перевод частоты в ноты и обратно. Опорная нота A4 настраивается (430–450 Гц). */
object PitchMath {
    const val DEFAULT_A4 = 440.0

    private val noteNames = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
    )

    fun midiToFrequency(midi: Int, a4: Double = DEFAULT_A4): Double =
        a4 * 2.0.pow((midi - 69) / 12.0)

    fun frequencyToMidi(frequency: Double, a4: Double = DEFAULT_A4): Double =
        69.0 + 12.0 * log2(frequency / a4)

    fun reading(frequency: Double, a4: Double = DEFAULT_A4): NoteReading {
        require(frequency > 0.0) { "frequency must be positive" }
        val exactMidi = frequencyToMidi(frequency, a4)
        val midi = exactMidi.roundToInt()
        val target = midiToFrequency(midi, a4)
        val cents = 1200.0 * log2(frequency / target)
        val name = noteNames[((midi % 12) + 12) % 12]
        val octave = midi / 12 - 1
        return NoteReading(midi, name, octave, frequency, target, cents)
    }

    /**
     * Номер гармоники цели, если частота близка к целому кратному.
     * Например, 3-я гармоника A2 (110 Гц) — это 330 Гц (открытая E4),
     * поэтому такие совпадения нельзя считать другой струной.
     *
     * Допуск — это расстройка самой струны: если струна уехала на δ центов,
     * её n-я гармоника тоже уехала на δ (делениe на n ошибку не меняет).
     * Поэтому 45 ¢ распознавали гармонику только у почти настроенной струны,
     * и на 4–6 струнах с детюном 20–50 ¢ цель начинала прыгать. 150 ¢
     * покрывают реальный диапазон подстройки и не путают соседние струны:
     * ближайшая негармоническая пара E2→D3 даёт −200 ¢.
     */
    fun harmonicOf(
        frequency: Double,
        target: Double,
        toleranceCents: Double = 150.0,
    ): Int? {
        if (frequency <= 0.0 || target <= 0.0) return null
        val ratio = frequency / target
        val harmonic = ratio.roundToInt()
        if (harmonic < 2) return null
        val error = 1200.0 * log2(ratio / harmonic)
        return if (abs(error) <= toleranceCents) harmonic else null
    }

    /** Частота, свёрнутая к октаве или обертону цели. */
    fun foldToTarget(frequency: Double, target: Double): Double {
        if (frequency <= 0.0 || target <= 0.0) return frequency
        val harmonic = harmonicOf(frequency, target)
        if (harmonic != null) return frequency / harmonic
        val octaves = log2(frequency / target).roundToInt()
        return frequency / 2.0.pow(octaves)
    }

    /**
     * Сворачивает частоту к октаве/обертону цели, но только если она правда
     * близка к ним. Чужую ноту (например, A2 при цели E4) не трогает — иначе
     * переключение на другую струну перестало бы работать.
     *
     * Нужна перед сглаживанием: EMA между E2 и её октавой проходит через
     * 120 Гц и даёт скачки в сотни центов. Свёрнутая частота остаётся 82.4.
     */
    fun foldNearTarget(
        frequency: Double,
        target: Double,
        harmonicCents: Double = 150.0,
    ): Double {
        if (frequency <= 0.0 || target <= 0.0) return frequency
        val harmonic = harmonicOf(frequency, target, harmonicCents)
        if (harmonic != null) return frequency / harmonic
        val octaves = log2(frequency / target).roundToInt()
        // Вверх — только на одну октаву: 82 Гц при цели E4 (329.6) это уже
        // скорее настоящая шестая струна, чем ошибка детектора.
        if (octaves == 0 || octaves < -1) return frequency
        val folded = frequency / 2.0.pow(octaves)
        val error = abs(1200.0 * log2(folded / target))
        return if (error <= OCTAVE_FOLD_CENTS) folded else frequency
    }

    /** Допуск, с которым частота считается октавой цели, центы. */
    const val OCTAVE_FOLD_CENTS = 100.0

    /** Отклонение от цели в центах с учётом октав и обертонов. */
    fun centsToTarget(frequency: Double, target: Double): Double =
        1200.0 * log2(foldToTarget(frequency, target) / target)
}
