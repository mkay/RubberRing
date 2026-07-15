package de.singular.looper.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 0 = C, 1 = C#, … 11 = B. */
private val ROOT_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** A chord quality. Triads plus the two common sevenths; DOM7/SUS are reserved for a later pass. */
enum class Quality { MAJ, MIN, MAJ7, MIN7, NONE }

/** A recognised chord: a root pitch class (0 = C) and a quality. NONE means "no chord / silence". */
data class Chord(val root: Int, val quality: Quality) {
    val name: String
        get() = when (quality) {
            Quality.MAJ -> ROOT_NAMES[root]
            Quality.MIN -> ROOT_NAMES[root] + "m"
            Quality.MAJ7 -> ROOT_NAMES[root] + "maj7"
            Quality.MIN7 -> ROOT_NAMES[root] + "m7"
            Quality.NONE -> ""
        }

    companion object {
        val NONE = Chord(0, Quality.NONE)
    }
}

/** A chord holding over a stretch of the clip, positioned by fraction so it survives re-decode. */
data class ChordSpan(val startFrac: Float, val endFrac: Float, val chord: Chord)

/** The precomputed chord timeline for a clip. Fractions, like [LoopRegion] and [BeatGridState]. */
class ChordTrack(val spans: List<ChordSpan>)

/**
 * A small, dependency-free chord estimator, built to sit beside [BeatDetector].
 *
 * Because this is a looper, the clip is analysed **once, offline** and the result is just a chord
 * timeline the UI highlights under the playhead — no real-time DSP. The front-end is exactly the
 * STFT that [BeatDetector.onsetEnvelope] already uses (Hann window, [FFT_SIZE] 2048, [HOP] 512,
 * mono mixing, radix-2 [Fft]); on top of it:
 *
 *  1. **Chromagram** — fold each frame's magnitude spectrum into a 12-bin pitch-class vector.
 *  2. **Classify** — score each frame against the chord templates (12 roots × {major, minor,
 *     major-7th, minor-7th}) by harmonic correlation, gating near-silent frames to "no chord".
 *  3. **Smooth** — a mode filter over ~0.4 s kills frame-to-frame flicker.
 *  4. **Segment** — coalesce equal neighbours into spans, re-label each span from its *mean*
 *     chroma (steadier than any single frame), and drop spans too short to be real.
 *
 * Honest about accuracy: template matching on clean triads lands ~60–75% of segments and degrades
 * on dense mixes. It is a hand-overridable practice aid, never ground truth — the beat-grid framing.
 *
 * Pure CPU work; call it off the main thread.
 */
object ChordDetector {

    // STFT geometry. A larger window than BeatDetector's onset front-end (2048): pitch classes must
    // be *resolved*, and at 44.1 kHz a 2048 FFT's ~21.5 Hz bins are wider than a semitone below
    // ~250 Hz, smearing bass and low chord tones across neighbouring pitch classes. 8192 gives
    // ~5.4 Hz bins — finer than a semitone down to ~65 Hz, the bottom of our band. Chords change
    // slowly, so a matching 2048 hop (~46 ms) keeps the frame count down without losing boundaries.
    private const val FFT_SIZE = 8192
    private const val HOP = 2048

    // Musical band for the chroma fold: below ~65 Hz is rumble/kick fundamental with little pitch
    // information, above ~2 kHz is mostly harmonics that just smear the pitch classes.
    private const val MIN_FREQ_HZ = 65f
    private const val MAX_FREQ_HZ = 2000f

    // A beat whose in-band energy falls below this fraction of the track's median is called "no
    // chord". Kept low on purpose: it should catch only genuine silence (lead-in/out, breakdowns),
    // not a chord that merely happens to be played quieter than the loud sections — those used to
    // drop out as blank gaps in the lane. True silence sits near zero energy and is still caught.
    private const val ENERGY_GATE = 0.05f

    // Flicker smoothing: take the most common label over a window this wide (each side ~half).
    private const val SMOOTH_SECONDS = 0.4f

    // A chord shorter than this is almost always a smoothing artefact; merge it into its neighbour.
    private const val MIN_SPAN_SECONDS = 0.3f

