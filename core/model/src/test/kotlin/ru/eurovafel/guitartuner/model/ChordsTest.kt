package ru.eurovafel.guitartuner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordsTest {

    @Test
    fun `library has unique names and every group is filled`() {
        assertEquals(ChordLibrary.all.size, ChordLibrary.all.map { it.name }.distinct().size)
        ChordGroup.entries.forEach { group ->
            assertTrue("group $group is empty", ChordLibrary.byGroup(group).isNotEmpty())
        }
    }

    @Test
    fun `c major uses only c e g`() {
        val notes = ChordLibrary.find("C")!!.noteNames().toSet()
        assertEquals(setOf("C", "E", "G"), notes)
    }

    @Test
    fun `a minor skips the muted low string`() {
        val chord = ChordLibrary.find("Am")!!
        assertEquals(5, chord.midiNotes().size)
        assertEquals(setOf("A", "C", "E"), chord.noteNames().toSet())
        assertFalse(chord.midiNotes().contains(40))
    }

    @Test
    fun `barre chord transposes every string by the same fret`() {
        val gMajor = ChordLibrary.find("G")!!
        assertEquals(listOf(43, 47, 50, 55, 59, 67), gMajor.midiNotes())
    }

    @Test
    fun `frequencies follow standard tuning`() {
        val a4 = ChordLibrary.find("A")!!.frequencies()
        // A2 110, E3 164.8, A3 220, C#4 277.2, E4 329.6 (A4 440).
        assertEquals(110.0, a4[0], 0.1)
        assertEquals(329.63, a4.last(), 0.1)
    }
}
