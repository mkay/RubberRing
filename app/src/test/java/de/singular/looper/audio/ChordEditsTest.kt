package de.singular.looper.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ChordEditsTest {

    private fun span(start: Float, end: Float, root: Int, q: Quality = Quality.MAJ) =
        ChordSpan(start, end, Chord(root, q))

    private val base = listOf(
        span(0f, 0.25f, 0),   // C
        span(0.25f, 0.5f, 9, Quality.MIN), // Am
        span(0.5f, 1f, 7),    // G
    )

    @Test fun relabelChangesOneSpan() {
        val out = ChordEdits.relabel(base, 1, Chord(5, Quality.MAJ7))
        assertEquals(Chord(5, Quality.MAJ7), out[1].chord)
        assertEquals(3, out.size)
        assertEquals(base[0].chord, out[0].chord) // others untouched
    }

    @Test fun relabelToNoneRemovesSpan() {
        val out = ChordEdits.relabel(base, 1, Chord.NONE)
        assertEquals(2, out.size)
        assertEquals(listOf(Chord(0, Quality.MAJ), Chord(7, Quality.MAJ)), out.map { it.chord })
    }

    @Test fun moveBoundaryJoinsSpansAtNewPoint() {
        val out = ChordEdits.moveBoundary(base, 0, 0.3f, minWidth = 0.02f)
        assertEquals(0.3f, out[0].endFrac)
        assertEquals(0.3f, out[1].startFrac) // the two meet at the moved boundary
        assertEquals(0.5f, out[1].endFrac)   // far side unchanged
    }

    @Test fun moveBoundaryClampsToMinWidth() {
        val out = ChordEdits.moveBoundary(base, 0, 0.0f, minWidth = 0.05f) // would collapse span 0
        assertEquals(0.05f, out[0].endFrac, 1e-6f)
    }

    @Test fun splitCreatesTwoOfSameChord() {
        val out = ChordEdits.split(base, 2, 0.75f, minWidth = 0.02f)
        assertEquals(4, out.size)
        assertEquals(0.75f, out[2].endFrac)
        assertEquals(0.75f, out[3].startFrac)
        assertEquals(out[2].chord, out[3].chord)
    }

    @Test fun mergeCombinesNeighbours() {
        val out = ChordEdits.merge(base, 0)
        assertEquals(2, out.size)
        assertEquals(0f, out[0].startFrac)
        assertEquals(0.5f, out[0].endFrac)         // spans 0 and 1 combined
        assertEquals(Chord(0, Quality.MAJ), out[0].chord) // keeps the first chord
    }

    @Test fun insertFillsAGap() {
        // A timeline with a gap between 0.5 and 0.8.
        val gapped = listOf(span(0f, 0.5f, 0), span(0.8f, 1f, 7))
        val out = ChordEdits.insertAt(gapped, 0.65f, Chord(5, Quality.MAJ), minWidth = 0.02f)
        assertEquals(3, out.size)
        val added = out[1]
        assertEquals(0.5f, added.startFrac) // fills from prev end
        assertEquals(0.8f, added.endFrac)   // to next start
        assertEquals(Chord(5, Quality.MAJ), added.chord)
    }

    @Test fun insertInsideExistingSpanIsNoOp() {
        assertEquals(base, ChordEdits.insertAt(base, 0.1f, Chord(5, Quality.MAJ), minWidth = 0.02f))
    }

    @Test fun outOfRangeIsNoOp() {
        assertEquals(base, ChordEdits.relabel(base, 9, Chord(0, Quality.MAJ)))
        assertEquals(base, ChordEdits.moveBoundary(base, 2, 0.6f, 0.02f)) // no span after last
        assertEquals(base, ChordEdits.merge(base, 2))
    }
}
