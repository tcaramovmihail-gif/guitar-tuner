package ru.eurovafel.guitartuner.model

/** Группа аккордов на вкладке «Аккорды». */
enum class ChordGroup(val title: String) {
    MAJOR("Мажорные"),
    MINOR("Минорные"),
    SEVENTH("Септаккорды"),
}

/**
 * Аппликатура аккорда в стандартном строе.
 *
 * [frets] — лады от самой низкой струны к самой высокой: −1 — струна не звучит,
 * 0 — открытая, 1 и больше — номер лада. Значения достаточно, чтобы показать
 * схему и синтезировать звучание без таблиц нот.
 */
data class ChordShape(
    val name: String,
    val group: ChordGroup,
    val frets: List<Int>,
) {
    init {
        require(frets.size == STANDARD_STRING_COUNT) {
            "Аккорд $name должен описывать $STANDARD_STRING_COUNT струн"
        }
    }

    /** MIDI-ноты звучащих струн в стандартном строе. */
    fun midiNotes(tuning: InstrumentTuning = InstrumentTuning.GUITAR_STANDARD): List<Int> =
        frets.mapIndexedNotNull { index, fret ->
            val open = tuning.midiNotes.getOrNull(index) ?: return@mapIndexedNotNull null
            if (fret < 0) null else open + fret
        }

    fun frequencies(
        tuning: InstrumentTuning = InstrumentTuning.GUITAR_STANDARD,
        a4: Double = PitchMath.DEFAULT_A4,
    ): List<Double> = midiNotes(tuning).map { PitchMath.midiToFrequency(it, a4) }

    /** Названия нот для подписи под схемой. */
    fun noteNames(): List<String> = midiNotes().map { midi ->
        PitchMath.reading(PitchMath.midiToFrequency(midi)).name
    }

    companion object {
        const val STANDARD_STRING_COUNT = 6
    }
}

/** Базовые аккорды для начинающего: открытые и простые баррэ. */
object ChordLibrary {

    val all: List<ChordShape> = listOf(
        // Мажорные.
        chord("C", ChordGroup.MAJOR, -1, 3, 2, 0, 1, 0),
        chord("D", ChordGroup.MAJOR, -1, -1, 0, 2, 3, 2),
        chord("E", ChordGroup.MAJOR, 0, 2, 2, 1, 0, 0),
        chord("F", ChordGroup.MAJOR, 1, 3, 3, 2, 1, 1),
        chord("G", ChordGroup.MAJOR, 3, 2, 0, 0, 0, 3),
        chord("A", ChordGroup.MAJOR, -1, 0, 2, 2, 2, 0),
        chord("B", ChordGroup.MAJOR, -1, 2, 4, 4, 4, 2),

        // Минорные.
        chord("Am", ChordGroup.MINOR, -1, 0, 2, 2, 1, 0),
        chord("Dm", ChordGroup.MINOR, -1, -1, 0, 2, 3, 1),
        chord("Em", ChordGroup.MINOR, 0, 2, 2, 0, 0, 0),
        chord("Fm", ChordGroup.MINOR, 1, 3, 3, 1, 1, 1),
        chord("Gm", ChordGroup.MINOR, 3, 5, 5, 3, 3, 3),
        chord("Bm", ChordGroup.MINOR, -1, 2, 4, 4, 3, 2),
        chord("Cm", ChordGroup.MINOR, -1, 3, 5, 5, 4, 3),

        // Септаккорды.
        chord("A7", ChordGroup.SEVENTH, -1, 0, 2, 0, 2, 0),
        chord("B7", ChordGroup.SEVENTH, -1, 2, 1, 2, 0, 2),
        chord("C7", ChordGroup.SEVENTH, -1, 3, 2, 3, 1, 0),
        chord("D7", ChordGroup.SEVENTH, -1, -1, 0, 2, 1, 2),
        chord("E7", ChordGroup.SEVENTH, 0, 2, 0, 1, 0, 0),
        chord("G7", ChordGroup.SEVENTH, 3, 2, 0, 0, 0, 1),
        chord("Am7", ChordGroup.SEVENTH, -1, 0, 2, 0, 1, 0),
        chord("Dm7", ChordGroup.SEVENTH, -1, -1, 0, 2, 1, 1),
        chord("Em7", ChordGroup.SEVENTH, 0, 2, 0, 0, 0, 0),
    )

    fun byGroup(group: ChordGroup): List<ChordShape> = all.filter { it.group == group }

    fun find(name: String): ChordShape? = all.firstOrNull { it.name == name }

    private fun chord(name: String, group: ChordGroup, vararg frets: Int) =
        ChordShape(name = name, group = group, frets = frets.toList())
}
