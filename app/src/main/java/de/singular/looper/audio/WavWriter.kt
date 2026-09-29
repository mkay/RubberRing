// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes interleaved 16-bit PCM as a canonical RIFF/WAVE file: a 44-byte header, then the samples.
 * The plainest format there is — every DAW, player and OS reads it, and Android can write it with
 * no encoder at all.
 */
object WavWriter {

    private const val HEADER_BYTES = 44
    private const val CHUNK_SAMPLES = 8192

    fun write(out: OutputStream, pcm: ShortArray, channels: Int, sampleRate: Int) {
        val dataBytes = pcm.size * 2
        val blockAlign = channels * 2
        val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(HEADER_BYTES - 8 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // fmt chunk size for plain PCM
            putShort(1) // audio format: PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * blockAlign) // byte rate
            putShort(blockAlign.toShort())
            putShort(16) // bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }
        out.write(header.array())

        // Samples go out in chunks so a long loop doesn't need a second full-size copy as bytes.
        val chunk = ByteBuffer.allocate(CHUNK_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN)
        var i = 0
        while (i < pcm.size) {
            val n = minOf(CHUNK_SAMPLES, pcm.size - i)
            chunk.clear()
            chunk.asShortBuffer().put(pcm, i, n)
            out.write(chunk.array(), 0, n * 2)
            i += n
        }
        out.flush()
    }
}
