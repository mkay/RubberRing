package de.singular.looper.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Checks for [Gain]: the two normalize modes must each do what their name promises on a synthetic
 * sine, neither may boost past the cap or blow up on silence, and no output may ever leave the
 * 16-bit range — a wrapped sample is an audible crack, so it's the one thing that must never happen.
 */
class GainTest {

    private val sampleRate = 44_100

    /** A mono sine of [freq] Hz peaking at [amplitude] (0f..1f of full scale). */
    private fun sine(freq: Double, seconds: Double, amplitude: Float): ShortArray {
        val frames = (seconds * sampleRate).toInt()
        val scale = amplitude * 32767f
        return ShortArray(frames) { (sin(2 * PI * freq * it / sampleRate) * scale).toInt().toShort() }
    }

    /**
     * A track that *sounds* quiet but already touches full scale: a low-level sine with one
     * full-scale transient in it. This is the case Peak mode cannot help with and Loudness can —
     * and it's the common one, since most music has peaks far above its average level.
     */
    private fun quietButPeaking(): ShortArray =
        sine(440.0, 1.0, amplitude = 0.06f).also { it[0] = Short.MAX_VALUE }

    private fun apply(pcm: ShortArray, gain: Float, softClip: Boolean): ShortArray {
        val out = ShortArray(pcm.size)
        Gain.applyInto(pcm, 0, out, pcm.size, gain, softClip)
        return out
    }

    @Test
    fun `peak mode lifts a quiet track to full scale`() {
        val quiet = sine(440.0, 1.0, amplitude = 0.1f)
        val (peak, rms) = Gain.measure(quiet)
        val gain = Gain.linearFor(NormalizeMode.PEAK, peak, rms)

        val (outPeak, _) = Gain.measure(apply(quiet, gain, softClip = false))
        assertEquals(1f, outPeak, 0.01f)
    }

    @Test
    fun `peak mode is a no-op on a track that already peaks at full scale`() {
        val (peak, rms) = Gain.measure(quietButPeaking())

        assertEquals(1f, Gain.linearFor(NormalizeMode.PEAK, peak, rms), 0.001f)
        // …and that's precisely the case Loudness exists for: same track, real boost.
        assertTrue(Gain.linearFor(NormalizeMode.LOUDNESS, peak, rms) > 2f)
    }

    @Test
    fun `loudness mode brings a quiet track up to the target average level`() {
        val quiet = sine(440.0, 1.0, amplitude = 0.1f)
        val (peak, rms) = Gain.measure(quiet)
        val gain = Gain.linearFor(NormalizeMode.LOUDNESS, peak, rms)

        val (_, outRms) = Gain.measure(apply(quiet, gain, softClip = true))
        // Within a dB of target: the soft clipper shaves a little off the peaks, and with it the RMS.
        assertEquals(Gain.TARGET_RMS_DB, Gain.linearToDb(outRms), 1f)
    }

    @Test
    fun `boost is capped, each mode at its own ceiling`() {
        // A near-silent file wants ~+60 dB; neither mode may give it that.
        val nearSilent = sine(440.0, 1.0, amplitude = 0.001f)
        val (peak, rms) = Gain.measure(nearSilent)

        assertEquals(
            Gain.dbToLinear(Gain.MAX_PEAK_BOOST_DB),
            Gain.linearFor(NormalizeMode.PEAK, peak, rms),
            0.001f,
        )
        assertEquals(
            Gain.dbToLinear(Gain.MAX_LOUDNESS_BOOST_DB),
            Gain.linearFor(NormalizeMode.LOUDNESS, peak, rms),
            0.001f,
        )
    }

    @Test
    fun `neither mode ever attenuates`() {
        val loud = sine(440.0, 1.0, amplitude = 1f)
        val (peak, rms) = Gain.measure(loud)
        // A track already above the loudness target is left alone, not turned down.
        assertTrue(Gain.linearFor(NormalizeMode.LOUDNESS, 1f, 0.9f) >= 1f)
        assertTrue(Gain.linearFor(NormalizeMode.PEAK, peak, rms) >= 1f)
    }

    @Test
    fun `silence yields unity gain rather than dividing by zero`() {
        val silence = ShortArray(1000)
        val (peak, rms) = Gain.measure(silence)

        assertEquals(0f, peak, 0f)
        assertEquals(0f, rms, 0f)
        assertEquals(1f, Gain.linearFor(NormalizeMode.PEAK, peak, rms), 0f)
        assertEquals(1f, Gain.linearFor(NormalizeMode.LOUDNESS, peak, rms), 0f)
    }

    @Test
    fun `off is a bit-exact passthrough`() {
        val track = sine(440.0, 0.1, amplitude = 0.5f)
        val out = apply(track, Gain.linearFor(NormalizeMode.OFF, 0.5f, 0.35f), softClip = false)
        assertTrue(track.contentEquals(out))
    }

    @Test
    fun `soft clipping keeps a heavy boost inside full scale`() {
        val track = sine(440.0, 0.5, amplitude = 0.9f)
        // Far more gain than the track has headroom for — every peak wants to overshoot.
        val out = apply(track, Gain.dbToLinear(Gain.MAX_LOUDNESS_BOOST_DB), softClip = true)

        // The failure mode this guards against is wraparound — a peak that overshoots and comes
        // back with its sign flipped, which is an audible crack. Every sample must keep its sign.
        assertTrue(out.indices.all { out[it] == 0.toShort() || (out[it] > 0) == (track[it] > 0) })
        assertTrue(out.all { abs(it.toInt()) <= 32768 })
        // Saturated, not silenced: the clipper bends the peaks over, it doesn't collapse them.
        val (outPeak, _) = Gain.measure(out)
        assertTrue(outPeak > 0.9f)
    }

    @Test
    fun `peak mode needs no soft clipping, loudness on an already-peaking track does`() {
        val (peak, rms) = Gain.measure(quietButPeaking())

        assertFalse(Gain.needsSoftClip(Gain.linearFor(NormalizeMode.PEAK, peak, rms), peak))
        assertTrue(Gain.needsSoftClip(Gain.linearFor(NormalizeMode.LOUDNESS, peak, rms), peak))
    }
}
