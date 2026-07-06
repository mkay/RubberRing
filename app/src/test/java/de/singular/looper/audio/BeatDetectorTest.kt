package de.singular.looper.audio

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

/**
 * Objective checks for [BeatDetector] on synthetic click tracks of a known tempo. These can't
 * prove it nails real music (no ground truth here), but they guard against regressions on the
 * easy, unambiguous cases and confirm the tempo/phase machinery is wired up correctly.
 */
class BeatDetectorTest {

    private val sampleRate = 44_100

    /** A mono click track: a short decaying 2 kHz burst on every beat. */
    private fun clickTrack(bpm: Double, seconds: Double = 12.0, sustainedTone: Boolean = false): DecodedAudio {
        val totalFrames = (seconds * sampleRate).toInt()
        val pcm = ShortArray(totalFrames)
        val periodFrames = 60.0 / bpm * sampleRate
        val clickLen = (0.03 * sampleRate).toInt()
        val decayFrames = 0.008 * sampleRate

        var t = 0.0
        while (t < totalFrames) {
            val start = t.toInt()
            for (j in 0 until clickLen) {
                val idx = start + j
                if (idx >= totalFrames) break
                val s = sin(2 * PI * 2000 * j / sampleRate) * exp(-j / decayFrames)
                pcm[idx] = (pcm[idx] + (s * 12_000).toInt()).toShort().coerceAudio()
            }
            t += periodFrames
        }

        // Optionally add a loud sustained tone over the middle third — the kind of section that
        // used to pull the tempo/phase toward the loudest part of the song.
        if (sustainedTone) {
            val from = totalFrames / 3
            val to = 2 * totalFrames / 3
            for (idx in from until to) {
                val s = sin(2 * PI * 220 * idx / sampleRate) * 8_000
                pcm[idx] = (pcm[idx] + s.toInt()).toShort().coerceAudio()
            }
        }

        val wave = WaveformData(FloatArray(1), FloatArray(1), 0L, sampleRate, 1)
        return DecodedAudio(pcm, 1, sampleRate, wave)
    }

    private fun Short.coerceAudio(): Short = toInt().coerceIn(-32_768, 32_767).toShort()

    private fun assertTempo(bpm: Double, tolerance: Float = 2.5f, sustainedTone: Boolean = false) {
        val est = BeatDetector.detect(clickTrack(bpm, sustainedTone = sustainedTone))
        assertNotNull("detector returned null for $bpm BPM", est)
        val error = kotlin.math.abs(est!!.bpm - bpm.toFloat())
        assertTrue("detected ${est.bpm} for $bpm BPM (error $error)", error <= tolerance)
    }

    @Test fun detects90() = assertTempo(90.0)
    @Test fun detects100() = assertTempo(100.0)
    @Test fun detects120() = assertTempo(120.0)
    @Test fun detects128() = assertTempo(128.0)
    @Test fun detects150() = assertTempo(150.0, tolerance = 3f)

    /** A loud sustained passage must not throw off the tempo — the novelty whitening's job. */
    @Test fun detects110WithSustainedLoudSection() = assertTempo(110.0, sustainedTone = true)

    /** The downbeat should land on a click, not somewhere between beats. */
    @Test fun phaseLandsOnABeat() {
        val bpm = 100.0
        val audio = clickTrack(bpm)
        val est = BeatDetector.detect(audio)!!
        val periodFrames = 60.0 / bpm * sampleRate
        val downbeatFrame = est.downbeatFrac * audio.frameCount
        // Distance to the nearest click (clicks are at multiples of the period from frame 0).
        val phase = downbeatFrame % periodFrames
        val distToClick = min(phase, periodFrames - phase)
        // Envelope hop is 512 frames; allow a couple of hops of quantisation slack (~35 ms).
        assertTrue("downbeat $distToClick frames off the nearest beat", distToClick < 512 * 3)
    }
}
