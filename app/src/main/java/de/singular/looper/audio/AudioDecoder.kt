// SPDX-License-Identifier: GPL-3.0-only

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
 * The whole file is held in memory as interleaved shorts: about 10.6 MB a minute for 44.1 kHz
 * stereo. A file too long for the app's heap is refused with [DecodeFailure.TOO_LONG] — up front
 * from its stated duration where it has one, or as soon as the decode outgrows the budget.
 */
object AudioDecoder {

    private const val TIMEOUT_US = 10_000L

    // Heap kept back for everything that is not this track's audio: the UI, the library, the
    // analysis passes. A loaded 5-minute track measured about 4 MB of Java heap besides its PCM.
    private const val RESERVE_BYTES = 64L * 1024 * 1024

    // Slack on top of the stated duration, so the usual few hundred frames of codec priming and
    // padding fit without regrowing the buffer: whichever is more, 1% or one second.
    private const val MARGIN_FRACTION = 0.01
    private const val MARGIN_SECONDS = 1

    // Buffer to start from when the file states no duration; it grows by half as it fills.
    private const val UNKNOWN_DURATION_SECONDS = 60

    /**
     * The most frames of [channels]-channel audio the app can hold in a heap of [maxMemory] bytes.
     * Each frame costs its 16-bit samples plus one byte for a time-stretch, whose search keeps a
     * float for every fourth frame (see [TimeStretch.plan]).
     */
    fun maxFrames(maxMemory: Long, channels: Int): Int =
        ((maxMemory - RESERVE_BYTES).coerceAtLeast(0) / (2L * channels + 1))
            .coerceAtMost(Int.MAX_VALUE.toLong() / channels)
            .toInt()

    /**
     * Why a decode failed, in the terms the UI cares about: either we never got at the file, or we
     * got at it and could not make audio out of it. [DecodeException]'s message keeps the precise
     * cause for the log; the enum is what picks the message the user sees, so that message can be
     * translated rather than being whatever the platform codec happened to say.
     */
    enum class DecodeFailure { CANNOT_OPEN, DECODE_FAILED, TOO_LONG }

    /** [maxMinutes] is set for [DecodeFailure.TOO_LONG]: how much audio like this file's fits. */
    class DecodeException(
        val failure: DecodeFailure,
        message: String,
        cause: Throwable? = null,
        val maxMinutes: Int = 0,
    ) : Exception(message, cause)

    /**
     * @param bucketCount number of display columns to produce for the envelope.
     * Assumes 16-bit PCM output (the platform default). Runs synchronously — call off the main thread.
     */
    // Higher bucket count keeps the waveform detailed when zoomed in.
    // Catch-all: the platform MediaExtractor/MediaCodec stack throws a wide, undocumented range
    // of unchecked exceptions on malformed or unsupported media; we normalise them all into a
    // single DecodeException for callers.
    @Suppress("TooGenericExceptionCaught")
    fun decode(context: Context, uri: Uri, bucketCount: Int = 6000): DecodedAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var maxMinutes = 0
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw DecodeException(DecodeFailure.CANNOT_OPEN, "Could not open file")

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw DecodeException(DecodeFailure.DECODE_FAILED, "No audio track found in file")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw DecodeException(DecodeFailure.DECODE_FAILED, "Missing MIME type")
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs =
                if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L

            val maxFrames = maxFrames(Runtime.getRuntime().maxMemory(), channels)
            maxMinutes = maxFrames / sampleRate.coerceAtLeast(1) / 60
            val estimatedFrames = (durationUs / 1_000_000.0 * sampleRate).toLong()
            if (estimatedFrames > maxFrames) throw tooLong(maxMinutes)

            // Size the PCM buffer once, from the stated duration plus a margin, so it is never
            // copied: growing it, or trimming it to fit, would briefly hold the track twice.
            val initialFrames = if (estimatedFrames > 0) {
                estimatedFrames + maxOf(
                    (estimatedFrames * MARGIN_FRACTION).toLong(),
                    sampleRate.toLong() * MARGIN_SECONDS,
                )
            } else {
                sampleRate.toLong() * UNKNOWN_DURATION_SECONDS
            }
            val capacity = minOf(maxOf(initialFrames, 1024L), maxFrames.toLong()).toInt()
            var pcm = ShortArray(capacity * channels)
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
                            // The duration was missing or wrong. Grow by half rather than double:
                            // the copy holds both buffers at once.
                            val maxSize = maxFrames.toLong() * channels
                            if (pcmSize + count > maxSize) throw tooLong(maxMinutes)
                            val newCap = maxOf(pcm.size + pcm.size / 2L, pcmSize + count.toLong())
                            pcm = pcm.copyOf(newCap.coerceAtMost(maxSize).toInt())
                        }
                        shorts.get(pcm, pcmSize, count)
                        pcmSize += count
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
                }
            }

            if (pcmSize == 0) throw DecodeException(DecodeFailure.DECODE_FAILED, "Decoded no audio samples")
            val frames = pcmSize / channels

            val durationMs = if (durationUs > 0) durationUs / 1000
            else (frames.toLong() * 1000 / sampleRate.coerceAtLeast(1))

            val waveform = buildEnvelope(pcm, channels, frames, bucketCount, durationMs, sampleRate)
            val (peak, rms) = Gain.measure(pcm, pcmSize)
            return DecodedAudio(pcm, channels, sampleRate, waveform, peak, rms, frames)
        } catch (e: DecodeException) {
            throw e
        } catch (e: OutOfMemoryError) {
            // The budget is an estimate; a heap that runs out anyway is the same answer.
            throw tooLong(maxMinutes, e)
        } catch (e: Exception) {
            throw DecodeException(DecodeFailure.DECODE_FAILED, "Failed to decode audio: ${e.message}", e)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun tooLong(maxMinutes: Int, cause: Throwable? = null) = DecodeException(
        DecodeFailure.TOO_LONG,
        "Too long for the heap (about $maxMinutes min fit)",
        cause,
        maxMinutes,
    )

    private fun buildEnvelope(
        pcm: ShortArray,
        channels: Int,
        frames: Int,
        bucketCount: Int,
        durationMs: Long,
        sampleRate: Int,
    ): WaveformData {
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
