package ru.eurovafel.guitartuner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StringTrackerTest {

    private val guitar = InstrumentTuning.GUITAR_STANDARD.midiNotes

    @Test
    fun `default tracker holds the active string for a long switch window`() {
        val tracker = StringTracker()
        repeat(5) { tracker.update(guitar, 40) }

        repeat(11) {
            assertEquals(40, tracker.update(guitar, 45))
        }
        assertEquals(45, tracker.update(guitar, 45))
    }

    @Test
    fun `string is acquired only after several stable frames`() {
        val tracker = StringTracker(acquireFrames = 3)

        assertNull(tracker.update(guitar, 40))
        assertNull(tracker.update(guitar, 40))
        assertEquals(40, tracker.update(guitar, 40))
    }

    @Test
    fun `single wrong frame does not switch active string`() {
        val tracker = StringTracker(acquireFrames = 2, switchFrames = 4)
        tracker.update(guitar, 40)
        tracker.update(guitar, 40)

        assertEquals(40, tracker.update(guitar, 45))
        assertEquals(40, tracker.update(guitar, 40))
        assertEquals(40, tracker.update(guitar, 45))
    }

    @Test
    fun `different string is accepted after the switch window`() {
        val tracker = StringTracker(acquireFrames = 2, switchFrames = 4)
        repeat(2) { tracker.update(guitar, 40) }

        repeat(3) { assertEquals(40, tracker.update(guitar, 45)) }
        assertEquals(45, tracker.update(guitar, 45))
    }

    @Test
    fun `active string survives a short pause and clears after release window`() {
        val tracker = StringTracker(acquireFrames = 2, releaseFrames = 3)
        repeat(2) { tracker.update(guitar, 40) }

        assertEquals(40, tracker.update(guitar, null))
        assertEquals(40, tracker.update(guitar, null))
        assertNull(tracker.update(guitar, null))
    }

    @Test
    fun `octave is folded to the nearest open string`() {
        val tracker = StringTracker(acquireFrames = 1)
        assertEquals(64, tracker.update(guitar, 76)) // E5 -> high E
        tracker.reset()

        assertEquals(40, tracker.update(guitar, 52)) // E3 -> low E
        tracker.reset()

        assertEquals(45, tracker.update(guitar, 57)) // A3 -> A2
    }

    @Test
    fun `chromatic mode does not keep a string`() {
        val tracker = StringTracker(acquireFrames = 1)
        assertEquals(40, tracker.update(guitar, 40))

        assertNull(tracker.update(emptyList(), 40))
        assertNull(tracker.update(emptyList(), 40))
    }

    @Test
    fun `note outside open strings does not change active string`() {
        val tracker = StringTracker(acquireFrames = 1, switchFrames = 2)
        assertEquals(40, tracker.update(guitar, 40))

        assertEquals(40, tracker.update(guitar, 61)) // C#4 is not an open string
        assertEquals(40, tracker.update(guitar, 40))
    }
}
