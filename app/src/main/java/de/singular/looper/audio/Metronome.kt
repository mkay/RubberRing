package de.singular.looper.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * A count-in metronome. Plays [bars] × [beatsPerBar] clicks at a given tempo, then finishes, so
 * the caller can start the real audio on the downbeat that follows. An accented (higher) click
 * marks beat 1 of each bar; the rest are plain clicks.
 *
 * The whole count-in is rendered to one mono PCM buffer and played by a single [AudioTrack] in
 * static mode — inter-click timing is therefore sample-accurate, not subject to scheduler jitter.
 */
class Metronome(private val sampleRate: Int = 44_100) {

    // Two "tock" timbres, rendered once: a higher accent and a lower plain click, both dry and
    // woody like a mechanical metronome.
    private val accentClick = renderClick(freq = 1_100f, durationMs = 30)
    private val plainClick = renderClick(freq = 800f, durationMs = 30)

    @Volatile private var track: AudioTrack? = null

    private companion object {
        // Silent lead-in before the first click, covering AudioTrack cold-start latency.
        const val LEAD_MS = 80
        // Start the real audio this much before the nominal downbeat, to absorb the LoopPlayer's
        // (stream-mode) startup latency, which is larger than the static metronome track's.
        const val AUDIO_LEAD_MS = 83
    }

    /**
     * Sound the count-in and suspend until it has fully played. Returns normally only if it ran to
     * completion; cancelling the coroutine stops the clicks (the caller then won't start audio).
     */
    suspend fun countIn(bpm: Float, beatsPerBar: Int, bars: Int) {
        val beats = (beatsPerBar * bars).coerceAtLeast(1)
        val safeBpm = bpm.coerceIn(20f, 400f)
        val intervalFrames = (sampleRate * 60.0 / safeBpm).toInt().coerceAtLeast(1)
        // A short silence before the first click so the audio path is warmed up by the time it
        // sounds — otherwise cold-start latency intermittently swallows the first click's attack.
        val leadFrames = sampleRate * LEAD_MS / 1_000
        val buffer = ShortArray(leadFrames + intervalFrames * beats)
        for (b in 0 until beats) {
            val click = if (b % beatsPerBar == 0) accentClick else plainClick
            val at = leadFrames + b * intervalFrames
            System.arraycopy(click, 0, buffer, at, minOf(click.size, buffer.size - at))
        }

        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(buffer.size * 2)
            .build()
        track = t
        try {
            t.write(buffer, 0, buffer.size)
            t.play()
            // Suspend until the downbeat that follows the last click (the whole buffer's length),
            // minus a lead so the audio's own warmup lands it on the beat. Cancellation → finally.
            val bufferMs = buffer.size.toLong() * 1_000 / sampleRate
            delay((bufferMs - AUDIO_LEAD_MS).coerceAtLeast(1))
        } finally {
            stop()
        }
    }

    /** Stop and release any in-flight count-in (idempotent). */
    fun stop() {
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        track = null
    }

    /**
     * One click: a dry, woody "tock" like a mechanical metronome. A fundamental plus an
     * inharmonic partial (~2.76×) under a fast exponential decay gives the block-y character;
     * rendered to 16-bit mono PCM.
     */
    private fun renderClick(freq: Float, durationMs: Int): ShortArray {
        val n = sampleRate * durationMs / 1_000
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val env = exp(-9f * t * (1_000f / durationMs)) // fast decay → short, dry
            val tone = sin(2.0 * PI * freq * t).toFloat() +
                0.6f * sin(2.0 * PI * freq * 2.76 * t).toFloat()
            out[i] = (tone * env * 0.5f * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
