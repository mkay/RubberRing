// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * WSOLA (Waveform-Similarity Overlap-Add) time-stretch: change a clip's *duration* without
 * changing its pitch. Pure Kotlin, no dependencies. Operates on interleaved 16-bit PCM and
 * returns a new buffer; played back at the normal rate the result sounds slower (or faster)
 * with the pitch intact.
 *
 * Applied offline to a (short) loop region, so it can afford a generous similarity search —
 * which is the whole point. Naive overlap-add drops overlapping grains at fixed positions and
 * the phase mismatch between them smears transients and adds an echoey "phasiness" (this is
 * roughly what Android's built-in `PlaybackParams` does, and why it artifacts). WSOLA instead
 * *searches* a small window for the grain position whose waveform best continues the previously
 * emitted output, so consecutive grains line up in phase — attacks stay crisp and sustained
 * tones stay clean.
 *
 * [speed] < 1 slows down (longer output); > 1 speeds up. [speed] == 1 returns the input as-is.
 * Pure CPU work — call it off the main thread. A whole song takes long enough that the caller may
 * want to give up part-way: [checkCancelled] runs once per grain of the search and may throw to
 * abandon the stretch.
 */
object TimeStretch {

    // Grain length ≈ 46 ms (50% overlap → synthesis hop ≈ 23 ms); search radius ≈ 15 ms, enough
    // to slide a grain across a pitch period down to ~65 Hz. All derived from the sample rate.
    private const val GRAIN_SECONDS = 0.046f
    private const val SEARCH_SECONDS = 0.015f

    // The search runs coarse-then-fine: every candidate offset is scored on a copy of the signal
    // averaged down by this factor, and only the neighbourhood of the winner is scored at full
    // resolution. That is about DECIMATION² less work — what makes stretching a whole song take
    // seconds rather than minutes — and the fine pass still places the grain to the sample.
    private const val DECIMATION = 4

    fun stretch(
        pcm: ShortArray,
        channels: Int,
        sampleRate: Int,
        speed: Float,
        checkCancelled: () -> Unit = {},
    ): ShortArray {
        if (speed == 1f || channels < 1 || pcm.size < channels * 4) return pcm
        val frames = pcm.size / channels

        val seg = evenize((sampleRate * GRAIN_SECONDS).roundToInt().coerceAtLeast(64))
        val hop = seg / 2 // synthesis hop == overlap length
        val delta = (sampleRate * SEARCH_SECONDS).roundToInt().coerceAtLeast(1)
        if (frames < seg + delta + 2 || hop < 1) return pcm

        // The output is exactly frames / speed long, so a loop keeps its length at any speed: a
        // loop that comes round even a few ms early stumbles at the seam on every pass. It is
        // built as the first grain's leading half, [grains] whole grains, and an [ending] taken
        // straight from the end of the input — so the output ends on the input's last sample,
        // just as it starts on its first, and the seam joins exactly what the original joins.
        val outFrames = (frames / speed.toDouble()).roundToInt()
        // hop <= ending < 2 * hop: long enough to carry the closing cross-fade and still fit.
        val grains = (outFrames - 2 * hop) / hop
        if (grains < 1) return pcm
        val ending = outFrames - (grains + 1) * hop
        val endStart = frames - ending // where the ending is read from the input
        if (endStart < hop) return pcm // a loop of a few dozen ms: nothing to stretch it with

        // Nominal grain positions, spaced so the last grain's natural continuation lands exactly on
        // [endStart]. Because the ending plays at 1×, the step is a hair off hop * speed, which is
        // inaudible; computing each from its index keeps rounding from drifting.
        val analysisStep = (endStart - hop).toDouble() / grains
        val nominal = IntArray(grains + 1) { (it * analysisStep).roundToInt() }

        // Periodic Hann so two 50%-overlapped windows sum to exactly 1 (constant-overlap-add).
        val hann = FloatArray(seg) { (0.5 - 0.5 * cos(2.0 * PI * it / seg)).toFloat() }

        // Mono downmix used only for the (cheap) similarity search.
        val mono = FloatArray(frames)
        run {
            var i = 0
            for (f in 0 until frames) {
                var s = 0
                for (c in 0 until channels) s += pcm[i++]
                mono[f] = s.toFloat() / channels
            }
        }
        // The same, averaged over each DECIMATION frames: the averaging is a crude low-pass, so
        // the coarse pass compares the waveform's shape rather than aliased treble.
        val coarse = FloatArray(frames / DECIMATION) { i ->
            var s = 0f
            for (j in 0 until DECIMATION) s += mono[i * DECIMATION + j]
            s / DECIMATION
        }

        // Find the coarse winner, then rescore the offsets around it at full resolution. The
        // neighbourhood spans a whole coarse step either side, which absorbs the rounding of
        // positions down to coarse indices.
        fun search(base: Int, target: Int, range: IntRange): Int {
            val step = DECIMATION
            val len = hop / step
            var bestK = 0
            var bestScore = Float.NEGATIVE_INFINITY
            var k = range.first
            while (k <= range.last) {
                val score = similarity(coarse, (base + k) / step, target / step, len)
                if (score > bestScore) { bestScore = score; bestK = k }
                k += step
            }
            val fine = max(range.first, bestK - step)..min(range.last, bestK + step)
            return bestOffset(mono, base, target, hop, fine)
        }

        val out = ShortArray(outFrames * channels)
        var outSample = 0
        val tail = FloatArray(hop * channels) // windowed second half of the previous grain

        fun writeSample(v: Float) { if (outSample < out.size) out[outSample++] = clampToShort(v) }

        // Cross-fade this grain's first half onto [tail], emit those hop frames, then stash its
        // windowed second half as the next tail. [rawFirstHalf] skips the window so the very first
        // grain starts at full amplitude (no fade-in at the loop's start edge).
        fun emitGrain(p: Int, rawFirstHalf: Boolean) {
            for (f in 0 until hop) {
                val w = if (rawFirstHalf) 1f else hann[f]
                val src = (p + f) * channels
                for (c in 0 until channels) writeSample(tail[f * channels + c] + pcm[src + c] * w)
            }
            for (f in 0 until hop) {
                val w = hann[hop + f]
                val src = (p + hop + f) * channels
                for (c in 0 until channels) tail[f * channels + c] = pcm[src + c] * w
            }
        }

        // Fixing both edges costs one join that the similarity search can't choose freely: the
        // search's jumps must add up to exactly the length it has to make up, so whatever phase
        // is left over lands somewhere. The chain is therefore built twice — forward from the
        // start and backward from the ending, each grain matched to its neighbour — and the two
        // meet at whichever grain boundary has the least audible error, such as a quiet gap or a
        // spot where the waveforms already line up. On a sustained tone it would otherwise be a
        // dip at the seam.
        fun searchRange(ia: Int) = max(-delta, -ia)..min(delta, frames - seg - ia)
        val fwd = IntArray(grains + 1) // fwd[0] = 0: the loop starts on its own first sample
        for (k in 1..grains) {
            checkCancelled()
            val ia = nominal[k]
            fwd[k] = ia + search(ia, fwd[k - 1] + hop, searchRange(ia))
        }
        // bwd[k] is grain k chosen so its continuation matches the start of grain k + 1; the
        // ending acts as grain grains + 1, read from [endStart].
        val bwd = IntArray(grains + 2)
        bwd[grains + 1] = endStart
        for (k in grains downTo 1) {
            checkCancelled()
            val ia = nominal[k]
            bwd[k] = ia + search(ia + hop, bwd[k + 1], searchRange(ia))
        }
        // Join at k: forward grains before it, backward grains from it on.
        val meet = (1..grains + 1).minBy { k -> mismatch(mono, fwd[k - 1] + hop, bwd[k], hop) }

        emitGrain(0, rawFirstHalf = true)
        for (k in 1..grains) emitGrain(if (k < meet) fwd[k] else bwd[k], rawFirstHalf = false)

        // Cross-fade the last grain's tail into the ending, then play the ending out raw — no
        // fade-out, the loop's end edge stays at full amplitude.
        for (f in 0 until hop) {
            val src = (endStart + f) * channels
            for (c in 0 until channels) writeSample(tail[f * channels + c] + pcm[src + c] * hann[f])
        }
        for (f in endStart + hop until frames) {
            val src = f * channels
            for (c in 0 until channels) writeSample(pcm[src + c].toFloat())
        }

        return if (outSample == out.size) out else out.copyOf(outSample)
    }

    /**
     * The offset within [range] whose [len]-frame window at [base] + offset best matches the
     * window at [target] (normalised cross-correlation on the mono signal). Forward, [target] is
     * the previous grain's natural continuation; backward, [base] is a grain's continuation and
     * [target] the next grain. Either way it phase-aligns neighbouring grains.
     */
    private fun bestOffset(mono: FloatArray, base: Int, target: Int, len: Int, range: IntRange): Int {
        var bestK = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (k in range) {
            val score = similarity(mono, base + k, target, len)
            if (score > bestScore) { bestScore = score; bestK = k }
        }
        return bestK
    }

    /** How well the [len]-frame window at [from] continues the one at [target] (see [bestOffset]). */
    private fun similarity(signal: FloatArray, from: Int, target: Int, len: Int): Float {
        var dot = 0f
        var energy = 0f
        var j = 0
        while (j < len) {
            val a = signal[from + j]
            dot += a * signal[target + j]
            energy += a * a
            j++
        }
        return dot / sqrt(energy + 1e-3f)
    }

    /**
     * How audibly two [len]-frame windows differ: the energy of their difference. Unlike the
     * search's correlation it is absolute, so a join in a quiet passage counts as nearly free.
     */
    private fun mismatch(mono: FloatArray, a: Int, b: Int, len: Int): Float {
        var sum = 0f
        for (j in 0 until len) {
            val d = mono[a + j] - mono[b + j]
            sum += d * d
        }
        return sum
    }

    private fun clampToShort(v: Float): Short {
        val i = v.roundToInt()
        return when {
            i > Short.MAX_VALUE.toInt() -> Short.MAX_VALUE
            i < Short.MIN_VALUE.toInt() -> Short.MIN_VALUE
            else -> i.toShort()
        }
    }

    private fun evenize(n: Int) = if (n % 2 == 0) n else n + 1
}
