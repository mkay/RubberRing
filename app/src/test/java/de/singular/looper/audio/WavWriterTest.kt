// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Checks for [WavWriter]: the header must describe the samples exactly (a wrong size or byte rate
 * makes a DAW misplace or refuse the file), and the samples must come back bit for bit.
 */
class WavWriterTest {

    private fun write(pcm: ShortArray, channels: Int, sampleRate: Int): ByteBuffer {
        val out = ByteArrayOutputStream()
        WavWriter.write(out, pcm, channels, sampleRate)
        return ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun ByteBuffer.tag(at: Int) = String(array(), at, 4, Charsets.US_ASCII)

    @Test
    fun headerDescribesStereo44k() {
        val pcm = ShortArray(2 * 1000) { (it * 7).toShort() }
        val b = write(pcm, channels = 2, sampleRate = 44_100)

        assertEquals(44 + pcm.size * 2, b.capacity())
        assertEquals("RIFF", b.tag(0))
        assertEquals(b.capacity() - 8, b.getInt(4))
        assertEquals("WAVE", b.tag(8))
        assertEquals("fmt ", b.tag(12))
        assertEquals(16, b.getInt(16))
        assertEquals(1.toShort(), b.getShort(20)) // PCM
        assertEquals(2.toShort(), b.getShort(22))
        assertEquals(44_100, b.getInt(24))
        assertEquals(44_100 * 4, b.getInt(28))
        assertEquals(4.toShort(), b.getShort(32))
        assertEquals(16.toShort(), b.getShort(34))
        assertEquals("data", b.tag(36))
        assertEquals(pcm.size * 2, b.getInt(40))
    }

    @Test
    fun samplesRoundTripAcrossChunks() {
        // Longer than one write chunk, and covering both ends of the 16-bit range.
        val pcm = ShortArray(20_001) { (it * 31 - 32768).toShort() }
        pcm[0] = Short.MIN_VALUE
        pcm[pcm.size - 1] = Short.MAX_VALUE
        val b = write(pcm, channels = 1, sampleRate = 48_000)

        b.position(44)
        val back = ShortArray(pcm.size)
        b.asShortBuffer().get(back)
        assertArrayEquals(pcm, back)
    }
}
