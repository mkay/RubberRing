// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

/**
 * Fully decoded audio held in memory: interleaved 16-bit PCM plus a display envelope.
 *
 * [pcm] is interleaved by channel (L,R,L,R,… for stereo). One *frame* is [channels]
 * consecutive shorts. This buffer is what [de.singular.looper.audio.LoopPlayer] loops over.
 *
 * [pcm] may run on past [frameCount] frames: the decoder sizes it from the file's stated duration
 * plus a margin, and trimming it to fit would mean a second full-size copy at the moment memory
 * is tightest. Only the first [frameCount] frames are audio — read them through [source].
 *
 * [peak] and [rms] are the track's loudest sample and its average level, both as 0f..1f fractions
 * of full scale. They are what [Gain] normalises from — measured here because the decode already
 * walks every sample.
 */
class DecodedAudio(
    val pcm: ShortArray,
    val channels: Int,
    val sampleRate: Int,
    val waveform: WaveformData,
    val peak: Float,
    val rms: Float,
    val frameCount: Int = pcm.size / channels,
) {
    val durationMs: Long get() = waveform.durationMs

    /** The whole track as a [PcmSource], read in place. */
    fun source(): PcmSource = PcmRegion(pcm, channels, 0, frameCount)

    /** Convert a 0f..1f position along the file to a frame index. */
    fun fractionToFrame(fraction: Float): Int =
        (fraction.coerceIn(0f, 1f) * frameCount).toInt().coerceIn(0, frameCount)
}
