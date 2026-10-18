// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * WSOLA (Waveform-Similarity Overlap-Add) time-stretch: change a clip's *duration* without
 * changing its pitch. Pure Kotlin, no dependencies. Operates on interleaved 16-bit PCM; played
 * back at the normal rate the result sounds slower (or faster) with the pitch intact.
 *
 * The search runs ahead of playback over the whole loop region, so it can afford to be generous —
 * which is the whole point. Naive overlap-add drops overlapping grains at fixed positions and
 * the phase mismatch between them smears transients and adds an echoey "phasiness" (this is
 * roughly what Android's built-in `PlaybackParams` does, and why it artifacts). WSOLA instead
 * *searches* a small window for the grain position whose waveform best continues the previously
 * emitted output, so consecutive grains line up in phase — attacks stay crisp and sustained
 * tones stay clean.
 *
 * [speed] < 1 slows down (longer output); > 1 speeds up. [speed] == 1 returns the input as-is.
 * [plan] is pure CPU work — call it off the main thread. A whole song takes long enough that the
 * caller may want to give up part-way: [checkCancelled] runs once per grain of the search, from
 * two threads at once, and may throw to abandon the stretch.
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

    /** The whole of [plan] for all of [pcm], rendered into one array. */
    fun stretch(
        pcm: ShortArray,
        channels: Int,
        sampleRate: Int,
        speed: Float,
        checkCancelled: () -> Unit = {},
    ): ShortArray {
        if (speed == 1f || channels < 1) return pcm
        val source = plan(pcm, channels, sampleRate, speed, checkCancelled = checkCancelled)
        if (source is PcmRegion) return pcm
        val out = ShortArray(source.frameCount * channels)
        source.read(0, out, source.frameCount)
        return out
    }

    /**
     * Stretch frames [startFrame] until [endFrame] of [pcm] by [speed]. Returns a [PcmSource] that
     * builds the stretched audio as it is read, rather than the audio itself: a whole song slowed
     * to 0.5× is twice the size of the decoded song, more than a 256 MB heap has room for. What
     * is worked out here is only where each grain is read from, which is all of the search and
     * one int per grain; the reads that follow are a cross-fade of two grains.
     *
     * The region is returned as it is when there is nothing to stretch: [speed] == 1, or a
     * region too short to hold a grain.
     */
    fun plan(
        pcm: ShortArray,
        channels: Int,
        sampleRate: Int,
        speed: Float,
        startFrame: Int = 0,
        endFrame: Int = pcm.size / channels,
        checkCancelled: () -> Unit = {},
    ): PcmSource {
        val asIs = PcmRegion(pcm, channels, startFrame, endFrame)
        val frames = endFrame - startFrame
        if (speed == 1f || frames < 4) return asIs

        val seg = evenize((sampleRate * GRAIN_SECONDS).roundToInt().coerceAtLeast(64))
        val hop = seg / 2 // synthesis hop == overlap length
        val delta = (sampleRate * SEARCH_SECONDS).roundToInt().coerceAtLeast(1)
        if (frames < seg + delta + 2 || hop < 1) return asIs

        // The output is exactly frames / speed long, so a loop keeps its length at any speed: a
        // loop that comes round even a few ms early stumbles at the seam on every pass. It is
        // built as the first grain's leading half, [grains] whole grains, and an [ending] taken
        // straight from the end of the input — so the output ends on the input's last sample,
        // just as it starts on its first, and the seam joins exactly what the original joins.
        val outFrames = (frames / speed.toDouble()).roundToInt()
        // hop <= ending < 2 * hop: long enough to carry the closing cross-fade and still fit.
        val grains = (outFrames - 2 * hop) / hop
        if (grains < 1) return asIs
        val ending = outFrames - (grains + 1) * hop
        val endStart = frames - ending // where the ending is read from the input
        if (endStart < hop) return asIs // a loop of a few dozen ms: nothing to stretch it with

        // Nominal grain positions, spaced so the last grain's natural continuation lands exactly on
        // [endStart]. Because the ending plays at 1×, the step is a hair off hop * speed, which is
        // inaudible; computing each from its index keeps rounding from drifting.
        val analysisStep = (endStart - hop).toDouble() / grains
        val nominal = IntArray(grains + 1) { (it * analysisStep).roundToInt() }

        // Periodic Hann so two 50%-overlapped windows sum to exactly 1 (constant-overlap-add).
        val hann = FloatArray(seg) { (0.5 - 0.5 * cos(2.0 * PI * it / seg)).toFloat() }

        // The mono downmix the similarity search runs on, read straight from the interleaved input:
        // a mono copy of a whole song would be another song's worth of heap.
        fun mono(f: Int): Float {
            val base = (startFrame + f) * channels
            if (channels == 2) return (pcm[base] + pcm[base + 1]).toFloat() / 2 // the usual case
            var s = 0
            for (c in 0 until channels) s += pcm[base + c]
            return s.toFloat() / channels
        }
        // The same, averaged over each DECIMATION frames: the averaging is a crude low-pass, so
        // the coarse pass compares the waveform's shape rather than aliased treble.
        val coarse = FloatArray(frames / DECIMATION) { i ->
            var s = 0f
            for (j in 0 until DECIMATION) s += mono(i * DECIMATION + j)
            s / DECIMATION
        }

        // How well the [len]-frame window at [from] continues the one at [target], at full
        // resolution (see [similarity]).
        fun monoSimilarity(from: Int, target: Int, len: Int): Float {
            var dot = 0f
            var energy = 0f
            var j = 0
            while (j < len) {
                val a = mono(from + j)
                dot += a * mono(target + j)
                energy += a * a
                j++
            }
            return dot / sqrt(energy + 1e-3f)
        }

        // How audibly two [len]-frame windows differ: the energy of their difference. Unlike the
        // search's correlation it is absolute, so a join in a quiet passage counts as nearly free.
        fun mismatch(a: Int, b: Int, len: Int): Float {
            var sum = 0f
            for (j in 0 until len) {
                val d = mono(a + j) - mono(b + j)
                sum += d * d
            }
            return sum
        }

        // Find the coarse winner, then rescore the offsets around it at full resolution. The
        // neighbourhood spans a whole coarse step either side, which absorbs the rounding of
        // positions down to coarse indices. Each offset says where the [len]-frame window at
        // [base] + offset best matches the window at [target]. Forward, [target] is the previous
        // grain's natural continuation; backward, [base] is a grain's continuation and [target]
        // the next grain. Either way it phase-aligns neighbouring grains.
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
            bestScore = Float.NEGATIVE_INFINITY
            for (kk in max(range.first, bestK - step)..min(range.last, bestK + step)) {
                val score = monoSimilarity(base + kk, target, hop)
                if (score > bestScore) { bestScore = score; bestK = kk }
            }
            return bestK
        }

        // Fixing both edges costs one join that the similarity search can't choose freely: the
        // search's jumps must add up to exactly the length it has to make up, so whatever phase
        // is left over lands somewhere. The chain is therefore built twice — forward from the
        // start and backward from the ending, each grain matched to its neighbour — and the two
        // meet at whichever grain boundary has the least audible error, such as a quiet gap or a
        // spot where the waveforms already line up. On a sustained tone it would otherwise be a
        // dip at the seam.
        //
        // The two chains don't depend on each other, so the backward one runs on a thread of its
        // own: over a whole song that halves the wait.
        fun searchRange(ia: Int) = max(-delta, -ia)..min(delta, frames - seg - ia)
        // bwd[k] is grain k chosen so its continuation matches the start of grain k + 1; the
        // ending acts as grain grains + 1, read from [endStart].
        val bwd = IntArray(grains + 2)
        bwd[grains + 1] = endStart
        var backwardFailure: Throwable? = null
        val backward = thread(name = "TimeStretch-backward") {
            try {
                for (k in grains downTo 1) {
                    checkCancelled()
                    val ia = nominal[k]
                    bwd[k] = ia + search(ia + hop, bwd[k + 1], searchRange(ia))
                }
            } catch (t: Throwable) {
                backwardFailure = t
            }
        }
        val fwd = IntArray(grains + 1) // fwd[0] = 0: the loop starts on its own first sample
        try {
            for (k in 1..grains) {
                checkCancelled()
                val ia = nominal[k]
                fwd[k] = ia + search(ia, fwd[k - 1] + hop, searchRange(ia))
            }
        } finally {
            backward.join() // a cancelled stretch stops both chains before it returns
        }
        backwardFailure?.let { throw it }
        // Join at k: forward grains before it, backward grains from it on.
        val meet = (1..grains + 1).minBy { k -> mismatch(fwd[k - 1] + hop, bwd[k], hop) }

        val starts = IntArray(grains + 2) { k ->
            when {
                k == 0 -> 0
                k == grains + 1 -> endStart
                k < meet -> fwd[k]
                else -> bwd[k]
            }
        }
        return Stretched(pcm, channels, startFrame, hop, hann, starts, outFrames)
    }

    /**
     * A stretched region, built as it is read. Output block 0 is the first grain's leading half,
     * played raw so the loop starts at full amplitude; each block after it is the previous grain's
     * windowed trailing half cross-faded onto the next grain's leading half — the last "grain"
     * being the ending, read from [starts].last(); past those blocks the rest of the ending plays
     * out raw, so the loop's end edge stays at full amplitude too. Every output frame depends only
     * on the two grains either side of it, so any range can be read on its own.
     */
    private class Stretched(
        private val pcm: ShortArray,
        override val channels: Int,
        private val startFrame: Int,
        private val hop: Int,
        private val hann: FloatArray,
        private val starts: IntArray, // where each grain is read from, relative to [startFrame]
        override val frameCount: Int,
    ) : PcmSource {

        // Where the cross-faded blocks end and the raw rest of the ending begins.
        private val rawFrom = starts.size * hop

        override fun read(frame: Int, dest: ShortArray, frames: Int) {
            val until = frame + frames
            var n = frame
            var i = 0
            while (n < until) {
                if (n >= rawFrom) {
                    val src = startFrame + starts[starts.size - 1] + hop + (n - rawFrom)
                    System.arraycopy(pcm, src * channels, dest, i, (until - n) * channels)
                    return
                }
                val b = n / hop
                val f0 = n - b * hop
                val count = min(hop - f0, until - n)
                if (b == 0) {
                    val src = (startFrame + starts[0] + f0) * channels
                    System.arraycopy(pcm, src, dest, i, count * channels)
                    i += count * channels
                } else {
                    val tail = startFrame + starts[b - 1] + hop
                    val head = startFrame + starts[b]
                    for (f in f0 until f0 + count) {
                        val wTail = hann[hop + f]
                        val wHead = hann[f]
                        val t = (tail + f) * channels
                        val h = (head + f) * channels
                        for (c in 0 until channels) {
                            dest[i++] = clampToShort(pcm[t + c] * wTail + pcm[h + c] * wHead)
                        }
                    }
                }
                n += count
            }
        }
    }

    /**
     * How well the [len]-frame window at [from] continues the one at [target]: normalised
     * cross-correlation, the score [plan]'s search maximises.
     */
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
