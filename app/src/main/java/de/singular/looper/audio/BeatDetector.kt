package de.singular.looper.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Result of tempo analysis: a tempo plus the position of a beat to anchor the grid to. */
data class BeatEstimate(val bpm: Float, val downbeatFrac: Float)

/**
 * A small, dependency-free tempo estimator.
 *
 * Pipeline: reduce the audio to an **onset-strength envelope** (spectral flux — where new
 * frequency content appears), **autocorrelate** it, then score each candidate tempo
 * with a **comb filter** that sums the correlation at that tempo's period *and its harmonics*
 * (2×, 3×, 4×). The comb is the key to avoiding octave/metrical errors (e.g. reporting 144 for a
 * 108 BPM song): a wrong multiple lines up with only some of the true beats, while the real tempo
 * lines up with all of them. A gentle prior around 120 BPM breaks remaining ties. A final step
 * catches **compound meters** (6/8 and friends), where the beat is the dotted note rather than the
 * subdivision the sweep locks onto.
 *
 * This is deliberately not a full beat tracker — it assumes a roughly steady tempo, which fits
 * the use case (looping a section for practice), and every value it produces is hand-overridable.
 */
object BeatDetector {

    // Tempo search range for the sweep. Practice material is almost always inside this. The
    // *reported* tempo can still land below MIN_BPM: the compound-meter step divides by 1.5.
    private const val MIN_BPM = 70f
    private const val MAX_BPM = 180f

    // Envelope resolution: one flux sample per this many audio frames. At 44.1 kHz a hop of
    // 512 gives ~86 envelope samples/sec — fine enough for beat spacing, cheap to analyse.
    private const val HOP = 512

    // STFT window length for spectral flux. 2048 @ 44.1 kHz ≈ 46 ms — the usual onset-detection
    // resolution; with the 512 hop that's 75% overlap. Must be a power of two for the radix-2 FFT.
    private const val FFT_SIZE = 2048

    // Sub-hop resolution for the phase (downbeat) search, in envelope samples.
    private const val PHASE_STEP_HOPS = 0.25f

    // Adaptive-novelty window radius, in hops. Each onset is measured against the local mean over
    // roughly this much time either side (~0.19 s at 44.1 kHz), so a sustained-loud passage (a
    // chorus) reads as flat rather than as one giant onset — which used to drag the tempo and the
    // downbeat toward wherever the song was loudest.
    private const val LOCAL_MEAN_HOPS = 16

    // How many harmonics of each candidate period the comb filter sums over.
    private const val COMB_HARMONICS = 4

    // Perceptual tempo prior: a wide log-normal centred here, so octave-ambiguous cases lean
    // toward a humanly plausible tempo without overriding strong evidence.
    private const val PRIOR_CENTER_BPM = 120.0
    private const val PRIOR_WIDTH = 0.9 // in ln-tempo units; larger = gentler

    // Compound-meter test (see the dotted-beat step in [detect]). Both conditions must hold, and
    // each earns its keep: on a set of real tracks with known meters, one alone would have
    // misfired on duple material that the other correctly vetoed.
    //
    //  - PERIODICITY: the dotted lag must be at least as periodic as the beat the sweep found.
    //  - PULSE: a grid at the dotted period must also collect more onset energy *per pulse* — i.e.
    //    it must actually land on the strong hits, not merely repeat.
    private const val COMPOUND_MIN_PERIODICITY = 1.0f
    private const val COMPOUND_MIN_PULSE = 1.10f

    // Floor for the dotted beat. Below this a grid is too coarse to practise against, so we keep
    // the straight beat even if the evidence leans compound.
    private const val MIN_COMPOUND_BPM = 40f

