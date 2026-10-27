package ru.eurovafel.guitartuner.audio

/** Один шаг барабанной партии (восьмая нота). */
data class DrumStep(
    val kick: Boolean = false,
    val snare: Boolean = false,
    val hat: Boolean = false,
    /** Тише обычного: для баллад и «половинного» грува. */
    val soft: Boolean = false,
)

/**
 * Пять простых барабанных рисунков на такт 4/4.
 *
 * Сетка — восьмые: каждый шаг звучит через половину доли, поэтому темп
 * задаёт и скорость рисунка. Это позволяет показать и четверти, и синкопы,
 * не заводя отдельных аудиофайлов.
 */
enum class DrumPattern(val title: String, val steps: List<DrumStep>) {
    /** Классический рок: бочка на 1 и 3, малый на 2 и 4. */
    ROCK(
        "Рок",
        listOf(
            DrumStep(kick = true, hat = true),
            DrumStep(hat = true),
            DrumStep(snare = true, hat = true),
            DrumStep(hat = true),
            DrumStep(kick = true, hat = true),
            DrumStep(kick = true, hat = true),
            DrumStep(snare = true, hat = true),
            DrumStep(hat = true),
        ),
    ),

    /** Баллада: редкие удары, тихие тарелки, много воздуха. */
    BALLAD(
        "Баллада",
        listOf(
            DrumStep(kick = true, hat = true, soft = true),
            DrumStep(),
            DrumStep(hat = true, soft = true),
            DrumStep(),
            DrumStep(snare = true, hat = true, soft = true),
            DrumStep(),
            DrumStep(hat = true, soft = true),
            DrumStep(),
        ),
    ),

    /** Марш: ровные четверти, малый барабан на 2 и 4. */
    MARCH(
        "Марш",
        listOf(
            DrumStep(kick = true, hat = true),
            DrumStep(),
            DrumStep(snare = true, hat = true),
            DrumStep(),
            DrumStep(kick = true, hat = true),
            DrumStep(),
            DrumStep(snare = true, hat = true),
            DrumStep(),
        ),
    ),

    /** Фанк: синкопированная бочка и плотные восьмые тарелки. */
    FUNK(
        "Фанк",
        listOf(
            DrumStep(kick = true, hat = true),
            DrumStep(hat = true),
            DrumStep(hat = true),
            DrumStep(kick = true, hat = true),
            DrumStep(snare = true, hat = true),
            DrumStep(hat = true),
            DrumStep(kick = true, hat = true),
            DrumStep(hat = true),
        ),
    ),

    /** Панк: быстрый backbeat и тарелки восьмыми. */
    PUNK(
        "Панк",
        listOf(
            DrumStep(kick = true, hat = true),
            DrumStep(hat = true),
            DrumStep(snare = true, hat = true),
            DrumStep(hat = true),
            DrumStep(kick = true, hat = true),
            DrumStep(hat = true),
            DrumStep(snare = true, hat = true),
            DrumStep(kick = true, hat = true),
        ),
    ),
}
