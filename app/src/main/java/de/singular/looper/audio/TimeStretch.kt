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
 * Pure CPU work — call it off the main thread.
 */
object TimeStretch {

    // Grain length ≈ 46 ms (50% overlap → synthesis hop ≈ 23 ms); search radius ≈ 15 ms, enough
    // to slide a grain across a pitch period down to ~65 Hz. All derived from the sample rate.
    private const val GRAIN_SECONDS = 0.046f
    private const val SEARCH_SECONDS = 0.015f

    fun stretch(pcm: ShortArray, channels: Int, sampleRate: Int, speed: Float): ShortArray {
        if (speed == 1f || channels < 1 || pcm.size < channels * 4) return pcm
        val frames = pcm.size / channels

        val seg = evenize((sampleRate * GRAIN_SECONDS).roundToInt().coerceAtLeast(64))
        val hop = seg / 2 // synthesis hop == overlap length
        val delta = (sampleRate * SEARCH_SECONDS).roundToInt().coerceAtLeast(1)
        if (frames < seg + delta + 2 || hop < 1) return pcm

        val analysisHop = max(1, (hop * speed).roundToInt()) // Ha = Hs * speed

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

        val out = ShortArray(((frames / speed).toInt() + 3 * seg) * channels)
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

        emitGrain(0, rawFirstHalf = true)
        var natPos = hop // where this grain naturally continues — the target the next grain matches
        var lastP = 0
        var ia = analysisHop
        while (ia + seg < frames) {
            val p = (ia + bestOffset(mono, ia, natPos, hop, delta, frames, seg)).coerceIn(0, frames - seg)
            emitGrain(p, rawFirstHalf = false)
            lastP = p
            natPos = p + hop
            ia += analysisHop
        }

        // End at full amplitude (no fade-out): write the last grain's second half raw, since there
        // is no successor grain to cross-fade it against.
        for (f in 0 until hop) {
            val src = (lastP + hop + f) * channels
            for (c in 0 until channels) writeSample(pcm[src + c].toFloat())
        }

        return if (outSample == out.size) out else out.copyOf(outSample)
    }

    /**
     * Within ±[delta] frames of [ia], find the offset whose [len]-frame window best matches the
     * natural continuation at [natPos] (normalised cross-correlation on the mono signal). This
     * phase-aligns the next grain to the previously emitted output.
     */
    private fun bestOffset(
        mono: FloatArray,
        ia: Int,
        natPos: Int,
        len: Int,
        delta: Int,
        frames: Int,
        seg: Int,
    ): Int {
        val kLo = max(-delta, -ia)
        val kHi = min(delta, frames - seg - ia)
        var bestK = 0
        var bestScore = Float.NEGATIVE_INFINITY
        var k = kLo
        while (k <= kHi) {
            val base = ia + k
            var dot = 0f
            var energy = 0f
            var j = 0
            while (j < len) {
                val a = mono[base + j]
                dot += a * mono[natPos + j]
                energy += a * a
                j++
            }
            val score = dot / sqrt(energy + 1e-3f)
            if (score > bestScore) { bestScore = score; bestK = k }
            k++
        }
        return bestK
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
