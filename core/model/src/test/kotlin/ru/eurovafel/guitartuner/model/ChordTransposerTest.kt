package ru.eurovafel.guitartuner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTransposerTest {

    @Test
    fun `chord line is transposed and lyric line is kept`() {
        val text = """
            Куплет
            Am        F        C        G
            Замученный дорогой,
            Am        F        G
            Я выбился из сил,
        """.trimIndent()

        val result = ChordTransposer.transposeText(text, 2)

        assertTrue(result.contains("Bm        G        D        A"))
        assertTrue(result.contains("Bm        G        A"))
        assertTrue(result.contains("Замученный дорогой,"))
        assertTrue(result.contains("Я выбился из сил,"))
    }

    @Test
    fun `word that starts like a chord is not transposed`() {
        val text = """
            Amazing grace, how sweet the sound
            Am F C G
        """.trimIndent()

        val result = ChordTransposer.transposeText(text, 2)

        assertTrue(result.contains("Amazing grace, how sweet the sound"))
        assertTrue(result.contains("Bm G D A"))
    }

    @Test
    fun `tab line and section header are not transposed`() {
        val text = """
            [Куплет 1]:
            1-----0-0-----3-3
            2---1-------1-----
        """.trimIndent()

        assertEquals(text, ChordTransposer.transposeText(text, 3))
    }

    @Test
    fun `slash bass is transposed with the chord`() {
        assertEquals("A/C#", ChordTransposer.transposeChord("G/B", 2))
        assertEquals("Bb/D", ChordTransposer.transposeChord("G/B", 3, preferFlats = true))
    }

    @Test
    fun `flat chord spelling is preserved`() {
        assertEquals("C", ChordTransposer.transposeChord("Bb", 2))
        assertEquals("Db", ChordTransposer.transposeChord("Bb", 3))
        assertEquals("Dm7", ChordTransposer.transposeChord("C#m7", 1))
    }

    @Test
    fun `key is detected for common progressions`() {
        assertEquals("Am", ChordTransposer.detectKey("Am F C G"))
        assertEquals("C", ChordTransposer.detectKey("C G Am F"))
        assertEquals("Em", ChordTransposer.detectKey("Em C G D"))
        assertEquals("D", ChordTransposer.detectKey("D A Bm G"))
    }

    @Test
    fun `key is transposed`() {
        assertEquals("Bm", ChordTransposer.transposeKey("Am", 2))
        assertEquals("G", ChordTransposer.transposeKey("F", 2))
        assertEquals("Bb", ChordTransposer.transposeKey("C", -2))
    }

    @Test
    fun `chord recognition accepts common suffixes`() {
        listOf("A", "Am", "C#m7", "Bbadd9", "G/B", "Dsus4", "F#m7b5", "E7", "C°").forEach {
            assertTrue(it, ChordTransposer.isChord(it))
        }
        listOf("Amazing", "Припев", "1-----0-0", "x2", "N.C.").forEach {
            assertFalse(it, ChordTransposer.isChord(it))
        }
    }

    @Test
    fun `chord line markers do not break transposition`() {
        val text = "Am F C G | x2"
        assertEquals("Bm G D A | x2", ChordTransposer.transposeText(text, 2))
    }
}
