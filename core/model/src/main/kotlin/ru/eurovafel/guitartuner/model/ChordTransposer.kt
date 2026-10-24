package ru.eurovafel.guitartuner.model

/**
 * Транспонирование аккордов и грубое определение тональности по тексту подбора.
 *
 * Транспонируются только «аккордовые» строки: строки, целиком состоящие из
 * аккордов и служебных пометок вроде `x2` или `|`. Строки с текстом песни
 * остаются нетронутыми, поэтому слова вроде «Amazing» не превращаются в аккорды.
 */
object ChordTransposer {

    private val SHARP_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
    )
    private val FLAT_NAMES = arrayOf(
        "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B",
    )

    private val NOTE_TO_PITCH_CLASS = mapOf(
        "C" to 0, "C#" to 1, "Db" to 1,
        "D" to 2, "D#" to 3, "Eb" to 3,
        "E" to 4, "Fb" to 4, "E#" to 5,
        "F" to 5, "F#" to 6, "Gb" to 6,
        "G" to 7, "G#" to 8, "Ab" to 8,
        "A" to 9, "A#" to 10, "Bb" to 10,
        "B" to 11, "Cb" to 11, "B#" to 0,
    )

    private val ROOT_REGEX = Regex("""^([A-G])([#b]?)(.*)$""")
    private val SUFFIX_REGEX = Regex(
        """^(?:maj|min|m|dim|aug|sus|add|M|°|ø|Δ|\+|-|\(|\)|[0-9]|#|b|/|[A-G])*$""",
    )
    private val CHORD_TOKEN_REGEX = Regex("""[A-G][#b]?[A-Za-z0-9#b+°øΔ()/\-]*""")

    private val MAJOR_OFFSETS = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val MINOR_OFFSETS = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    private val MAJOR_QUALITIES = arrayOf(
        Quality.MAJOR, Quality.MINOR, Quality.MINOR, Quality.MAJOR,
        Quality.MAJOR, Quality.MINOR, Quality.DIMINISHED,
    )
    private val MINOR_QUALITIES = arrayOf(
        Quality.MINOR, Quality.DIMINISHED, Quality.MAJOR, Quality.MINOR,
        Quality.MINOR, Quality.MAJOR, Quality.MAJOR,
    )
    private val MAJOR_WEIGHTS = doubleArrayOf(3.0, 2.0, 1.5, 2.5, 3.0, 2.0, 0.5)
    private val MINOR_WEIGHTS = doubleArrayOf(3.0, 1.0, 2.0, 2.5, 2.5, 3.0, 2.0)

    // Ключи, которые на гитаре принято записывать с бемолями.
    private val FLAT_MAJOR = setOf(5, 10, 3, 8, 1, 6)
    private val FLAT_MINOR = setOf(2, 7, 0, 5, 10, 3)

    /** Транспонирует аккорды в [text] на [semitones] полутонов. */
    fun transposeText(text: String, semitones: Int): String {
        if (semitones == 0 || text.isEmpty()) return text

        val key = detectKey(text)
        val preferFlats = key?.let(::keyUsesFlats) == true
        return text.split('\n').joinToString("\n") { line ->
            if (isChordLine(line)) {
                transposeChordLine(line, semitones, preferFlats)
            } else {
                line
            }
        }
    }

    /** Транспонирует один аккорд, сохраняя его надстройки и бас после слэша. */
    fun transposeChord(
        chord: String,
        semitones: Int,
        preferFlats: Boolean = false,
    ): String? {
        val trimmed = chord.trim()
        val match = ROOT_REGEX.matchEntire(trimmed) ?: return null
        val root = match.groupValues[1] + match.groupValues[2]
        val suffixWithBass = match.groupValues[3]
        if (!SUFFIX_REGEX.matches(suffixWithBass)) return null

        val slash = suffixWithBass.lastIndexOf('/')
        val suffix: String
        val bass: String?
        if (slash >= 0) {
            suffix = suffixWithBass.substring(0, slash)
            bass = suffixWithBass.substring(slash + 1)
        } else {
            suffix = suffixWithBass
            bass = null
        }

        val flat = preferFlats || root.endsWith("b") || bass?.endsWith("b") == true
        val transposedRoot = transposeNote(root, semitones, flat) ?: return null
        val transposedBass = bass?.let { transposeNote(it, semitones, flat) ?: it }

        return buildString {
            append(transposedRoot)
            append(suffix)
            if (transposedBass != null) append('/').append(transposedBass)
        }
    }

    /** Транспонирует тональность вида `Am`, `C`, `F#m`, `Bb`. */
    fun transposeKey(key: String, semitones: Int): String {
        val trimmed = key.trim()
        val minor = trimmed.endsWith("m") && !trimmed.endsWith("min")
        val rootPart = if (minor) trimmed.dropLast(1) else trimmed
        val rootPitchClass = NOTE_TO_PITCH_CLASS[rootPart] ?: return key
        val shifted = ((rootPitchClass + semitones) % 12 + 12) % 12
        val preferFlats = if (minor) shifted in FLAT_MINOR else shifted in FLAT_MAJOR
        val transposed = transposeChord(rootPart, semitones, preferFlats) ?: return key
        return transposed + if (minor) "m" else ""
    }

    /**
     * Определяет тональность по аккордовым строкам. Это эвристика для подсказки,
     * а не анализ аудио: она ищет тонику и лад, при которых аккорды чаще всего
     * попадают в соответствующую гамму. Первый и последний аккорды получают бонус.
     */
    fun detectKey(text: String): String? {
        val chords = extractChords(text)
        if (chords.isEmpty()) return null

        val parsed = chords.mapNotNull(::parseChord)
        if (parsed.isEmpty()) return null

        var bestTonic = 0
        var bestMinor = false
        var bestScore = Double.NEGATIVE_INFINITY

        for (minor in listOf(false, true)) {
            val offsets = if (minor) MINOR_OFFSETS else MAJOR_OFFSETS
            val qualities = if (minor) MINOR_QUALITIES else MAJOR_QUALITIES
            val weights = if (minor) MINOR_WEIGHTS else MAJOR_WEIGHTS

            for (tonic in 0..11) {
                var score = 0.0
                for (chord in parsed) {
                    val degree = offsets.indexOf((chord.pitchClass - tonic + 12) % 12)
                    if (degree < 0) continue
                    score += weights[degree]
                    if (chord.quality == qualities[degree]) score += 0.5
                }

                if (parsed.first().pitchClass == tonic) score += 1.5
                if (parsed.last().pitchClass == tonic) score += 2.5

                val tonicChord = parsed.firstOrNull { it.pitchClass == tonic }
                if (tonicChord == null) {
                    score -= 1.0
                } else {
                    if (minor && tonicChord.quality == Quality.MINOR) score += 1.0
                    if (!minor && tonicChord.quality == Quality.MAJOR) score += 1.0
                }

                if (score > bestScore) {
                    bestScore = score
                    bestTonic = tonic
                    bestMinor = minor
                }
            }
        }

        val preferFlats = if (bestMinor) bestTonic in FLAT_MINOR else bestTonic in FLAT_MAJOR
        val names = if (preferFlats) FLAT_NAMES else SHARP_NAMES
        return names[bestTonic] + if (bestMinor) "m" else ""
    }

    /** Проверяет, является ли токен аккордом (A, Am, C#m7, G/B, Bbadd9...). */
    fun isChord(token: String): Boolean {
        val cleaned = token.trim().trim(',', ';', ':', '[', ']', '(', ')', '|')
        if (cleaned.isEmpty()) return false
        val match = ROOT_REGEX.matchEntire(cleaned) ?: return false
        return SUFFIX_REGEX.matches(match.groupValues[3])
    }

    private fun extractChords(text: String): List<String> =
        text.split('\n')
            .filter(::isChordLine)
            .flatMap { line ->
                CHORD_TOKEN_REGEX.findAll(line)
                    .map { it.value }
                    .filter(::isChord)
                    .toList()
            }

    private fun isChordLine(line: String): Boolean {
        val tokens = line.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false

        var chordCount = 0
        for (token in tokens) {
            if (isChord(token)) {
                chordCount++
            } else if (!isChordLineMarker(token)) {
                return false
            }
        }
        return chordCount > 0
    }

    private fun isChordLineMarker(token: String): Boolean {
        val cleaned = token.trim().trim('(', ')', '[', ']', '{', '}', ',', '.').lowercase()
        return cleaned in setOf(
            "|", "||", "-", "–", "—", "*", "x2", "x3", "x4", "x5",
            "2x", "3x", "4x", "5x", "n.c", "nc",
        ) || cleaned.matches(Regex("""\d+x"""))
    }

    private fun transposeChordLine(
        line: String,
        semitones: Int,
        preferFlats: Boolean,
    ): String {
        val result = StringBuilder()
        var cursor = 0

        for (match in CHORD_TOKEN_REGEX.findAll(line)) {
            if (!isChord(match.value)) continue

            result.append(line, cursor, match.range.first)
            val transposed = transposeChord(match.value, semitones, preferFlats)
            result.append(transposed ?: match.value)
            cursor = match.range.last + 1
        }
        result.append(line, cursor, line.length)
        return result.toString()
    }

    private fun transposeNote(note: String, semitones: Int, preferFlats: Boolean): String? {
        val pitchClass = NOTE_TO_PITCH_CLASS[note] ?: return null
        val shifted = ((pitchClass + semitones) % 12 + 12) % 12
        return if (preferFlats) FLAT_NAMES[shifted] else SHARP_NAMES[shifted]
    }

    private fun keyUsesFlats(key: String): Boolean {
        val minor = key.endsWith("m")
        val root = if (minor) key.dropLast(1) else key
        val pitchClass = NOTE_TO_PITCH_CLASS[root] ?: return root.contains('b')
        return if (minor) pitchClass in FLAT_MINOR else pitchClass in FLAT_MAJOR
    }

    private fun parseChord(chord: String): ParsedChord? {
        val match = ROOT_REGEX.matchEntire(chord) ?: return null
        val root = match.groupValues[1] + match.groupValues[2]
        val pitchClass = NOTE_TO_PITCH_CLASS[root] ?: return null
        return ParsedChord(pitchClass, qualityOf(match.groupValues[3]))
    }

    private fun qualityOf(suffixWithBass: String): Quality {
        val suffix = suffixWithBass.substringBefore('/').lowercase()
        return when {
            suffix.startsWith("dim") || suffix.contains('°') || suffix.contains('ø') ->
                Quality.DIMINISHED
            suffix.startsWith("min") || (suffix.startsWith("m") && !suffix.startsWith("maj")) ->
                Quality.MINOR
            else -> Quality.MAJOR
        }
    }

    private data class ParsedChord(
        val pitchClass: Int,
        val quality: Quality,
    )

    private enum class Quality {
        MAJOR,
        MINOR,
        DIMINISHED,
    }
}
