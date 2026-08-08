// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * How high the click sounds. Each option is a pair of frequencies — an accent for beat one and a
 * plain click for the rest — kept at the same 1.375× ratio so the accent reads as the same gesture
 * at every pitch.
 *
 * [MID] is the original click, unchanged, and stays the default. [LOW] sits under a bright snare
 * and cymbals; [HIGH] cuts through a loud, low-mid-heavy mix.
 */
enum class ClickPitch(val accentHz: Float, val plainHz: Float) {
    LOW(800f, 580f),
    MID(1_100f, 800f),
    HIGH(1_550f, 1_130f),
}

/**
 * The click, mixed into somebody else's PCM stream rather than played on its own.
 *
 * **Why it mixes instead of playing.** A click on its own [android.media.AudioTrack] is a second
 * clock: each track starts on its own latency, so the click and the music drift apart by tens of
 * milliseconds at the join — right at the downbeat, the one moment a metronome is judged on. Mixed
 * into the player's buffer, click and music go down the same pipe, so whatever the device's output
 * latency is, it applies to both equally and cancels. That is the whole reason this class writes
 * into a caller's array and owns no audio device at all.
 *
 * **Beat positions are computed, never accumulated.** At 44.1 kHz and 110 bpm a beat is
 * 24054.5454… frames; adding a rounded interval per beat walks more than a bar away over five
 * minutes. Each beat's frame comes from its index, so the grid cannot drift from itself.
 *
 * [gain] and [pitch] are safe to set while playback is running — the feeder picks them up on its
 * next chunk, so the volume slider and the pitch chips take effect live.
 */
