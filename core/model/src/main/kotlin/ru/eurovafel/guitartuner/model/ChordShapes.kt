package ru.eurovafel.guitartuner.model

/**
 * Поиск аппликатуры по названию аккорда из текста песни.
 *
 * В песнях встречаются аккорды, которых нет в [ChordLibrary] (баррэ, sus,
 * шестёрки). Для них строим простую форму от мажорного/минорного баррэ:
 * это честная схема, а не выдуманная нота. Сложные созвучия (dim, add9)
 * лучше не показывать вовсе, чем показать неверную схему.
 */
object ChordShapes {

    /** @return схема или null, если аккорд не распознан. */
    fun find(name: String): ChordShape? {
        val clean = normalize(name) ?: return null
        ChordLibrary.find(clean.name)?.let { return it }
        return generate(clean)?.let { shape ->
            // В библиотеке имя может отличаться регистром — сохраняем привычное.
            shape.copy(name = clean.name)
        }
    }

    private data class Parsed(
        val root: String,
        val quality: Quality,
    ) {
        val name: String get() = root + quality.suffix
    }

    private enum class Quality(
        val suffix: String,
        val group: ChordGroup,
        val eShape: List<Int>,
        val aShape: List<Int>,
    ) {
        MAJOR(
            "", ChordGroup.MAJOR,
            listOf(0, 2, 2, 1, 0, 0),
            listOf(-1, 0, 2, 2, 2, 0),
        ),
        MINOR(
            "m", ChordGroup.MINOR,
            listOf(0, 2, 2, 0, 0, 0),
            listOf(-1, 0, 2, 2, 1, 0),
        ),
        SEVEN(
            "7", ChordGroup.SEVENTH,
            listOf(0, 2, 0, 1, 0, 0),
            listOf(-1, 0, 2, 0, 2, 0),
        ),
        MINOR_SEVEN(
            "m7", ChordGroup.SEVENTH,
            listOf(0, 2, 0, 0, 0, 0),
            listOf(-1, 0, 2, 0, 1, 0),
        ),
        MAJOR_SEVEN(
            "maj7", ChordGroup.SEVENTH,
            listOf(0, 2, 1, 1, 0, 0),
            listOf(-1, 0, 2, 1, 2, 0),
        ),
        SUS2(
            "sus2", ChordGroup.MAJOR,
            listOf(0, 2, 4, 4, 0, 0),
            listOf(-1, 0, 2, 2, 0, 0),
        ),
        SUS4(
            "sus4", ChordGroup.MAJOR,
            listOf(0, 2, 2, 2, 0, 0),
            listOf(-1, 0, 2, 2, 3, 0),
        ),
        SIX(
            "6", ChordGroup.MAJOR,
            listOf(0, 2, 2, 1, 2, 0),
            listOf(-1, 0, 2, 2, 2, 2),
        ),
        MINOR_SIX(
            "m6", ChordGroup.MINOR,
            listOf(0, 2, 2, 0, 2, 0),
            listOf(-1, 0, 2, 2, 1, 2),
        ),
        POWER(
            "5", ChordGroup.MAJOR,
            listOf(0, 2, 2, -1, -1, -1),
            listOf(-1, 0, 2, 2, -1, -1),
        ),
    }

    private fun generate(parsed: Parsed): ChordShape? {
        val pitch = pitchClass(parsed.root) ?: return null
        val eFret = fretOnString(pitch, openPitch = 4)
        val aFret = fretOnString(pitch, openPitch = 9)
        val candidates = buildList {
            eFret?.let { root -> add(transpose(parsed.quality.eShape, root)) }
            aFret?.let { root -> add(transpose(parsed.quality.aShape, root)) }
        }
        val playable = candidates.filter { frets ->
            val max = frets.maxOrNull() ?: return@filter false
            max <= MAX_FRET
        }
        // Предпочитаем форму ближе к голове грифа: её проще взять начинающему.
        val best = playable.minWithOrNull(
            compareBy({ frets -> frets.filter { it > 0 }.maxOrNull() ?: 0 }, { it.size }),
        ) ?: return null

        return ChordShape(
            name = parsed.name,
            group = parsed.quality.group,
            frets = best,
        )
    }

    /** Сдвигает форму на [root] ладов, не трогая заглушённые струны. */
    private fun transpose(shape: List<Int>, root: Int): List<Int> =
        shape.map { fret -> if (fret < 0) fret else fret + root }

    /** Лад, на котором нота [pitch] встречается на открытой струне [openPitch]. */
    private fun fretOnString(pitch: Int, openPitch: Int): Int? {
        val fret = (pitch - openPitch + 12) % 12
        return if (fret <= MAX_FRET) fret else null
    }

    private fun pitchClass(root: String): Int? {
        val letter = root.firstOrNull()?.uppercaseChar() ?: return null
        val base = when (letter) {
            'C' -> 0
            'D' -> 2
            'E' -> 4
            'F' -> 5
            'G' -> 7
            'A' -> 9
            'B' -> 11
            else -> return null
        }
        return when (root.drop(1)) {
            "" -> base
            "#", "\u266F" -> (base + 1) % 12
            "b", "\u266D" -> (base + 11) % 12
            else -> null
        }
    }

    /**
     * Приводит запись к привычной: «H» → B, «min» → m, «M7» → maj7.
     * Слэш-бас отбрасываем: аппликатура от него не меняется.
     */
    private fun normalize(raw: String): Parsed? {
        var value = raw.trim()
            .replace("\u266F", "#")
            .replace("\u266D", "b")
        value = value.substringBefore('/').substringBefore('(').trim()
        if (value.isEmpty()) return null
        if (value.startsWith("H")) value = "B" + value.drop(1)

        val match = ROOT.matchAt(value, 0) ?: return null
        val root = match.value
        val rest = value.drop(match.value.length)
        val quality = parseQuality(rest) ?: return null
        return Parsed(root = root.replaceFirstChar { it.uppercaseChar() }, quality = quality)
    }

    private fun parseQuality(rest: String): Quality? {
        val value = rest.replace(" ", "")
            .replace("M7", "maj7")
            .replace("min", "m")
            .replace("maj", "maj")
            .removeSuffix("alt")
        return when (value) {
            "", "M", "maj" -> Quality.MAJOR
            "m" -> Quality.MINOR
            "7" -> Quality.SEVEN
            "m7" -> Quality.MINOR_SEVEN
            "maj7", "\u03947" -> Quality.MAJOR_SEVEN
            "sus2" -> Quality.SUS2
            "sus4", "sus" -> Quality.SUS4
            "6" -> Quality.SIX
            "m6" -> Quality.MINOR_SIX
            "5" -> Quality.POWER
            else -> null
        }
    }

    private val ROOT = Regex("^[A-Ga-g][#b]?")

    private const val MAX_FRET = 12
}
