package de.singular.looper.audio

/**
 * Pure, position-based edits to a chord timeline (a list of [ChordSpan] sorted by start). The
 * ViewModel handles snapping the incoming fraction to the beat grid and persisting the result;
 * everything here is side-effect-free so it can be unit-tested directly.
 *
 * Boundaries passed in are expected to already be snapped. All ops clamp to keep spans at least
 * [minWidth] wide and return the input unchanged when an index or position is out of range.
 */
object ChordEdits {

    /** Relabel span [index]; a NONE chord removes the span, leaving a gap in the lane. */
    fun relabel(spans: List<ChordSpan>, index: Int, chord: Chord): List<ChordSpan> {
        if (index !in spans.indices) return spans
        if (chord.quality == Quality.NONE) return spans.filterIndexed { i, _ -> i != index }
        return spans.mapIndexed { i, s -> if (i == index) s.copy(chord = chord) else s }
    }

    /**
     * Move the boundary between span [index] and span [index] + 1 to [frac]. The two spans meet at
     * the new boundary (any gap between them is absorbed), each kept at least [minWidth] wide.
     */
    fun moveBoundary(spans: List<ChordSpan>, index: Int, frac: Float, minWidth: Float): List<ChordSpan> {
        if (index < 0 || index >= spans.size - 1) return spans
        val left = spans[index]
        val right = spans[index + 1]
        val b = frac.coerceIn(left.startFrac + minWidth, right.endFrac - minWidth)
        if (b <= left.startFrac || b >= right.endFrac) return spans
        return spans.mapIndexed { i, s ->
            when (i) {
                index -> s.copy(endFrac = b)
                index + 1 -> s.copy(startFrac = b)
                else -> s
            }
        }
    }

    /** Split span [index] at [frac] into two spans of the same chord. */
    fun split(spans: List<ChordSpan>, index: Int, frac: Float, minWidth: Float): List<ChordSpan> {
        if (index !in spans.indices) return spans
        val s = spans[index]
        if (frac < s.startFrac + minWidth || frac > s.endFrac - minWidth) return spans
        val left = s.copy(endFrac = frac)
        val right = s.copy(startFrac = frac)
        return spans.subList(0, index) + left + right + spans.subList(index + 1, spans.size)
    }

    /**
     * Insert a chord filling the empty gap that contains [frac]. The new span spans from the
     * previous span's end (or 0) to the next span's start (or 1). No-op if [frac] lands inside an
     * existing span or the gap is narrower than [minWidth].
     */
    fun insertAt(spans: List<ChordSpan>, frac: Float, chord: Chord, minWidth: Float): List<ChordSpan> {
        if (spans.any { frac >= it.startFrac && frac < it.endFrac }) return spans
        val gapStart = spans.filter { it.endFrac <= frac }.maxOfOrNull { it.endFrac } ?: 0f
        val gapEnd = spans.filter { it.startFrac >= frac }.minOfOrNull { it.startFrac } ?: 1f
        if (gapEnd - gapStart < minWidth) return spans
        return (spans + ChordSpan(gapStart, gapEnd, chord)).sortedBy { it.startFrac }
    }

    /** Merge span [index] with the following span, keeping span [index]'s chord. */
    fun merge(spans: List<ChordSpan>, index: Int): List<ChordSpan> {
        if (index < 0 || index >= spans.size - 1) return spans
        val merged = spans[index].copy(endFrac = spans[index + 1].endFrac)
        return spans.subList(0, index) + merged + spans.subList(index + 2, spans.size)
    }
}
