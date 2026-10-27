package ru.eurovafel.guitartuner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChordShapesTest {

    @Test
    fun knownChordsComeFromLibrary() {
        val chord = ChordShapes.find("Am")
        assertEquals("Am", chord?.name)
        assertEquals(ChordLibrary.find("Am")?.frets, chord?.frets)
    }

    @Test
    fun barreChordsAreGenerated() {
        listOf("F#m", "Bb", "Bm7", "G#", "C#7").forEach { name ->
            val chord = ChordShapes.find(name)
            assertNotNull("Нет схемы для $name", chord)
            assertEquals(6, chord!!.frets.size)
            assertEquals(name, chord.name)
            assertEquals(true, chord.frets.any { it >= 0 })
        }
    }

    @Test
    fun generatedShapeMatchesRootNote() {
        val chord = ChordShapes.find("Bb")!!
        val notes = chord.midiNotes().map { it % 12 }.toSet()
        // Bb = 10. В мажорном баррэ обязательны тоника, терция (D = 2) и квинта (F = 5).
        assertEquals(setOf(10, 2, 5), notes)
    }

    @Test
    fun russianAndSlashNotationIsNormalized() {
        assertEquals("B", ChordShapes.find("H")?.name)
        assertEquals("Am", ChordShapes.find("Am/C")?.name)
        assertEquals("F#m", ChordShapes.find("F\u266Fm")?.name)
    }

    @Test
    fun unknownChordsAreNotInvented() {
        assertNull(ChordShapes.find("N.C."))
        assertNull(ChordShapes.find("Cadd9"))
        assertNull(ChordShapes.find(""))
    }

    @Test
    fun allGeneratedFretsArePlayable() {
        listOf("A", "Bb", "B", "C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#").forEach { root ->
            val chord = ChordShapes.find(root)!!
            assertEquals("$root: 6 струн", 6, chord.frets.size)
            assertEquals("$root: высокий лад", true, chord.frets.max() <= 12)
        }
    }
}