    // Below this the tempo can't be trusted to place beat lines, so fall back to frame-level.
    private const val MIN_USABLE_BPM = 20f

    // Beat-synchronous smoothing bonus for staying on the same chord from one beat to the next.
    // In correlation units (~[-1, 1]): a switch has to be worth more than this in emission gain to
    // happen, so a lone off-beat or a two-bars-early lean can't drag a boundary, while a genuine
    // change (a sustained correlation swing) still wins easily. Tuned so a clear 2-beat chord holds.
    private const val CHORD_SELF_BIAS = 0.25f

    // Guard against an absurd tempo producing a runaway boundary list.
    private const val MAX_BEATS = 100_000

    private const val CHROMA_BINS = 12
    private const val NO_CHORD = -1 // label sentinel, kept out of the 0 until CHORD_COUNT range

    // Chord vocabulary: 12 roots × {major, minor, major-7th, minor-7th}. A label is root * QUALITIES
    // + qualityIndex, where the index follows the order in [labelToChord].
    private const val QUALITIES = 4
    private const val CHORD_COUNT = 12 * QUALITIES // 48

    // Triad prior: a seventh must out-correlate the best triad by at least this much (in Pearson
    // units) to be chosen. Sevenths share three of their four notes with a triad, so without a nudge
    // toward the simpler chord, noise in the seventh bin would sprinkle spurious 7ths over plain
    // triads. A genuine seventh (its fourth note actually present) clears this margin easily.
    private const val SEVENTH_BIAS = 0.06f

    // Harmonic template model. Each chord tone excites a series of overtones, so a real chord
    // deposits energy well beyond its notes. Modelling that pattern — and scoring the whole 12-bin
    // chroma by correlation — lets quality be decided from the full harmonic fingerprint rather than
    // a single (noisy) bin, which is where bare masks flip e.g. D into Dm. Offsets give the pitch
    // class of the n-th harmonic relative to its fundamental: harmonics 1/2/4 are octaves (0), 3/6 a
    // fifth (7), 5 a major third (4). The 7th is dropped — it lands about a third of a semitone flat
    // and only muddies the pitch classes. Weights roll off.
    private val HARMONIC_OFFSETS = intArrayOf(0, 0, 7, 0, 4, 7)
    private val HARMONIC_WEIGHTS = floatArrayOf(1f, 0.6f, 0.36f, 0.216f, 0.13f, 0.078f)

    // The [CHORD_COUNT] chord templates, each mean-centred and unit-normalised so a dot product with
    // a mean-centred chroma vector is a Pearson correlation.
    private val TEMPLATES: Array<FloatArray> = Array(CHORD_COUNT) { label ->
        val t = FloatArray(CHROMA_BINS)
        for (tone in tonesFor(label)) { // tones are non-negative, so a plain modulo is safe
            for (n in HARMONIC_OFFSETS.indices) {
                t[(tone + HARMONIC_OFFSETS[n]) % CHROMA_BINS] += HARMONIC_WEIGHTS[n]
            }
        }
        centreAndNormalise(t)
        t
    }

    // Per-label prior added to each template's correlation: 0 for triads, −[SEVENTH_BIAS] for sevenths.
    private val QUALITY_BIAS: FloatArray = FloatArray(CHORD_COUNT) { label ->
        when (label % QUALITIES) { 0, 1 -> 0f; else -> -SEVENTH_BIAS }
    }

    /** The chord tones (as pitch classes, possibly ≥12) for a template [label]. */
    private fun tonesFor(label: Int): IntArray {
        val root = label / QUALITIES
        return when (label % QUALITIES) {
            0 -> intArrayOf(root, root + 4, root + 7)          // major
            1 -> intArrayOf(root, root + 3, root + 7)          // minor
            2 -> intArrayOf(root, root + 4, root + 7, root + 11) // major 7th
            else -> intArrayOf(root, root + 3, root + 7, root + 10) // minor 7th
        }
    }

