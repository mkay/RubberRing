// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.min

/**
 * Seamless region looping over an in-memory PCM buffer.
 *
 * Uses an [AudioTrack] in stream mode fed by a background thread. The feeder writes frames
 * from the current position up to [loopEndFrame], then wraps back to [loopStartFrame] with
 * no decode step in between — so the loop boundary is gapless. The region can be changed
 * while playing; the feeder picks up the new bounds on its next iteration.
 *
 * All public methods are safe to call from the main thread.
 */
class LoopPlayer(
    private val pcm: ShortArray,
    private val channels: Int,
    private val sampleRate: Int,
) {
    val frameCount: Int = pcm.size / channels

    @Volatile private var loopStartFrame: Int = 0
    @Volatile private var loopEndFrame: Int = frameCount

    /** Current playback position in frames (approximate; slightly ahead of what's audible). */
    @Volatile var positionFrame: Int = 0
        private set

    /**
     * How many times playback has wrapped from the region end back to the start since [play].
     * Monotonic; the arrangement sequencer watches the delta to know when a step's repeats are done.
     */
    @Volatile var completedLoops: Int = 0
        private set

    /** A pending seek target, picked up by the feeder on its next iteration (-1 = none). */
    @Volatile private var pendingSeek: Int = -1

    // Playback gain (1f = untouched) and whether boosting it needs the soft clipper. Set from the
    // track's NormalizeMode; picked up by the feeder on its next chunk, so it takes effect live.
    @Volatile private var gain: Float = 1f
    @Volatile private var softClip: Boolean = false

    /** Set the playback gain. Safe to call while playing — takes effect within a chunk (~20 ms). */
    fun setGain(linear: Float, softClip: Boolean) {
        this.gain = linear
        this.softClip = softClip
    }

    // The audible click, mixed into this player's own buffer rather than played on a track of its
    // own — see [ClickTrack] for why that is the whole point. Null grid = no clicks through the
    // loop (the count-in, which has its own grid, is unaffected).
    @Volatile private var click: ClickTrack? = null
    @Volatile private var clickGrid: ClickGrid? = null

    /**
     * Set the click and where its beats fall in *this player's* frames. Safe to call while playing;
     * picked up on the next chunk, so toggling the click or dragging the grid takes effect live.
     */
    fun setClick(track: ClickTrack?, grid: ClickGrid?) {
        click = track
        clickGrid = grid
    }

    /**
     * True while the count-in pre-roll is being fed. Like [positionFrame], this is what has been
     * *written*, so it clears a buffer's worth before the last click is heard.
     */
    @Volatile var countingIn: Boolean = false
        private set

    // Pre-roll set up by [play]; read only by the feeder, which owns it once running.
    private var preRollFrames: Int = 0
    private var preRollGrid: ClickGrid? = null

    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var feeder: Thread? = null

    private val channelMask = when (channels) {
        1 -> AudioFormat.CHANNEL_OUT_MONO
        else -> AudioFormat.CHANNEL_OUT_STEREO
    }

    /** Set the loop region in frames. Safe to call while playing. */
    fun setRegion(startFrame: Int, endFrame: Int) {
        val s = startFrame.coerceIn(0, frameCount)
        val e = endFrame.coerceIn(s + 1, frameCount)
        loopStartFrame = s
        loopEndFrame = e
    }

    val isPlaying: Boolean get() = running

    /**
     * Start playback, optionally preceded by [countInFrames] frames of click-only count-in laid
     * out on [countInGrid].
     *
     * The count-in is fed through *this* track rather than sounded on one of its own, which is
     * what makes the join exact: the last click and the first sample of audio are consecutive
     * frames in one stream, so no amount of device output latency can open a gap between them.
     * (It used to be two tracks and a hardcoded 83 ms guess at the difference between their
     * startup latencies — see the history of `Metronome.AUDIO_LEAD_MS`.)
     */
    fun play(countInFrames: Int = 0, countInGrid: ClickGrid? = null) {
        if (running) return
        preRollFrames = countInFrames.coerceAtLeast(0)
        preRollGrid = countInGrid
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBytes * 2)
            .build()
        track = t

        // Start at the region start if we're outside it (e.g. first play or region moved).
        if (positionFrame < loopStartFrame || positionFrame >= loopEndFrame) {
            positionFrame = loopStartFrame
        }
        completedLoops = 0
        countingIn = preRollFrames > 0

        running = true
        t.play()
        feeder = Thread({ feedLoop(t) }, "LoopPlayer-feeder").apply { start() }
    }

    private fun feedLoop(t: AudioTrack) {
        val chunkFrames = (sampleRate / 50).coerceAtLeast(256) // ~20 ms
        // Scratch space for the gain and click stages; untouched while neither is active.
        val scratch = ShortArray(chunkFrames * channels)
        feedCountIn(t, scratch, chunkFrames)
        var frame = positionFrame
        while (running) {
            val seek = pendingSeek
            if (seek >= 0) {
                frame = seek
                pendingSeek = -1
            }
            val start = loopStartFrame
            val end = loopEndFrame
            if (frame < start || frame >= end) frame = start

            val framesThisWrite = min(chunkFrames, end - frame)
            val shortOffset = frame * channels
            val shortCount = framesThisWrite * channels
            val g = gain
            val cl = click
            val cg = clickGrid
            // A partial write drops its tail and re-reads from the new frame next time round. That
            // is safe for the click only because the mix is stateless in the frame number — the
            // dropped clicks are simply regenerated at the same positions.
            //
            // The same statelessness is what lets the loop wrap underneath the click: a click still
            // decaying at loopEndFrame is cut off there rather than carried across. Audible only in
            // theory — the region snaps to the grid, so the wrap lands on a beat and the click that
            // was cut is struck again immediately at the region start.
            val written = if (g == 1f && (cl == null || cg == null || cl.gain <= 0f)) {
                t.write(pcm, shortOffset, shortCount, AudioTrack.WRITE_NON_BLOCKING)
            } else {
                if (g == 1f) {
                    System.arraycopy(pcm, shortOffset, scratch, 0, shortCount)
                } else {
                    Gain.applyInto(pcm, shortOffset, scratch, shortCount, g, softClip)
                }
                // After the gain stage, so the click is a fixed level the user set rather than
                // something a loudness boost drags up and down under them.
                if (cl != null && cg != null) {
                    cl.mixInto(scratch, 0, framesThisWrite, channels, frame.toLong(), cg)
                }
                t.write(scratch, 0, shortCount, AudioTrack.WRITE_NON_BLOCKING)
            }
            if (written < 0) break // error
            if (written > 0) {
                val framesWritten = written / channels // request is frame-aligned
                frame += framesWritten
                if (frame >= end) { frame = start; completedLoops++ } // a full pass of the region
                positionFrame = frame
            } else {
                // Output buffer full — wait briefly, keeping pause latency low.
                try { Thread.sleep(3) } catch (_: InterruptedException) { break }
            }
        }
    }

    /**
     * Feed the count-in: silence with clicks mixed in, on its own grid, immediately ahead of the
     * first frame of audio. Returns when it has all been queued, or at once when there is none.
     *
     * No warm-up lead is needed here, unlike the static-buffer count-in this replaces. A stream
     * plays the frames it is given in the order it is given them — nothing sounds until the first
     * write arrives, so a cold start cannot swallow the first click's attack, it can only delay
     * the whole count-in equally. Which is exactly what it does to the audio behind it too.
     */
    private fun feedCountIn(t: AudioTrack, scratch: ShortArray, chunkFrames: Int) {
        val total = preRollFrames
        val grid = preRollGrid
        if (total <= 0 || grid == null) {
            countingIn = false
            return
        }
        val cl = click
        var at = 0
        while (running && at < total) {
            val framesThisWrite = min(chunkFrames, total - at)
            val shortCount = framesThisWrite * channels
            scratch.fill(0, 0, shortCount)
            cl?.mixInto(scratch, 0, framesThisWrite, channels, at.toLong(), grid)
            val written = t.write(scratch, 0, shortCount, AudioTrack.WRITE_NON_BLOCKING)
            if (written < 0) break
            if (written > 0) {
                at += written / channels
            } else {
                try { Thread.sleep(3) } catch (_: InterruptedException) { break }
            }
        }
        countingIn = false
    }

    fun pause() {
        if (!running) return
        running = false
        feeder?.let { runCatching { it.join(200) } }
        feeder = null
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        track?.release()
        track = null
    }

    /** Reset the playhead to the region start (takes effect live if playing). */
    fun rewind() {
        seekTo(loopStartFrame)
    }

    /** Move the playhead to [frame]; if playing, playback jumps there on the next iteration. */
    fun seekTo(frame: Int) {
        val f = frame.coerceIn(0, frameCount)
        positionFrame = f
        pendingSeek = f
    }

    fun release() {
        running = false
        feeder?.let { runCatching { it.join(200) } }
        feeder = null
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 1.5f
    }
}
