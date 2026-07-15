package de.singular.looper.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Objective checks for [ChordDetector] on synthesised triads of a known root and quality. Like
 * [BeatDetectorTest], these can't prove it nails real mixes (no ground truth here), but they guard
 * the chroma → template → segmentation path against regressions on the clean, unambiguous cases.
 */
class ChordDetectorTest {

    private val sampleRate = 44_100

    /** Equal-tempered frequency of a pitch class in a given octave (0 = C). */
    private fun freqOf(pitchClass: Int, octave: Int): Double {
        // MIDI note for this pitch class at the octave (MIDI 60 = C4), then to Hz (A4 = 69 = 440).
        val midi = 12 * (octave + 1) + pitchClass
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
    }

    /**
     * Render a sustained triad as the sum of its three notes, each a few sine partials so the
     * chroma sees real harmonic content (as a real instrument would present it), written into
     * [pcm] over [from, to).
     */
    /** Render the four notes of a seventh chord over [from, to). */
    private fun renderSeventh(pcm: ShortArray, from: Int, to: Int, root: Int, quality: Quality) {
        val third = if (quality == Quality.MIN7) root + 3 else root + 4
        val seventh = if (quality == Quality.MIN7) root + 10 else root + 11
        for (semis in intArrayOf(root, third, root + 7, seventh)) renderNote(pcm, from, to, semis)
    }

    /** One sustained note (fundamental + a few rolled-off partials) at [pitchClass], scaled by [level]. */
    private fun renderNote(pcm: ShortArray, from: Int, to: Int, pitchClass: Int, level: Double = 1.0) {
        val pc = ((pitchClass % 12) + 12) % 12
        val base = freqOf(pc, octave = 4) // mid-range: fundamental + partials sit inside the band
        for ((h, amp) in listOf(1 to 1.0, 2 to 0.5, 3 to 0.3)) {
            for (idx in from until to) {
                val s = sin(2 * PI * base * h * (idx - from) / sampleRate) * amp * level * 3_000
                pcm[idx] = (pcm[idx] + s.toInt()).toShort().coerceAudio()
            }
        }
    }

    private fun renderTriad(pcm: ShortArray, from: Int, to: Int, root: Int, quality: Quality, level: Double = 1.0) {
        val third = if (quality == Quality.MIN) root + 3 else root + 4
        for (semis in intArrayOf(root, third, root + 7)) renderNote(pcm, from, to, semis, level)
    }

    private fun triad(root: Int, quality: Quality, seconds: Double = 3.0): DecodedAudio {
        val totalFrames = (seconds * sampleRate).toInt()
        val pcm = ShortArray(totalFrames)
        renderTriad(pcm, 0, totalFrames, root, quality)
        return decoded(pcm)
    }

    /** Two triads back to back, to exercise segmentation into distinct spans. */
    private fun progression(first: Pair<Int, Quality>, second: Pair<Int, Quality>, seconds: Double = 6.0): DecodedAudio {
        val totalFrames = (seconds * sampleRate).toInt()
        val pcm = ShortArray(totalFrames)
        val mid = totalFrames / 2
        renderTriad(pcm, 0, mid, first.first, first.second)
        renderTriad(pcm, mid, totalFrames, second.first, second.second)
        return decoded(pcm)
    }

    private fun decoded(pcm: ShortArray): DecodedAudio {
        val wave = WaveformData(FloatArray(1), FloatArray(1), 0L, sampleRate, 1)
        val (peak, rms) = Gain.measure(pcm)
        return DecodedAudio(pcm, 1, sampleRate, wave, peak, rms)
    }

    private fun Short.coerceAudio(): Short = toInt().coerceIn(-32_768, 32_767).toShort()

    /** The dominant chord over the whole clip (the span covering the most time). */
    private fun dominantChord(track: ChordTrack): Chord =
        track.spans.maxByOrNull { it.endFrac - it.startFrac }!!.chord

    private fun assertTriad(root: Int, quality: Quality) {
        val result = ChordDetector.detect(triad(root, quality))
        assertNotNull("detector returned null for root=$root $quality", result)
        val chord = dominantChord(result!!)
        assertEquals("wrong root for root=$root $quality", root, chord.root)
        assertEquals("wrong quality for root=$root $quality", quality, chord.quality)
    }

