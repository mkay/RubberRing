// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The memory budget [AudioDecoder] refuses over-long files by. Decoding itself needs a device. */
class AudioDecoderTest {

    private val mb = 1024L * 1024

    @Test
    fun `a 256 MB heap holds about a quarter of an hour of 44_1 kHz stereo`() {
        val minutes = AudioDecoder.maxFrames(256 * mb, channels = 2) / 44_100 / 60
        assertEquals(15, minutes)
    }

    @Test
    fun `the reported 5-minute track fits with room to spare`() {
        val reported = 14_195_712 // frames in the issue #7 track, 5:22 at 44.1 kHz
        assertTrue(AudioDecoder.maxFrames(256 * mb, channels = 2) > 2 * reported)
    }

    @Test
    fun `mono costs less per frame than stereo`() {
        assertTrue(AudioDecoder.maxFrames(256 * mb, 1) > AudioDecoder.maxFrames(256 * mb, 2))
    }

    @Test
    fun `a heap smaller than the reserve holds nothing`() {
        assertEquals(0, AudioDecoder.maxFrames(32 * mb, channels = 2))
    }
}