    /**
     * Estimate a chord timeline for [audio]. Returns null if the clip is too short or too quiet
     * to analyse. Pure CPU work — run it off the main thread.
     *
     * When a usable [beat] estimate is supplied, segmentation is **beat-synchronous**: chroma is
     * averaged between the beat lines and one chord is chosen per beat, so boundaries snap to
     * musical positions and the classifier can't invent a change in the middle of a held chord.
     * Without it (or at an implausible tempo), it falls back to frame-level segmentation.
     */
    fun detect(audio: DecodedAudio, beat: BeatEstimate? = null): ChordTrack? {
        val frames = audio.frameCount
        val hops = frames / HOP
        if (hops < 8) return null

        val (chroma, energy) = chromagram(audio, hops) ?: return null

        // Energy gate from the median frame energy: robust to a few very loud or very quiet frames.
        val energyFloor = median(energy) * ENERGY_GATE
        val fps = audio.sampleRate.toFloat() / HOP

        val spans = if (beat != null && beat.bpm >= MIN_USABLE_BPM)
            segmentByBeats(chroma, energy, energyFloor, hops, frames, fps, beat)
        else
            segmentByFrames(chroma, energy, energyFloor, hops, frames, fps)
        return if (spans.isEmpty()) null else ChordTrack(spans)
    }

    /** Frame-level segmentation: classify each frame, mode-smooth, coalesce, merge tiny spans. */
    private fun segmentByFrames(
        chroma: FloatArray, energy: FloatArray, energyFloor: Float, hops: Int, frames: Int, fps: Float,
    ): List<ChordSpan> {
        val labels = IntArray(hops) { h ->
            if (energy[h] < energyFloor) NO_CHORD else classify(chroma, h)
        }
        val smoothed = modeFilter(labels, radius = (SMOOTH_SECONDS * fps / 2f).roundToInt().coerceAtLeast(1))
        return segment(smoothed, chroma, frames, minHops = (MIN_SPAN_SECONDS * fps).roundToInt().coerceAtLeast(1))
    }

    /**
     * Beat-synchronous segmentation. Lay down the beat grid implied by [beat], average the chroma
     * within each beat and classify it, iron out lone-beat slips with a small mode filter, then
     * coalesce equal neighbours — a chord held for several beats becomes one span, and boundaries
     * land on beats instead of wherever the frame classifier happened to wobble.
     */
    private fun segmentByBeats(
        chroma: FloatArray, energy: FloatArray, energyFloor: Float,
        hops: Int, frames: Int, fps: Float, beat: BeatEstimate,
    ): List<ChordSpan> {
        val beatHops = 60f / beat.bpm * fps
        if (beatHops < 1f) return segmentByFrames(chroma, energy, energyFloor, hops, frames, fps)
        val downbeatHop = beat.downbeatFrac * frames / HOP

        // Beat boundaries in hops: 0, then every beat line strictly inside the clip, then hops.
        val boundaries = ArrayList<Int>()
        boundaries.add(0)
        var k = ceil((0.5f - downbeatHop) / beatHops).toInt()
        while (boundaries.size < MAX_BEATS) {
            val pos = (downbeatHop + k * beatHops).roundToInt()
            if (pos >= hops) break
            if (pos > boundaries.last()) boundaries.add(pos)
            k++
        }
        if (boundaries.last() < hops) boundaries.add(hops)

        // Per-beat emissions: how well the beat's mean chroma correlates with each template, or a
        // gate flag when the beat is near-silent (forced to NO_CHORD downstream).
        val beatCount = boundaries.size - 1
        val gated = BooleanArray(beatCount)
        val emissions = Array(beatCount) { FloatArray(CHORD_COUNT) }
        val mean = FloatArray(CHROMA_BINS)
        for (i in 0 until beatCount) {
            val lo = boundaries[i]
            val hi = boundaries[i + 1]
            if (hi <= lo) { gated[i] = true; continue }
            mean.fill(0f)
            var e = 0f
            for (h in lo until hi) {
                val off = h * CHROMA_BINS
                for (b in 0 until CHROMA_BINS) mean[b] += chroma[off + b]
                e += energy[h]
            }
            if (e / (hi - lo) < energyFloor) gated[i] = true else correlations(mean, emissions[i])
        }

        val smoothed = viterbi(emissions, gated)

        // Coalesce runs of equal beats into spans, positioned by fraction.
        val spans = ArrayList<ChordSpan>()
        var i = 0
        while (i < beatCount) {
            var j = i
            while (j + 1 < beatCount && smoothed[j + 1] == smoothed[i]) j++
            if (smoothed[i] != NO_CHORD) {
                val startFrac = (boundaries[i].toLong() * HOP).toFloat() / frames
                val endFrac = if (j + 1 == beatCount) 1f else (boundaries[j + 1].toLong() * HOP).toFloat() / frames
                spans.add(ChordSpan(startFrac.coerceIn(0f, 1f), endFrac.coerceIn(0f, 1f), labelToChord(smoothed[i])))
            }
            i = j + 1
        }
        return spans
    }