    @Test fun detectsCMajor() = assertTriad(0, Quality.MAJ)
    @Test fun detectsAMinor() = assertTriad(9, Quality.MIN)
    @Test fun detectsGMajor() = assertTriad(7, Quality.MAJ)
    @Test fun detectsEMinor() = assertTriad(4, Quality.MIN)
    @Test fun detectsFMajor() = assertTriad(5, Quality.MAJ)
    @Test fun detectsDMinor() = assertTriad(2, Quality.MIN)
    @Test fun detectsDMajor() = assertTriad(2, Quality.MAJ)

    /**
     * The reported failure: a major chord read as minor. With stray energy leaking into the minor
     * third (D + 3 = F under a D major chord), a bare 3-note template flips to Dm because it decides
     * on that one bin; the harmonic-correlation classifier weighs the whole profile and holds D major.
     */
    @Test fun majorNotFlippedByStrayMinorThird() {
        val totalFrames = (4.0 * sampleRate).toInt()
        val pcm = ShortArray(totalFrames)
        renderTriad(pcm, 0, totalFrames, root = 2, quality = Quality.MAJ) // D major: D F# A
        renderNote(pcm, 0, totalFrames, pitchClass = 5, level = 0.5)       // stray F (the minor third)
        val result = ChordDetector.detect(decoded(pcm), BeatEstimate(120f, 0f))
        assertNotNull("detector returned null", result)
        assertEquals("D major flipped to minor by stray third", Chord(2, Quality.MAJ), dominantChord(result!!))
    }

    /** A silent clip yields no chords rather than a guess on noise. */
    @Test fun silenceHasNoChords() {
        val result = ChordDetector.detect(decoded(ShortArray((2.0 * sampleRate).toInt())))
        // Either null or an empty timeline is acceptable — the point is "no chord invented".
        assertTrue("silence produced chords", result == null || result.spans.isEmpty())
    }

    /** A two-chord progression must segment into both chords, in order. */
    @Test fun segmentsTwoChordProgression() {
        val result = ChordDetector.detect(progression(0 to Quality.MAJ, 7 to Quality.MAJ))
        assertNotNull("detector returned null for C→G", result)
        val spans = result!!.spans
        assertTrue("expected at least two spans, got ${spans.size}", spans.size >= 2)
        // The first substantial span is C major, a later one is G major.
        val cSpan = spans.first { it.endFrac - it.startFrac > 0.2f }
        assertEquals("first chord not C major", Chord(0, Quality.MAJ), cSpan.chord)
        assertTrue("no G major span found", spans.any { it.chord == Chord(7, Quality.MAJ) })
        // C comes before G.
        val cStart = spans.first { it.chord == Chord(0, Quality.MAJ) }.startFrac
        val gStart = spans.first { it.chord == Chord(7, Quality.MAJ) }.startFrac
        assertTrue("C should precede G", cStart < gStart)
    }

    /**
     * Beat-synchronous mode must not over-segment a single held chord: a sustained C major with a
     * tempo supplied should coalesce to one span, not a string of them.
     */
    @Test fun beatSyncHoldsOneChordAsOneSpan() {
        val result = ChordDetector.detect(triad(0, Quality.MAJ, seconds = 6.0), BeatEstimate(120f, 0f))
        assertNotNull("detector returned null", result)
        assertEquals("a held chord should be a single span", 1, result!!.spans.size)
        assertEquals(Chord(0, Quality.MAJ), result.spans[0].chord)
    }

    /** Beat-synchronous mode still segments a real change into the two chords, in order. */
    @Test fun beatSyncSegmentsTwoChords() {
        val result = ChordDetector.detect(
            progression(0 to Quality.MAJ, 7 to Quality.MAJ, seconds = 8.0),
            BeatEstimate(120f, 0f),
        )
        assertNotNull("detector returned null for C→G", result)
        val spans = result!!.spans
        val cStart = spans.first { it.chord == Chord(0, Quality.MAJ) }.startFrac
        val gStart = spans.first { it.chord == Chord(7, Quality.MAJ) }.startFrac
        assertTrue("C should precede G", cStart < gStart)
        // The change should land near the middle where the chords actually switch.
        assertTrue("boundary not near mid-clip (G starts at $gStart)", gStart in 0.4f..0.6f)
    }

