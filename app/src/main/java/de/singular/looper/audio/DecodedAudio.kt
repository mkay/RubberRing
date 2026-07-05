package de.singular.looper.audio

/**
 * Fully decoded audio held in memory: interleaved 16-bit PCM plus a display envelope.
 *
 * [pcm] is interleaved by channel (L,R,L,R,… for stereo). One *frame* is [channels]
 * consecutive shorts. This buffer is what [de.singular.looper.audio.LoopPlayer] loops over.
 */
class DecodedAudio(
    val pcm: ShortArray,
    val channels: Int,
    val sampleRate: Int,
    val waveform: WaveformData,
) {
    val frameCount: Int get() = pcm.size / channels
    val durationMs: Long get() = waveform.durationMs

    /** Convert a 0f..1f position along the file to a frame index. */
    fun fractionToFrame(fraction: Float): Int =
        (fraction.coerceIn(0f, 1f) * frameCount).toInt().coerceIn(0, frameCount)
}