class ClickTrack(
    private val sampleRate: Int,
    pitch: ClickPitch = ClickPitch.MID,
) {
    /** Level, 0f..1f. Zero is silence, and the mixer skips its work entirely. */
    @Volatile
    var gain: Float = DEFAULT_GAIN

    @Volatile
    private var voices: Voices = render(pitch)

    @Volatile
    var pitch: ClickPitch = pitch
        set(value) {
            if (field == value) return
            field = value
            voices = render(value) // two 30 ms buffers; cheap enough to do on the caller's thread
        }

    /**
     * One rendered timbre: the click itself, normalised to full scale, and the envelope it was
     * shaped with. The envelope is kept because the ducking below needs the click's *shape*, not
     * its waveform — multiplying the music by the oscillating waveform would ring-modulate it.
     */
    private class Voice(val pcm: ShortArray, val env: FloatArray)

    /** The two rendered timbres, swapped as a pair so a pitch change can never half-apply. */
    private class Voices(val accent: Voice, val plain: Voice) {
        val longest = maxOf(accent.pcm.size, plain.pcm.size)
    }

    /**
     * Mix clicks into [dest] for the [frames] frames of interleaved [channels]-channel audio
     * starting at [destOffset], where the first of those frames sits at [startFrame] on [grid]'s
     * timeline. Adds to whatever is already there, so the caller's music survives; a mono click
     * goes to every channel.
     *
     * Each call asks which beats *overlap* the block rather than tracking a click across the
     * boundary: a click is 30 ms and a block ~20, so one always straddles, and looking the overlap
     * up from the beat index keeps this stateless — which is what lets the loop wrap, seek, and
     * change region underneath it without the click needing to know.
     */
    fun mixInto(
        dest: ShortArray,
        destOffset: Int,
        frames: Int,
        channels: Int,
        startFrame: Long,
        grid: ClickGrid,
    ) {
        val g = gain
        if (g <= 0f || frames <= 0 || grid.framesPerBeat <= 0.0) return
        val v = voices
        val until = startFrame + frames

        // The earliest beat whose click could still be sounding here, and the last that starts here.
        val first = floor((startFrame - v.longest - grid.firstBeatFrame) / grid.framesPerBeat).toLong()
        val last = floor((until - 1 - grid.firstBeatFrame) / grid.framesPerBeat).toLong()

        val amp = g * MAX_GAIN
        var b = first
        while (b <= last) {
            val at = grid.firstBeatFrame + Math.round(b * grid.framesPerBeat)
            // floorMod, so beats before the downbeat accent correctly rather than skewing the bar.
            val voice = if (Math.floorMod(b + grid.barPhase, grid.beatsPerBar.toLong()) == 0L) {
                v.accent
            } else {
                v.plain
            }
            val from = maxOf(at, startFrame)
            val to = minOf(at + voice.pcm.size, until)
            var f = from
            while (f < to) {
                val idx = (f - at).toInt()
                val click = (voice.pcm[idx] / FULL_SCALE) * amp
                // Duck the music under the click, by the click's own envelope. Shaped like the
                // click and gone in 30 ms, so it reads as the click cutting through rather than as
                // the track dipping — and it is what buys audibility over a mastered mix without
                // simply driving the sum into the clipper.
                val duck = 1f - DUCK_DEPTH * g * voice.env[idx]
                var i = destOffset + (f - startFrame).toInt() * channels
                val end = i + channels
                while (i < end) {
                    val mixed = Gain.softClip((dest[i] / FULL_SCALE) * duck + click)
                    dest[i] = (mixed * FULL_SCALE).toInt().coerceIn(-32_768, 32_767).toShort()
                    i++
                }
                f++
            }
            b++
        }
    }

    /**
     * One click: a dry, woody "tock" like a mechanical metronome. A fundamental plus an inharmonic
     * partial (~2.76×) under a fast exponential decay gives the block-y character; rendered to
     * 16-bit mono PCM.
     */
    private fun render(pitch: ClickPitch) = Voices(
        accent = renderClick(pitch.accentHz),
        plain = renderClick(pitch.plainHz),
    )

    private fun renderClick(freq: Float): Voice {
        val n = sampleRate * DURATION_MS / 1_000
        val raw = FloatArray(n)
        val env = FloatArray(n)
        var peak = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            env[i] = exp(-9f * t * (1_000f / DURATION_MS)) // fast decay → short, dry
            val tone = sin(2.0 * PI * freq * t).toFloat() +
                0.6f * sin(2.0 * PI * freq * 2.76 * t).toFloat()
            raw[i] = tone * env[i]
            val mag = abs(raw[i])
            if (mag > peak) peak = mag
        }
        // Normalised rather than scaled by a constant: the fundamental and its inharmonic partial
        // sum to a different peak at every pitch, so a fixed factor would have left each pitch a
        // different amount of the range unused — and would have wrapped if it ever overshot.
        val norm = if (peak > 0f) Short.MAX_VALUE / peak else 0f
        val pcm = ShortArray(n) { (raw[it] * norm).toInt().coerceIn(-32_768, 32_767).toShort() }
        return Voice(pcm, env)
    }

    companion object {
        const val DURATION_MS = 30

        /**
         * What the slider's top end means, as a multiple of full scale. Past 1× the click is
         * deliberately louder than any sample can be on its own, and [Gain.softClip] bends what
         * will not fit — a click has to sit *over* a mastered track, not politely beneath it,
         * and 0 dBFS against a track already mastered to 0 dBFS is not "loud", it is "equal".
         */
        private const val MAX_GAIN = 2.5f

        /**
         * How far the music is pulled down under a click at full slider, as a fraction. Applied
         * through the click's own envelope, so it lasts as long as the click does.
         */
        private const val DUCK_DEPTH = 0.45f

        private const val FULL_SCALE = 32_768f

        /** Where the volume slider starts: audible over most material without burying it. */
        const val DEFAULT_GAIN = 0.5f
    }
}

/**
 * Where the beats fall, in the frames of whatever buffer is being mixed.
 *
 * [firstBeatFrame] may be negative or fractional-in-effect — it is beat zero's position, extended
 * backwards as far as needed, so a region starting mid-bar still lands its clicks on the grid.
 * [barPhase] is which beat of the bar beat zero is, so the accent stays on the real downbeat
 * rather than on whichever beat happens to come first.
 */
data class ClickGrid(
    val firstBeatFrame: Long,
    val framesPerBeat: Double,
    val beatsPerBar: Int,
    val barPhase: Long = 0,
)
