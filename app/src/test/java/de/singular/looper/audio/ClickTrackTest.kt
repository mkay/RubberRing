// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickTrackTest {

    private val sampleRate = 44_100

    /** 110 bpm — the tempo whose beat length (24054.5454… frames) is the drift trap. */
    private val framesPerBeat = sampleRate * 60.0 / 110.0

    private fun grid(firstBeatFrame: Long = 0, beatsPerBar: Int = 4, barPhase: Long = 0) =
        ClickGrid(firstBeatFrame, framesPerBeat, beatsPerBar, barPhase)

    private fun click(gain: Float = 1f, pitch: ClickPitch = ClickPitch.MID) =
        ClickTrack(sampleRate, pitch).also { it.gain = gain }

    /** Mix [frames] frames starting at [startFrame] into a fresh mono buffer. */
    private fun render(
        c: ClickTrack,
        g: ClickGrid,
        startFrame: Long,
        frames: Int,
        channels: Int = 1,
    ): ShortArray {
        val out = ShortArray(frames * channels)
        c.mixInto(out, 0, frames, channels, startFrame, g)
        return out
    }

    /**
     * Assert the click occupies [at] onwards and nothing before it. Not "first non-zero sample ==
     * at": the click's own first sample is exactly zero — it starts on a zero crossing, so it adds
     * no DC step to the music it lands on — which would put a naive search one sample late.
     */
    private fun assertClickStartsAt(out: ShortArray, at: Int) {
        assertTrue(
            "nothing may sound before the beat",
            out.copyOfRange(0, at).all { it == 0.toShort() },
        )
        assertTrue(
            "the click must be sounding just after the beat",
            out.copyOfRange(at, minOf(at + 50, out.size)).any { it != 0.toShort() },
        )
    }

    @Test
    fun `click starts exactly on the beat`() {
        val out = render(click(), grid(firstBeatFrame = 5_000), startFrame = 4_000, frames = 3_000)
        assertClickStartsAt(out, 1_000)
    }

    @Test
    fun `beat positions do not drift over thousands of beats`() {
        val c = click()
        val g = grid()
        // Beat 5000 at 110 bpm is over 45 minutes in — where an accumulated rounded interval would
        // have walked whole bars away from where the beat actually is.
        val beat = 5_000L
        val expected = Math.round(beat * framesPerBeat)
        val out = render(c, g, startFrame = expected - 10, frames = 100)
        assertClickStartsAt(out, 10)
    }

    @Test
    fun `a click straddling a block boundary is identical to one rendered whole`() {
        val c = click()
        val g = grid(firstBeatFrame = 1_000)
        val whole = render(c, g, startFrame = 0, frames = 4_000)

        // Split right through the middle of the click, which starts at 1000 and runs 30 ms.
        val split = ShortArray(4_000)
        c.mixInto(split, 0, 1_200, 1, 0L, g)
        c.mixInto(split, 1_200, 2_800, 1, 1_200L, g)

        assertTrue("stateless mixing must not depend on where blocks fall", whole.contentEquals(split))
    }

    @Test
    fun `the downbeat is accented and the other beats are not`() {
        val c = click()
        val g = grid(beatsPerBar = 4)
        val beatOne = render(c, g, startFrame = 0, frames = 1_500)
        val beatTwo = render(c, g, startFrame = Math.round(framesPerBeat), frames = 1_500)
        val beatFive = render(c, g, startFrame = Math.round(4 * framesPerBeat), frames = 1_500)

        assertNotEquals("beat 2 should not sound like the downbeat", beatOne.toList(), beatTwo.toList())
        assertEquals("beat 5 is the next downbeat", beatOne.toList(), beatFive.toList())
    }

    @Test
    fun `beats before beat zero still accent on the real downbeat`() {
        val c = click()
        val g = grid(firstBeatFrame = Math.round(4 * framesPerBeat), beatsPerBar = 4)
        // Beat -4 is a downbeat one bar before the anchor; beat -3 is not.
        val downbeat = render(c, g, startFrame = 0, frames = 1_500)
        val offbeat = render(c, g, startFrame = Math.round(framesPerBeat), frames = 1_500)
        val anchor = render(c, g, startFrame = Math.round(4 * framesPerBeat), frames = 1_500)

        assertEquals(anchor.toList(), downbeat.toList())
        assertNotEquals(anchor.toList(), offbeat.toList())
    }

    @Test
    fun `silence at zero gain`() {
        val out = render(click(gain = 0f), grid(), startFrame = 0, frames = 2_000)
        assertTrue(out.all { it == 0.toShort() })
    }

    @Test
    fun `gain scales the click`() {
        val loud = render(click(gain = 1f), grid(), startFrame = 0, frames = 2_000)
        val quiet = render(click(gain = 0.5f), grid(), startFrame = 0, frames = 2_000)
        val loudPeak = loud.maxOf { abs(it.toInt()) }
        val quietPeak = quiet.maxOf { abs(it.toInt()) }
        assertTrue(quietPeak in (loudPeak / 2 - 2)..(loudPeak / 2 + 2))
    }

    @Test
    fun `mixing adds to existing audio and saturates instead of wrapping`() {
        val frames = 2_000
        // A bed already at full scale: a click on top must clamp, not fold over to negative.
        val out = ShortArray(frames) { Short.MAX_VALUE }
        click().mixInto(out, 0, frames, 1, 0L, grid())
        assertTrue("clamped, never wrapped", out.all { it > 0 })
        assertEquals(Short.MAX_VALUE, out.max())
    }

    @Test
    fun `a mono click reaches every channel of a stereo buffer`() {
        val frames = 1_500
        val out = ShortArray(frames * 2)
        click().mixInto(out, 0, frames, 2, 0L, grid())
        val left = ShortArray(frames) { out[it * 2] }
        val right = ShortArray(frames) { out[it * 2 + 1] }
        assertTrue(left.any { it != 0.toShort() })
        assertTrue(left.contentEquals(right))
    }

    @Test
    fun `pitch changes the sound but keeps the accent relationship`() {
        val g = grid()
        val mid = render(click(pitch = ClickPitch.MID), g, 0, 2_000)
        val high = render(click(pitch = ClickPitch.HIGH), g, 0, 2_000)
        assertNotEquals(mid.toList(), high.toList())
        // Same envelope, so the peaks stay in the same ballpark — pitch is not a volume control.
        val midPeak = mid.maxOf { abs(it.toInt()) }
        val highPeak = high.maxOf { abs(it.toInt()) }
        assertTrue(abs(midPeak - highPeak) < midPeak / 4)
    }

    @Test
    fun `an offset write leaves earlier samples alone`() {
        val frames = 2_000
        val out = ShortArray(frames * 2)
        // Fill the first half with a marker, then mix only into the second half.
        for (i in 0 until frames) out[i] = 123
        click().mixInto(out, frames, frames, 1, 0L, grid())
        assertTrue(out.copyOfRange(0, frames).all { it == 123.toShort() })
        assertTrue(out.copyOfRange(frames, frames * 2).any { it != 0.toShort() })
    }
}
