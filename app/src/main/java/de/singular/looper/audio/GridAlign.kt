// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Pure arithmetic for correcting the beat grid's anchor by hand. Positions are fractions of the
 * file; [beatFrac] is one beat's length as a fraction of the file.
 *
 * The detector finds where the beats fall, but not which of them is beat one: it anchors the grid
 * on whichever beat comes first. The two corrections here cover both cases a user can hear. Moving
 * the anchor by whole beats leaves every grid line where it was and only moves the accent. Moving
 * it to an arbitrary point also moves the lines themselves.
 */
object GridAlign {

    /** Below this (in beats) two grids are treated as having the same lines. */
    private const val SAME_PHASE_BEATS = 1e-3

    /**
     * How far the grid *lines* move when the anchor goes from [from] to [to]: the change reduced to
     * the nearest line, so a whole number of beats counts as no move. Zero means only the accent
     * moved.
     */
    fun lineShift(from: Float, to: Float, beatFrac: Float): Float {
        if (beatFrac <= 0f) return 0f
        val delta = (to - from).toDouble()
        val rest = delta - (delta / beatFrac).roundToLong() * beatFrac.toDouble()
        return if (abs(rest) < beatFrac * SAME_PHASE_BEATS) 0f else rest.toFloat()
    }

    /**
     * The anchor moved by [beats] whole beats, then folded back into the first bar of the file so it
     * stays a valid position however often it is shifted. The lines stay put; beat one moves.
     */
    fun shiftByBeats(anchor: Float, beats: Int, beatFrac: Float, beatsPerBar: Int): Float {
        if (beatFrac <= 0f) return anchor
        val bar = beatFrac.toDouble() * beatsPerBar.coerceAtLeast(1)
        val moved = anchor + beats * beatFrac.toDouble()
        val folded = moved - floor(moved / bar) * bar
        // Folding can land a hair under a full bar from rounding; that is the same position as 0.
        return if (bar - folded < beatFrac * SAME_PHASE_BEATS) 0f else folded.toFloat().coerceIn(0f, 1f)
    }
}
