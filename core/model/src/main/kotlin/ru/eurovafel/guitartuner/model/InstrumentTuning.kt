package ru.eurovafel.guitartuner.model

/** Строй инструмента: MIDI-ноты открытых струн от самой низкой к самой высокой. */
enum class InstrumentTuning(
    val displayName: String,
    val midiNotes: List<Int>,
) {
    GUITAR_STANDARD("Гитара, стандартный", listOf(40, 45, 50, 55, 59, 64)),
    GUITAR_DROP_D("Гитара, Drop D", listOf(38, 45, 50, 55, 59, 64)),
    GUITAR_HALF_STEP_DOWN("Гитара, на полтона ниже", listOf(39, 44, 49, 54, 58, 63)),
    UKULELE("Укулеле", listOf(67, 60, 64, 69)),
    BASS("Бас-гитара", listOf(28, 33, 38, 43)),
    CHROMATIC("Хроматический", emptyList()),
}