    /**
     * Estimate tempo and a downbeat position for [audio]. Returns null if the clip is too
     * short or too quiet to analyse. Pure CPU work — call it off the main thread.
     */
    fun detect(audio: DecodedAudio): BeatEstimate? {
        val env = onsetEnvelope(audio) ?: return null
        val fps = audio.sampleRate.toFloat() / HOP // envelope samples per second

        val minLag = floor(fps * 60f / MAX_BPM).toInt().coerceAtLeast(1)
        val maxLag = ceil(fps * 60f / MIN_BPM).toInt()
        val maxCombLag = min(env.size - 1, maxLag * COMB_HARMONICS)
        if (maxLag <= minLag || maxCombLag <= minLag) return null

        // Autocorrelate the *zero-mean* envelope so the score reflects periodicity (covariance),
        // not the envelope's positive DC offset — which otherwise adds a near-constant floor at
        // every lag and washes out the peaks. Normalised by overlap length so the count of summed
        // terms doesn't bias the result toward one end of the range.
        val mean = (env.sum() / env.size)
        val cen = FloatArray(env.size) { env[it] - mean }
        val ac = FloatArray(maxCombLag + 1)
        for (lag in 1..maxCombLag) {
            var acc = 0f
            var i = 0
            val end = cen.size - lag
            while (i < end) {
                acc += cen[i] * cen[i + lag]
                i++
            }
            ac[lag] = acc / end
        }

        // Linear interpolation into the autocorrelation for fractional lags.
        fun acAt(x: Float): Float {
            if (x <= 1f) return ac[1]
            if (x >= maxCombLag.toFloat()) return ac[maxCombLag]
            val lo = x.toInt()
            val frac = x - lo
            return ac[lo] * (1f - frac) + ac[lo + 1] * frac
        }

        // Sweep candidate tempos; comb-filter score × prior picks the winner.
        var bestBpm = PRIOR_CENTER_BPM.toFloat()
        var bestScore = Float.NEGATIVE_INFINITY
        var bpm = MIN_BPM
        while (bpm <= MAX_BPM) {
            val period = fps * 60f / bpm
            var comb = 0f
            for (k in 1..COMB_HARMONICS) comb += acAt(period * k)
            val z = ln(bpm / PRIOR_CENTER_BPM.toFloat()) / PRIOR_WIDTH
            val prior = exp(-0.5 * z * z).toFloat()
            val score = comb * prior
            if (score > bestScore) {
                bestScore = score
                bestBpm = bpm
            }
            bpm += 0.25f
        }

        // Compound meter (6/8, 9/8, 12/8): the beat people count is the *dotted* note — three
        // subdivisions long, so one and a half times the period the sweep above lands on. In 6/8 at
        // 100 BPM the sweep reports the quarter note: a real periodicity, but not one anyone taps
        // to, and a grid built on it falls *between* the beats (they coincide only once a bar).
        //
        // Two things must both be true before we move the beat there, and on real material each
        // catches a case the other would get wrong (a 4/4 track can have a periodic dotted lag; a
        // 4/4 track can have a grid that collects energy at 1.5× — neither has both):
        //   1. the dotted lag is at least as *periodic* as the beat, and
        //   2. a grid at the dotted period collects more onset energy *per pulse* — it lands on the
        //      strong hits rather than straddling them.
        val straightPeriod = fps * 60f / bestBpm
        val dottedPeriod = straightPeriod * 1.5f
        val dottedBpm = bestBpm / 1.5f
        if (dottedBpm >= MIN_COMPOUND_BPM &&
            acAt(dottedPeriod) >= acAt(straightPeriod) * COMPOUND_MIN_PERIODICITY &&
            meanPulseEnergy(env, dottedPeriod) >=
            meanPulseEnergy(env, straightPeriod) * COMPOUND_MIN_PULSE
        ) {
            bestBpm = dottedBpm
        }

        // Beat period in envelope samples — kept *fractional*. Stepping the phase search by a
        // rounded integer period would drift ~0.3 hop/beat off the true grid and smear the result.
        val periodHops = fps * 60f / bestBpm

        // Linear interpolation into the envelope for fractional positions.
        fun envAt(x: Float): Float {
            if (x <= 0f) return env[0]
            if (x >= (env.size - 1).toFloat()) return env[env.size - 1]
            val lo = x.toInt()
            val frac = x - lo
            return env[lo] * (1f - frac) + env[lo + 1] * frac
        }

        // Phase: slide a one-period window at sub-hop resolution and keep the sub-hop offset whose
        // pulse train collects the most onset energy across the whole file.
        var bestOffset = 0f
        var bestPhaseScore = Float.NEGATIVE_INFINITY
        var offset = 0f
        while (offset < periodHops) {
            var acc = 0f
            var pos = offset
            while (pos < env.size) {
                acc += envAt(pos)
                pos += periodHops
            }
            if (acc > bestPhaseScore) {
                bestPhaseScore = acc
                bestOffset = offset
            }
            offset += PHASE_STEP_HOPS
        }

        val downbeatFrame = (bestOffset * HOP).toLong()
        val downbeatFrac = (downbeatFrame.toFloat() / audio.frameCount).coerceIn(0f, 1f)
        return BeatEstimate(bestBpm, downbeatFrac)
    }

