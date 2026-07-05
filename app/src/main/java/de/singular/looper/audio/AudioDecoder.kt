package de.singular.looper.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes an audio file (any format the platform codecs support: mp3/aac/flac/wav/ogg/…)
 * into an in-memory interleaved 16-bit PCM buffer plus a mono display envelope, in a
 * single pass.
 *
 * Note: the whole file is held in memory (interleaved shorts). A 4-minute stereo track at
 * 44.1 kHz is ~84 MB — fine for typical practice material; very long files could strain
 * memory (revisit if it becomes a problem).
 */
object AudioDecoder {

    private const val TIMEOUT_US = 10_000L

    class DecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * @param bucketCount number of display columns to produce for the envelope.
     * Assumes 16-bit PCM output (the platform default). Runs synchronously — call off the main thread.
     */
    // Higher bucket count keeps the waveform detailed when zoomed in.
    fun decode(context: Context, uri: Uri, bucketCount: Int = 6000): DecodedAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw DecodeException("Could not open file")

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw DecodeException("No audio track found in file")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw DecodeException("Missing MIME type")
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs =
                if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L

            // Pre-size the PCM buffer from the duration estimate; grow if we overshoot.
            val estimatedFrames = (durationUs / 1_000_000.0 * sampleRate).toLong().coerceAtLeast(1024)
            var pcm = ShortArray((estimatedFrames * channels).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            var pcmSize = 0

            val decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }
            codec = decoder

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outBuf = decoder.getOutputBuffer(outIndex)!!
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shorts = outBuf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val count = shorts.remaining()
                        if (pcmSize + count > pcm.size) {
                            val newCap = maxOf(pcm.size * 2, pcmSize + count)
                            pcm = pcm.copyOf(newCap)
                        }
                        shorts.get(pcm, pcmSize, count)
                        pcmSize += count
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
                }
            }

            if (pcmSize == 0) throw DecodeException("Decoded no audio samples")
            if (pcmSize != pcm.size) pcm = pcm.copyOf(pcmSize)

            val durationMs = if (durationUs > 0) durationUs / 1000
            else ((pcmSize / channels).toLong() * 1000 / sampleRate.coerceAtLeast(1))

            val waveform = buildEnvelope(pcm, channels, bucketCount, durationMs, sampleRate)
            return DecodedAudio(pcm, channels, sampleRate, waveform)
        } catch (e: DecodeException) {
            throw e
        } catch (e: Exception) {
            throw DecodeException("Failed to decode audio: ${e.message}", e)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun buildEnvelope(
        pcm: ShortArray,
        channels: Int,
        bucketCount: Int,
        durationMs: Long,
        sampleRate: Int,
    ): WaveformData {
        val frames = pcm.size / channels
        val minima = FloatArray(bucketCount) { 0f }
        val maxima = FloatArray(bucketCount) { 0f }
        if (frames == 0) return WaveformData(minima, maxima, durationMs, sampleRate, channels)

        val framesPerBucket = (frames.toLong() / bucketCount).coerceAtLeast(1)
        for (b in 0 until bucketCount) {
            val startFrame = (b.toLong() * framesPerBucket).toInt()
            if (startFrame >= frames) { break }
            val endFrame = ((b + 1).toLong() * framesPerBucket).coerceAtMost(frames.toLong()).toInt()
            var mn = Float.POSITIVE_INFINITY
            var mx = Float.NEGATIVE_INFINITY
            var f = startFrame
            while (f < endFrame) {
                var acc = 0
                val base = f * channels
                for (c in 0 until channels) acc += pcm[base + c]
                val mono = (acc / channels) / 32768f
                if (mono < mn) mn = mono
                if (mono > mx) mx = mono
                f++
            }
            if (mn != Float.POSITIVE_INFINITY) { minima[b] = mn; maxima[b] = mx }
        }
        return WaveformData(minima, maxima, durationMs, sampleRate, channels)
    }
}
