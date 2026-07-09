package de.singular.looper.library

/**
 * One step in a track's practice arrangement: play the saved loop [loopId] [repeatCount] times
 * before moving on. The same loop may appear in several steps (e.g. Intro … Intro), so each step
 * carries its own [stepId] independent of the loop it points at.
 *
 * Stored as an ordered list on the owning [LibraryTrack]; a step whose [loopId] no longer resolves
 * to a saved loop (the loop was deleted) is simply skipped during playback.
 */
data class ArrangementStep(
    val stepId: String,
    val loopId: String,
    val repeatCount: Int = 1,
)
