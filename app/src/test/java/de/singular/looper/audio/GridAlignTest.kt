// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class GridAlignTest {

    private val beat = 0.01f // one beat = 1% of the file

    @Test fun wholeBeatsDoNotMoveTheLines() {
        assertEquals(0f, GridAlign.lineShift(0.002f, 0.002f + 3 * beat, beat))
        assertEquals(0f, GridAlign.lineShift(0.052f, 0.002f, beat))
    }

    @Test fun anOffBeatAnchorMovesTheLinesTheShortWay() {
        // A quarter beat later, seen from 7 beats away: the lines move +0.25 beat, not −0.75.
        assertEquals(0.0025f, GridAlign.lineShift(0.002f, 0.002f + 7.25f * beat, beat), 1e-6f)
        // A quarter beat earlier.
        assertEquals(-0.0025f, GridAlign.lineShift(0.0045f, 0.002f, beat), 1e-6f)
    }

    @Test fun shiftByBeatsMovesOneBeat() {
        assertEquals(0.012f, GridAlign.shiftByBeats(0.002f, 1, beat, 4), 1e-6f)
    }

    @Test fun shiftByBeatsFoldsIntoTheFirstBar() {
        // One beat earlier than 0.002 would be negative; it wraps to the same beat one bar on.
        assertEquals(0.032f, GridAlign.shiftByBeats(0.002f, -1, beat, 4), 1e-6f)
        // Ten bars into the file (where aligning can put it) folds back to the first bar.
        assertEquals(0.012f, GridAlign.shiftByBeats(0.4f + 0.002f, 1, beat, 4), 1e-6f)
    }

    @Test fun fourStepsInFourFourComeBackToTheStart() {
        var a = 0.002f
        repeat(4) { a = GridAlign.shiftByBeats(a, 1, beat, 4) }
        assertEquals(0.002f, a, 1e-6f)
    }

    @Test fun shiftByBeatsNeverDrawsTheLinesAnywhereElse() {
        val a = GridAlign.shiftByBeats(0.4413f, -3, beat, 3)
        assertEquals(0f, GridAlign.lineShift(0.4413f, a, beat))
    }
}