    /**
     * Build the chromagram: for each hop a 12-bin pitch-class vector plus that frame's total in-band
     * energy (used by the silence gate). Returns null if nothing lands in the musical band.
     */
    private fun chromagram(audio: DecodedAudio, hops: Int): Pair<FloatArray, FloatArray>? {
        val pcm = audio.pcm
        val channels = audio.channels
        val frames = audio.frameCount
        val sampleRate = audio.sampleRate

        // Precompute the pitch class of every FFT bin once (depends only on the sample rate); bins
        // outside the musical band are marked -1 and skipped.
        val bins = FFT_SIZE / 2 + 1
        val binPitchClass = IntArray(bins) { -1 }
        for (k in 1 until bins) {
            val freq = k.toFloat() * sampleRate / FFT_SIZE
            if (freq < MIN_FREQ_HZ || freq > MAX_FREQ_HZ) continue
            // MIDI note number; 69 = A4 = 440 Hz. Pitch class = note mod 12, with 69 mod 12 = 9 = A,
            // so index 0 falls on C, matching ROOT_NAMES.
            val midi = 69.0 + 12.0 * (ln((freq / 440f).toDouble()) / ln(2.0))
            binPitchClass[k] = ((midi.roundToInt() % 12) + 12) % 12
        }

        val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * PI * it / (FFT_SIZE - 1)).toFloat() }
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        val half = FFT_SIZE / 2

        val chroma = FloatArray(hops * CHROMA_BINS)
        val energy = FloatArray(hops)
        var anyEnergy = false

        for (h in 0 until hops) {
            val center = h * HOP
            for (n in 0 until FFT_SIZE) {
                val idx = center - half + n
                var mono = 0f
                if (idx in 0 until frames) {
                    val base = idx * channels
                    var sum = 0
                    for (c in 0 until channels) sum += pcm[base + c].toInt()
                    mono = sum.toFloat() / channels
                }
                re[n] = mono * window[n]
                im[n] = 0f
            }
            Fft.transform(re, im)

            val off = h * CHROMA_BINS
            var frameEnergy = 0f
            for (k in 1 until bins) {
                val pc = binPitchClass[k]
                if (pc < 0) continue
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                chroma[off + pc] += mag
                frameEnergy += mag
            }
            energy[h] = frameEnergy
            if (frameEnergy > 0f) {
                anyEnergy = true
                // Normalise the frame so loud and soft passages classify on the same footing.
                for (b in 0 until CHROMA_BINS) chroma[off + b] /= frameEnergy
            }
        }

        return if (anyEnergy) chroma to energy else null
    }

    /** Best triad for the frame at [h] — see [bestLabel]. */
    private fun classify(chroma: FloatArray, h: Int): Int = bestLabel(chroma, h * CHROMA_BINS)

    /** Turn a label back into a chord. */
    private fun labelToChord(label: Int): Chord {
        if (label == NO_CHORD) return Chord.NONE
        val quality = when (label % QUALITIES) {
            0 -> Quality.MAJ
            1 -> Quality.MIN
            2 -> Quality.MAJ7
            else -> Quality.MIN7
        }
        return Chord(label / QUALITIES, quality)
    }

    /** Most common label within ±[radius] of each position — categorical smoothing that kills flicker. */
    private fun modeFilter(labels: IntArray, radius: Int): IntArray {
        val n = labels.size
        val out = IntArray(n)
        val counts = IntArray(CHORD_COUNT) // chord labels; NO_CHORD tracked separately
        for (i in 0 until n) {
            counts.fill(0)
            var noChord = 0
            val lo = max(0, i - radius)
            val hi = min(n - 1, i + radius)
            for (j in lo..hi) {
                val l = labels[j]
                if (l == NO_CHORD) noChord++ else counts[l]++
            }
            var bestLabel = NO_CHORD
            var bestCount = noChord
            for (l in 0 until CHORD_COUNT) if (counts[l] > bestCount) { bestCount = counts[l]; bestLabel = l }
            out[i] = bestLabel
        }
        return out
    }

    /**
     * Coalesce runs of equal labels into spans, re-labelling each run from its *mean* chroma (a
     * steadier read than any single frame), then merge runs shorter than [minHops] into whichever
     * neighbour they resemble most. NO_CHORD runs are dropped, leaving gaps in the lane.
     */
    private fun segment(labels: IntArray, chroma: FloatArray, frames: Int, minHops: Int): List<ChordSpan> {
        val n = labels.size
        if (n == 0) return emptyList()

        // 1. Runs of equal (smoothed) label.
        data class Run(var startHop: Int, var endHop: Int, var label: Int) // endHop exclusive

        val runs = ArrayList<Run>()
        var runStart = 0
        for (i in 1..n) {
            if (i == n || labels[i] != labels[runStart]) {
                runs.add(Run(runStart, i, labels[runStart]))
                runStart = i
            }
        }

        // 2. Re-label each non-silent run from its mean chroma.
        val mean = FloatArray(CHROMA_BINS)
        for (run in runs) {
            if (run.label == NO_CHORD) continue
            mean.fill(0f)
            for (h in run.startHop until run.endHop) {
                val off = h * CHROMA_BINS
                for (b in 0 until CHROMA_BINS) mean[b] += chroma[off + b]
            }
            run.label = classifyVector(mean)
        }

        // 3. Merge tiny runs into the stronger-matching neighbour, then coalesce equal neighbours.
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < runs.size) {
                val run = runs[i]
                if (run.endHop - run.startHop < minHops && runs.size > 1) {
                    val prev = if (i > 0) runs[i - 1] else null
                    val next = if (i < runs.size - 1) runs[i + 1] else null
                    val into = when {
                        prev == null -> next!!
                        next == null -> prev
                        (prev.endHop - prev.startHop) >= (next.endHop - next.startHop) -> prev
                        else -> next
                    }
                    into.startHop = min(into.startHop, run.startHop)
                    into.endHop = max(into.endHop, run.endHop)
                    runs.removeAt(i)
                    changed = true
                } else {
                    i++
                }
            }
            // Coalesce neighbours that now share a label.
            var j = 0
            while (j < runs.size - 1) {
                if (runs[j].label == runs[j + 1].label) {
                    runs[j].endHop = runs[j + 1].endHop
                    runs.removeAt(j + 1)
                    changed = true
                } else {
                    j++
                }
            }
        }

        // 4. Emit non-silent runs as fraction-positioned spans.
        val spans = ArrayList<ChordSpan>()
        for ((index, run) in runs.withIndex()) {
            if (run.label == NO_CHORD) continue
            val startFrac = (run.startHop.toLong() * HOP).toFloat() / frames
            val endFrac = if (index == runs.size - 1) 1f else (run.endHop.toLong() * HOP).toFloat() / frames
            spans.add(ChordSpan(startFrac.coerceIn(0f, 1f), endFrac.coerceIn(0f, 1f), labelToChord(run.label)))
        }
        return spans
    }

    /** Classify a raw 12-bin chroma vector — the segment-level version of [classify]. */
    private fun classifyVector(v: FloatArray): Int = bestLabel(v, 0)

    /**
     * Best-matching chord for the 12 chroma bins of [v] starting at [off]: the template with the
     * highest biased Pearson correlation. Mean-centring makes the match about the *shape* of the
     * chroma, not its overall level; the seventh templates carry a small negative prior so a triad
     * wins ties. Returns a label in 0 until [CHORD_COUNT].
     */
    private fun bestLabel(v: FloatArray, off: Int): Int {
        var mean = 0f
        for (b in 0 until CHROMA_BINS) mean += v[off + b]
        mean /= CHROMA_BINS
        // A flat/silent vector has no shape to match; the caller has already gated most of these.
        var variance = 0f
        for (b in 0 until CHROMA_BINS) { val c = v[off + b] - mean; variance += c * c }
        if (variance <= 0f) return 0
        val inv = 1f / sqrt(variance)
        var best = 0
        var bestScore = -Float.MAX_VALUE
        for (label in 0 until CHORD_COUNT) {
            val tpl = TEMPLATES[label]
            var dot = 0f
            for (b in 0 until CHROMA_BINS) dot += (v[off + b] - mean) * tpl[b]
            val score = dot * inv + QUALITY_BIAS[label]
            if (score > bestScore) { bestScore = score; best = label }
        }
        return best
    }

    /**
     * Fill [out] (size [CHORD_COUNT]) with the biased Pearson correlation of chroma [v] against each
     * template — the per-beat emission scores for the Viterbi pass, with the seventh prior applied.
     */
    private fun correlations(v: FloatArray, out: FloatArray) {
        var mean = 0f
        for (b in 0 until CHROMA_BINS) mean += v[b]
        mean /= CHROMA_BINS
        var variance = 0f
        for (b in 0 until CHROMA_BINS) { val c = v[b] - mean; variance += c * c }
        val inv = if (variance > 0f) 1f / sqrt(variance) else 0f
        for (label in 0 until CHORD_COUNT) {
            val tpl = TEMPLATES[label]
            var dot = 0f
            for (b in 0 until CHROMA_BINS) dot += (v[b] - mean) * tpl[b]
            out[label] = dot * inv + QUALITY_BIAS[label]
        }
    }

    /**
     * Viterbi decode of the per-beat chord sequence. States are the chord labels plus a NONE state;
     * emission is the beat's correlation with each chord (a large bias forces NONE on gated beats
     * and forbids it elsewhere), and staying on a chord earns [CHORD_SELF_BIAS]. The best path
     * therefore only leaves a chord when the evidence for the next one is strong and sustained —
     * ironing out lone-beat slips and premature switches without a fixed smoothing window.
     */
    private fun viterbi(emissions: Array<FloatArray>, gated: BooleanArray): IntArray {
        val n = emissions.size
        if (n == 0) return IntArray(0)
        val noneState = CHORD_COUNT
        val states = CHORD_COUNT + 1
        val neg = -1e9f
        fun emit(t: Int, s: Int): Float =
            if (gated[t]) { if (s == noneState) 0f else neg } else { if (s == noneState) neg else emissions[t][s] }

        val dp = Array(n) { FloatArray(states) }
        val back = Array(n) { IntArray(states) }
        for (s in 0 until states) dp[0][s] = emit(0, s)
        for (t in 1 until n) {
            for (s in 0 until states) {
                var bestPrev = 0
                var bestVal = Float.NEGATIVE_INFINITY
                for (p in 0 until states) {
                    val v = dp[t - 1][p] + if (p == s) CHORD_SELF_BIAS else 0f
                    if (v > bestVal) { bestVal = v; bestPrev = p }
                }
                dp[t][s] = bestVal + emit(t, s)
                back[t][s] = bestPrev
            }
        }
        var best = 0
        var bestVal = Float.NEGATIVE_INFINITY
        for (s in 0 until states) if (dp[n - 1][s] > bestVal) { bestVal = dp[n - 1][s]; best = s }
        val path = IntArray(n)
        var s = best
        for (t in n - 1 downTo 0) { path[t] = s; s = back[t][s] }
        return IntArray(n) { if (path[it] == noneState) NO_CHORD else path[it] }
    }

    /** Mean-centre and unit-normalise a template in place, so a dot product is a correlation. */
    private fun centreAndNormalise(t: FloatArray) {
        var mean = 0f
        for (b in t.indices) mean += t[b]
        mean /= t.size
        var norm = 0f
        for (b in t.indices) { t[b] -= mean; norm += t[b] * t[b] }
        val inv = if (norm > 0f) 1f / sqrt(norm) else 0f
        for (b in t.indices) t[b] *= inv
    }

    private fun median(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.copyOf().also { it.sort() }
        return sorted[sorted.size / 2]
    }
}
