package de.singular.looper.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * How a track's playback level is normalised. Persisted per track; the gain itself is derived
 * from the decoded audio each time (see [Gain.linearFor]), never stored.
 *
 * [PEAK] is the familiar DAW normalize — scale until the loudest sample sits at 0 dBFS. It cannot
 * distort, but it does nothing for a track that already peaks at full scale, which is most
 * commercial music. [LOUDNESS] targets the *average* level instead — what the ear reads as
 * "quiet" — and so must push some peaks past full scale, where [Gain.applyInto]'s soft clipper
 * catches them.
 */
enum class NormalizeMode { OFF, PEAK, LOUDNESS }

/**
 * Playback gain: how much to boost a track, and how to apply that boost to 16-bit PCM.
 *
 * Nothing here touches the imported file — the gain is applied to the PCM on its way to the
 * audio device (see [LoopPlayer]), so it is free to change and free to undo.
 */
object Gain {

    /** Average level [LOUDNESS] aims for, in dBFS RMS. Roughly streaming-service loudness. */
    const val TARGET_RMS_DB = -14f

    /**
     * Ceiling on a [LOUDNESS] boost, in dB. This one buys its loudness with soft clipping, so past
     * a point the artefacts and the raised noise floor outrun the benefit.
     */
    const val MAX_LOUDNESS_BOOST_DB = 18f

    /**
     * Ceiling on a [PEAK] boost, in dB. Peak mode cannot distort however far it goes, so this is
     * set generously — it only exists to stop a near-silent file (a failed rip, a dead track) from
     * being amplified into pure hiss.
     */
    const val MAX_PEAK_BOOST_DB = 30f

    /** Level above which [applyInto]'s soft clipper starts bending, when clipping is enabled. */
    private const val SOFT_KNEE = 0.8f

    private const val FULL_SCALE = 32768f

    fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    fun linearToDb(linear: Float): Float =
        if (linear <= 0f) Float.NEGATIVE_INFINITY else 20f * log10(linear)

    /**
     * The linear gain [mode] calls for on a track whose [peak] and [rms] are as measured (both
     * 0f..1f, see [DecodedAudio]). Only ever boosts: a track that is already loud enough is left
     * alone rather than turned down. Silence and near-silence yield 1f rather than a division
     * blow-up.
     */
    fun linearFor(mode: NormalizeMode, peak: Float, rms: Float): Float {
        val (raw, maxBoostDb) = when (mode) {
            NormalizeMode.OFF -> return 1f
            NormalizeMode.PEAK ->
                (if (peak <= 0f) 1f else 1f / peak) to MAX_PEAK_BOOST_DB
            NormalizeMode.LOUDNESS ->
                (if (rms <= 0f) 1f else dbToLinear(TARGET_RMS_DB - linearToDb(rms))) to MAX_LOUDNESS_BOOST_DB
        }
        return raw.coerceIn(1f, dbToLinear(maxBoostDb))
    }

    /**
     * Whether applying [gain] to a track peaking at [peak] would overshoot full scale — i.e.
     * whether [applyInto] needs its soft clipper. False for [NormalizeMode.PEAK] by construction,
     * which keeps peak mode a pure, undistorted scale.
     */
    fun needsSoftClip(gain: Float, peak: Float): Boolean = gain * peak > 1f

    /**
     * Scale [count] samples from [src] (at [srcOffset]) into [dest] by [gain].
     *
     * With [softClip] off, samples are simply scaled (and clamped, against rounding at the very
     * top of the scale). With it on, anything past [SOFT_KNEE] is bent through a tanh knee that
     * saturates smoothly towards full scale, so a loudness boost warms its transients over rather
     * than shattering them into hard-clipped edges.
     */
    fun applyInto(
        src: ShortArray,
        srcOffset: Int,
        dest: ShortArray,
        count: Int,
        gain: Float,
        softClip: Boolean,
    ) {
        for (i in 0 until count) {
            val x = (src[srcOffset + i] / FULL_SCALE) * gain
            val y = if (softClip) softClip(x) else x
            dest[i] = (y * FULL_SCALE).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /** Linear below the knee, tanh above it — |output| stays below 1f for any input. */
    private fun softClip(x: Float): Float {
        val mag = abs(x)
        if (mag <= SOFT_KNEE) return x
        val over = (mag - SOFT_KNEE) / (1f - SOFT_KNEE)
        val bent = SOFT_KNEE + (1f - SOFT_KNEE) * tanh(over)
        return if (x < 0f) -bent else bent
    }

    /**
     * The peak (max |sample|) and RMS of [pcm], both as 0f..1f fractions of full scale.
     * Measured across every sample of every channel.
     */
    fun measure(pcm: ShortArray): Pair<Float, Float> {
        if (pcm.isEmpty()) return 0f to 0f
        var peak = 0
        var sumSquares = 0.0
        for (s in pcm) {
            val mag = abs(s.toInt())
            if (mag > peak) peak = mag
            sumSquares += s.toDouble() * s.toDouble()
        }
        val rms = sqrt(sumSquares / pcm.size) / FULL_SCALE
        return (peak / FULL_SCALE) to rms.toFloat()
    }
}
