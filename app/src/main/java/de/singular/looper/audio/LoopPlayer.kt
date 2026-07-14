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

    fun play() {
        if (running) return
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

        running = true
        t.play()
        feeder = Thread({ feedLoop(t) }, "LoopPlayer-feeder").apply { start() }
    }

    private fun feedLoop(t: AudioTrack) {
        val chunkFrames = (sampleRate / 50).coerceAtLeast(256) // ~20 ms
        // Scratch space for the gain stage; unused (and untouched) while the gain is 1×.
        val scratch = ShortArray(chunkFrames * channels)
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
            val written = if (g == 1f) {
                t.write(pcm, shortOffset, shortCount, AudioTrack.WRITE_NON_BLOCKING)
            } else {
                Gain.applyInto(pcm, shortOffset, scratch, shortCount, g, softClip)
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
