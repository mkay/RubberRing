package de.singular.looper.audio

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Result of tempo analysis: a tempo plus the position of a beat to anchor the grid to. */
data class BeatEstimate(val bpm: Float, val downbeatFrac: Float)

/**
 * A small, dependency-free tempo estimator.
 *
 * Pipeline: reduce the audio to an **onset-strength envelope** (rectified flux of log energy —
 * where the sound suddenly gets louder), **autocorrelate** it, then score each candidate tempo
 * with a **comb filter** that sums the correlation at that tempo's period *and its harmonics*
 * (2×, 3×, 4×). The comb is the key to avoiding octave/metrical errors (e.g. reporting 144 for a
 * 108 BPM song): a wrong multiple lines up with only some of the true beats, while the real tempo
 * lines up with all of them. A gentle prior around 120 BPM breaks remaining ties.
 *
 * This is deliberately not a full beat tracker — it assumes a roughly steady tempo, which fits
 * the use case (looping a section for practice), and every value it produces is hand-overridable.
 */
object BeatDetector {

    // Tempo search range. Practice material is almost always inside this.
    private const val MIN_BPM = 70f
    private const val MAX_BPM = 180f

    // Envelope resolution: one energy sample per this many audio frames. At 44.1 kHz a hop of
    // 512 gives ~86 envelope samples/sec — fine enough for beat spacing, cheap to analyse.
    private const val HOP = 512

    // How many harmonics of each candidate period the comb filter sums over.
    private const val COMB_HARMONICS = 4

    // Perceptual tempo prior: a wide log-normal centred here, so octave-ambiguous cases lean
    // toward a humanly plausible tempo without overriding strong evidence.
    private const val PRIOR_CENTER_BPM = 120.0
    private const val PRIOR_WIDTH = 0.9 // in ln-tempo units; larger = gentler

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

        // Autocorrelation for every lag the comb might reach, normalised by overlap length so
        // the count of summed terms doesn't bias the result toward one end of the range.
        val ac = FloatArray(maxCombLag + 1)
        for (lag in 1..maxCombLag) {
            var acc = 0f
            var i = 0
            val end = env.size - lag
            while (i < end) {
                acc += env[i] * env[i + lag]
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

        val bestLag = (fps * 60f / bestBpm).roundToInt().coerceIn(minLag, maxLag)

        // Phase: slide a one-period window and keep the offset with the most onset energy.
        var bestOffset = 0
        var bestPhaseScore = Float.NEGATIVE_INFINITY
        for (offset in 0 until bestLag) {
            var acc = 0f
            var i = offset
            while (i < env.size) {
                acc += env[i]
                i += bestLag
            }
            if (acc > bestPhaseScore) {
                bestPhaseScore = acc
                bestOffset = offset
            }
        }

        val downbeatFrame = bestOffset.toLong() * HOP
        val downbeatFrac = (downbeatFrame.toFloat() / audio.frameCount).coerceIn(0f, 1f)
        return BeatEstimate(bestBpm, downbeatFrac)
    }

    /**
     * Rectified flux of log short-time energy: per hop, the increase over the previous hop.
     * Log compression keeps a few loud transients from dominating the periodicity estimate.
     * Channels are mixed to mono. Returns null if there isn't enough signal to work with.
     */
    private fun onsetEnvelope(audio: DecodedAudio): FloatArray? {
        val pcm = audio.pcm
        val channels = audio.channels
        val frames = audio.frameCount
        val hops = frames / HOP
        if (hops < 8) return null

        val env = FloatArray(hops)
        var prevLogE = 0f
        var maxVal = 0f
        for (h in 0 until hops) {
            var energy = 0f
            val frameStart = h * HOP
            for (f in 0 until HOP) {
                val base = (frameStart + f) * channels
                var sum = 0
                for (c in 0 until channels) sum += pcm[base + c].toInt()
                val mono = sum.toFloat() / channels
                energy += mono * mono
            }
            energy /= HOP
            val logE = ln(1f + energy)
            val flux = max(0f, logE - prevLogE)
            env[h] = flux
            prevLogE = logE
            if (flux > maxVal) maxVal = flux
        }
        if (maxVal <= 0f) return null

        for (i in env.indices) env[i] /= maxVal
        return env
    }
}
