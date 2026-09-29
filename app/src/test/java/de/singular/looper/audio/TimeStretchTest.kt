// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Objective checks for [TimeStretch]: a time-stretch must change a clip's *duration* by the
 * expected factor while leaving its *pitch* unchanged. Both are measurable on a synthetic sine —
 * length by frame count, pitch by counting zero crossings (exact for a pure tone).
 */
class TimeStretchTest {

    private val sampleRate = 44_100

    /** A mono sine of [freq] Hz for [seconds], as interleaved 16-bit PCM (1 channel). */
    private fun sine(freq: Double, seconds: Double): ShortArray {
        val frames = (seconds * sampleRate).toInt()
        return ShortArray(frames) { (sin(2 * PI * freq * it / sampleRate) * 12_000).toInt().toShort() }
    }

    /** Estimate fundamental frequency from zero-crossing rate (accurate for a single sine). */
    private fun estimateFreq(pcm: ShortArray): Double {
        var crossings = 0
        for (i in 1 until pcm.size) {
            if ((pcm[i - 1] < 0 && pcm[i] >= 0) || (pcm[i - 1] >= 0 && pcm[i] < 0)) crossings++
        }
        val seconds = pcm.size.toDouble() / sampleRate
        return crossings / 2.0 / seconds
    }

    @Test
    fun `slowing down lengthens output without shifting pitch`() {
        val input = sine(440.0, 1.0)
        val out = TimeStretch.stretch(input, channels = 1, sampleRate = sampleRate, speed = 0.8f)

        // Duration grows by 1/speed = 1.25× (within a grain's worth of slack).
        val expected = input.size / 0.8
        assertEquals(expected, out.size.toDouble(), expected * 0.05)

        // Pitch is unchanged.
        assertEquals(440.0, estimateFreq(out), 5.0)
    }

    @Test
    fun `speeding up shortens output without shifting pitch`() {
        val input = sine(330.0, 1.0)
        val out = TimeStretch.stretch(input, channels = 1, sampleRate = sampleRate, speed = 1.5f)

        val expected = input.size / 1.5
        assertEquals(expected, out.size.toDouble(), expected * 0.05)
        assertEquals(330.0, estimateFreq(out), 5.0)
    }

    @Test
    fun `unity speed returns the input unchanged`() {
        val input = sine(440.0, 0.5)
        val out = TimeStretch.stretch(input, channels = 1, sampleRate = sampleRate, speed = 1f)
        assertTrue(out === input)
    }

    @Test
    fun `stereo output stays interleaved and frame-aligned`() {
        val frames = sampleRate // 1 s
        val stereo = ShortArray(frames * 2) { i ->
            val f = i / 2
            val s = sin(2 * PI * 220 * f / sampleRate) * 10_000
            s.toInt().toShort()
        }
        val out = TimeStretch.stretch(stereo, channels = 2, sampleRate = sampleRate, speed = 0.75f)
        assertEquals(0, out.size % 2) // still whole stereo frames
        val expected = stereo.size / 0.75
        assertEquals(expected, out.size.toDouble(), expected * 0.05)
    }

    /**
     * A stretched loop is looped as it is, so its length must be exactly frames / speed: one that
     * comes round even a few ms early stumbles at the seam on every pass (and drifts off a DAW's
     * grid when exported). Checked across speeds and lengths that don't divide evenly into grains.
     */
    @Test
    fun `output length is exactly frames over speed`() {
        for (seconds in listOf(0.2, 1.0, 5.3271, 7.333)) {
            val input = sine(220.0, seconds)
            for (speed in listOf(0.5f, 0.75f, 0.8f, 0.95f, 1.05f, 1.25f, 1.5f)) {
                val out = TimeStretch.stretch(input, channels = 1, sampleRate = sampleRate, speed = speed)
                val expected = (input.size / speed.toDouble()).roundToInt()
                assertEquals("${seconds}s at $speed×", expected, out.size)
            }
        }
    }

    /** Both edges are the input's own, so the stretched seam joins what the original seam joins. */
    @Test
    fun `stretched loop starts and ends on the input's own samples`() {
        val frames = 3 * sampleRate
        val stereo = ShortArray(frames * 2) { i -> (sin(2 * PI * 220 * (i / 2) / sampleRate) * 10_000 + i % 2 * 50).toInt().toShort() }
        for (speed in listOf(0.8f, 0.95f, 1.25f)) {
            val out = TimeStretch.stretch(stereo, channels = 2, sampleRate = sampleRate, speed = speed)
            assertEquals(stereo[0], out[0])
            assertEquals(stereo[1], out[1])
            assertEquals(stereo[stereo.size - 2], out[out.size - 2])
            assertEquals(stereo[stereo.size - 1], out[out.size - 1])
        }
    }

    /**
     * Fixing both edges leaves one join whose phase the search can't choose — a pure tone looped
     * to a length that isn't a whole number of its periods can't be seamless anywhere. That join
     * must go where it can't be heard: here, into the gap between two steady tones, which then
     * keep their level from end to end.
     */
    @Test
    fun `the unavoidable join lands in a quiet gap, not on a held tone`() {
        val first = sine(440.0, 1.0)
        val gap = ShortArray((0.3 * sampleRate).toInt())
        // 0.93 s: a length at which a join forced onto the end of the second tone dips it ~20%.
        val second = sine(330.0, 0.93)
        val input = first + gap + second
        for (speed in listOf(0.5f, 0.8f, 0.95f, 1.25f, 1.5f)) {
            val out = TimeStretch.stretch(input, channels = 1, sampleRate = sampleRate, speed = speed)
            val margin = (0.08 * sampleRate).toInt() // clear of the grains that straddle an edge
            val tones = listOf(
                0 until (first.size / speed).toInt() - margin,
                ((first.size + gap.size) / speed).toInt() + margin until out.size,
            )
            val window = 441 // 10 ms: several periods of either tone
            for (tone in tones) {
                var start = tone.first
                while (start + window <= tone.last) {
                    var peak = 0
                    for (i in start until start + window) peak = maxOf(peak, abs(out[i].toInt()))
                    assertEquals("peak at frame $start, $speed×", 12_000.0, peak.toDouble(), 12_000 * 0.02)
                    start += window
                }
            }
        }
    }
}
