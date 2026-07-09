package de.singular.looper.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
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
}
