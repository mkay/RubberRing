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

    fun write(out: OutputStream, pcm: ShortArray, channels: Int, sampleRate: Int) =
        write(out, PcmRegion(pcm, channels), sampleRate)

    /**
     * Write all of [source]. [process] sees each chunk before it goes out and may change its first
     * `count` samples in place — how an export applies the track's gain without a full-size copy.
     */
    fun write(
        out: OutputStream,
        source: PcmSource,
        sampleRate: Int,
        process: (chunk: ShortArray, count: Int) -> Unit = { _, _ -> },
    ) {
        val channels = source.channels
        val dataBytes = source.frameCount * channels * 2
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

        // Samples go out in chunks, read from the source as they go, so a long loop never needs a
        // full-size copy — neither of the samples nor of their bytes.
        val chunkFrames = CHUNK_SAMPLES / channels
        val samples = ShortArray(chunkFrames * channels)
        val chunk = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        var frame = 0
        while (frame < source.frameCount) {
            val n = minOf(chunkFrames, source.frameCount - frame)
            val count = n * channels
            source.read(frame, samples, n)
            process(samples, count)
            chunk.clear()
            chunk.asShortBuffer().put(samples, 0, count)
            out.write(chunk.array(), 0, count * 2)
            frame += n
        }
        out.flush()
    }
}
