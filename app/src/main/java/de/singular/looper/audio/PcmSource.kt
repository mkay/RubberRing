// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

/**
 * Interleaved 16-bit PCM that is read a chunk at a time rather than held as one array. What
 * [LoopPlayer] loops over and what a loop export writes out: either a stretch of the decoded file
 * as it is ([PcmRegion]), or a time-stretched one built as it is read ([TimeStretch.plan]).
 */
interface PcmSource {
    val channels: Int
    val frameCount: Int

    /**
     * Copy [frames] frames starting at [frame] into [dest] from index 0, interleaved. Random
     * access: any range may be read in any order, which is what lets a player seek and wrap.
     */
    fun read(frame: Int, dest: ShortArray, frames: Int)
}

/** Frames [startFrame] until [endFrame] of [pcm], read in place — no copy is made. */
class PcmRegion(
    val pcm: ShortArray,
    override val channels: Int,
    val startFrame: Int = 0,
    endFrame: Int = pcm.size / channels,
) : PcmSource {
    override val frameCount: Int = endFrame - startFrame

    override fun read(frame: Int, dest: ShortArray, frames: Int) {
        System.arraycopy(pcm, (startFrame + frame) * channels, dest, 0, frames * channels)
    }
}