    /**
     * The onset energy a grid of period [period] collects **per pulse**, at whichever phase suits it
     * best. Where the autocorrelation asks "does this period repeat?", this asks the question the
     * user actually feels: "would a grid at this tempo land *on* the hits?" A grid that straddles
     * them scores poorly however periodic the lag is.
     */
    private fun meanPulseEnergy(env: FloatArray, period: Float): Float {
        var best = 0f
        var offset = 0f
        while (offset < period) {
            var acc = 0f
            var pulses = 0
            var pos = offset
            while (pos < env.size) {
                val lo = pos.toInt()
                val frac = pos - lo
                acc += if (lo >= env.size - 1) env[env.size - 1]
                else env[lo] * (1f - frac) + env[lo + 1] * frac
                pulses++
                pos += period
            }
            if (pulses > 0) {
                val mean = acc / pulses
                if (mean > best) best = mean
            }
            offset += PHASE_STEP_HOPS
        }
        return best
    }

    /**
     * An onset-strength envelope built from **spectral flux**: per STFT frame, the sum over
     * frequency bins of the *rise* in (compressed) magnitude since the previous frame. Rectifying
     * per bin *before* summing is the key — new energy in any band counts even while other bands
     * decay, so a note entering over a fading one still registers (a broadband energy difference
     * would cancel it). Each bin is compared against the max of its neighbours a frame back, which
     * keeps vibrato/tremolo from firing false onsets. The result is then **adaptively whitened** by
     * subtracting a local moving average so only local peaks survive (a sustained-loud passage
     * reads as flat). Channels are mixed to mono. Returns null if there isn't enough signal.
     */
    private fun onsetEnvelope(audio: DecodedAudio): FloatArray? {
        val pcm = audio.pcm
        val channels = audio.channels
        val frames = audio.frameCount
        val hops = frames / HOP
        if (hops < 8) return null

        // Pass 1: spectral flux per hop, from a Hann-windowed FFT centred on each hop.
        val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * PI * it / (FFT_SIZE - 1)).toFloat() }
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        val bins = FFT_SIZE / 2 + 1
        val half = FFT_SIZE / 2
        var prevMag = FloatArray(bins)
        var curMag = FloatArray(bins)
        val flux = FloatArray(hops)

        for (h in 0 until hops) {
            // Window the mono signal centred on this hop; zero-pad past the file edges.
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
            for (k in 0 until bins) curMag[k] = ln(1f + sqrt(re[k] * re[k] + im[k] * im[k]))

            if (h == 0) {
                flux[0] = 0f // no previous frame to diff against
            } else {
                var f = 0f
                for (k in 0 until bins) {
                    // Reference = loudest of the neighbouring bins one frame back (vibrato guard).
                    val lo = max(0, k - 1)
                    val hi = min(bins - 1, k + 1)
                    var ref = prevMag[lo]
                    for (kk in lo + 1..hi) if (prevMag[kk] > ref) ref = prevMag[kk]
                    val d = curMag[k] - ref
                    if (d > 0f) f += d
                }
                flux[h] = f
            }
            val swap = prevMag; prevMag = curMag; curMag = swap
        }

        // Pass 2: subtract a centred local mean (prefix sums make the window O(1)) and rectify, so
        // each onset is measured against its surroundings rather than the song's overall loudness.
        val prefix = DoubleArray(hops + 1)
        for (i in 0 until hops) prefix[i + 1] = prefix[i] + flux[i]

        val env = FloatArray(hops)
        var maxVal = 0f
        for (h in 0 until hops) {
            val lo = max(0, h - LOCAL_MEAN_HOPS)
            val hi = min(hops, h + LOCAL_MEAN_HOPS + 1)
            val localMean = ((prefix[hi] - prefix[lo]) / (hi - lo)).toFloat()
            val v = max(0f, flux[h] - localMean)
            env[h] = v
            if (v > maxVal) maxVal = v
        }
        if (maxVal <= 0f) return null

        for (i in env.indices) env[i] /= maxVal
        return env
    }
}
