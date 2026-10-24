package ru.eurovafel.guitartuner.model

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
}