    /**
     * Self-transition smoothing must curb spurious changes without swallowing a genuine short chord:
     * a clear 2-beat G between two runs of C has to survive as its own span.
     */
    @Test fun beatSyncKeepsAShortRealChord() {
        val total = (5.0 * sampleRate).toInt()
        val a = (2.0 * sampleRate).toInt() // 2 s of C  (4 beats @120)
        val b = (3.0 * sampleRate).toInt() // 1 s of G  (2 beats), then 2 s of C
        val pcm = ShortArray(total)
        renderTriad(pcm, 0, a, root = 0, quality = Quality.MAJ)      // C
        renderTriad(pcm, a, b, root = 7, quality = Quality.MAJ)      // G (short)
        renderTriad(pcm, b, total, root = 0, quality = Quality.MAJ)  // C
        val result = ChordDetector.detect(decoded(pcm), BeatEstimate(120f, 0f))
        assertNotNull("detector returned null", result)
        assertTrue("short G chord was smoothed away", result!!.spans.any { it.chord == Chord(7, Quality.MAJ) })
    }

    /**
     * A chord played quieter than the loud sections must still register, not drop to a blank gap.
     * The quiet G here sits below the old 0.12 gate but above the loosened one. (Level only affects
     * the silence gate — chroma correlation is per-beat normalised, so the quiet G still classifies.)
     */
    @Test fun looserGateDetectsAQuietChord() {
        val total = (6.0 * sampleRate).toInt()
        val a = (4.0 * sampleRate).toInt()
        val pcm = ShortArray(total)
        renderTriad(pcm, 0, a, root = 0, quality = Quality.MAJ)                    // loud C (sets the median)
        renderTriad(pcm, a, total, root = 7, quality = Quality.MAJ, level = 0.09)  // quiet G
        val result = ChordDetector.detect(decoded(pcm), BeatEstimate(120f, 0f))
        assertNotNull("detector returned null", result)
        assertTrue("quiet chord was gated to a gap", result!!.spans.any { it.chord == Chord(7, Quality.MAJ) })
    }

    /** A real major-7th chord (F A C E) should be named Fmaj7, not read as the Am triad inside it. */
    @Test fun detectsFMaj7() {
        val total = (4.0 * sampleRate).toInt()
        val pcm = ShortArray(total)
        renderSeventh(pcm, 0, total, root = 5, quality = Quality.MAJ7) // Fmaj7: F A C E
        val result = ChordDetector.detect(decoded(pcm), BeatEstimate(120f, 0f))
        assertNotNull("detector returned null", result)
        assertEquals("Fmaj7 not recognised", Chord(5, Quality.MAJ7), dominantChord(result!!))
    }

    /** A minor-7th chord (D F A C) should be named Dm7. */
    @Test fun detectsDMin7() {
        val total = (4.0 * sampleRate).toInt()
        val pcm = ShortArray(total)
        renderSeventh(pcm, 0, total, root = 2, quality = Quality.MIN7) // Dm7: D F A C
        val result = ChordDetector.detect(decoded(pcm), BeatEstimate(120f, 0f))
        assertNotNull("detector returned null", result)
        assertEquals("Dm7 not recognised", Chord(2, Quality.MIN7), dominantChord(result!!))
    }

    /** The triad prior must keep a plain C major triad from being upgraded to Cmaj7 by noise. */
    @Test fun plainTriadNotUpgradedToSeventh() {
        val result = ChordDetector.detect(triad(0, Quality.MAJ, seconds = 4.0), BeatEstimate(120f, 0f))
        assertNotNull("detector returned null", result)
        assertEquals("plain C major was upgraded to a seventh", Chord(0, Quality.MAJ), dominantChord(result!!))
    }

    @Test fun chordNamesReadCorrectly() {
        assertEquals("C", Chord(0, Quality.MAJ).name)
        assertEquals("Am", Chord(9, Quality.MIN).name)
        assertEquals("F#", Chord(6, Quality.MAJ).name)
        assertEquals("Fmaj7", Chord(5, Quality.MAJ7).name)
        assertEquals("Dm7", Chord(2, Quality.MIN7).name)
        assertEquals("", Chord.NONE.name)
    }
}
