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
        val (peak, rms) = Gain.measure(pcm)
        return DecodedAudio(pcm, 1, sampleRate, wave, peak, rms)
    }

    /**
     * A melodic track: a new pitched note begins on every beat with a soft attack and a long
     * decay that overlaps the next. Total loudness stays roughly flat (notes overlap), so a
     * broadband energy difference would barely see the onsets — only spectral flux, which notices
     * the *new frequency*, catches the beat.
     */
    private fun melodicTrack(bpm: Double, seconds: Double = 12.0): DecodedAudio {
        val totalFrames = (seconds * sampleRate).toInt()
        val pcm = ShortArray(totalFrames)
        val periodFrames = 60.0 / bpm * sampleRate
        val attackFrames = 0.04 * sampleRate   // ~40 ms soft attack (weak energy transient)
        val decayFrames = 0.9 * periodFrames    // long tail, overlaps the next note
        val noteLen = (periodFrames * 1.6).toInt()
        val scale = doubleArrayOf(261.63, 293.66, 329.63, 349.23, 392.0, 440.0, 493.88) // C major

        var beat = 0
        var t = 0.0
        while (t < totalFrames) {
            val start = t.toInt()
            val freq = scale[beat % scale.size]
            for (j in 0 until noteLen) {
                val idx = start + j
                if (idx >= totalFrames) break
                val attack = min(1.0, j / attackFrames)
                val decay = exp(-j / decayFrames)
                val s = sin(2 * PI * freq * j / sampleRate) * attack * decay * 6_000
                pcm[idx] = (pcm[idx] + s.toInt()).toShort().coerceAudio()
            }
            beat++
            t += periodFrames
        }

        val wave = WaveformData(FloatArray(1), FloatArray(1), 0L, sampleRate, 1)
        val (peak, rms) = Gain.measure(pcm)
        return DecodedAudio(pcm, 1, sampleRate, wave, peak, rms)
    }

    /**
     * A 6/8 track: eighth notes running three to a dotted beat, with a **kick** on the beat and
     * light hats on the two eighths between — the metrical shape of every compound meter, played
     * the way a drummer plays it.
     *
     * The beat has to differ from the subdivision in *content*, not merely in volume: the onset
     * envelope is built from log-compressed spectral flux, so a click that is simply louder barely
     * stands out, while a low thump against a high tick plainly does. (A first version of this
     * fixture only accented the amplitude, and its beat was correctly judged no more periodic than
     * its subdivision.)
     */
    private fun compoundTrack(dottedBpm: Double, seconds: Double = 12.0): DecodedAudio {
        val totalFrames = (seconds * sampleRate).toInt()
        val pcm = ShortArray(totalFrames)
        val eighthFrames = 60.0 / (dottedBpm * 3) * sampleRate

        // A bass line moving under the beats, so each beat brings new frequency content — which is
        // what spectral flux keys on, and what makes the beat more than a louder subdivision.
        val bass = doubleArrayOf(82.41, 98.0, 110.0, 87.31) // E A A F

        var eighth = 0
        var t = 0.0
        while (t < totalFrames) {
            val onBeat = eighth % 3 == 0
            val start = t.toInt()

            // Hats tick on every eighth; the beat also gets a kick and a fresh bass note.
            val hits = if (onBeat) {
                listOf(
                    Triple(6_000.0, 0.02, 4_000),                          // hat
                    Triple(55.0, 0.12, 14_000),                            // kick
                    Triple(bass[(eighth / 3) % bass.size], 0.35, 9_000),   // bass note
                )
            } else {
                listOf(Triple(6_000.0, 0.02, 4_000))
            }

            for ((freq, seconds, amplitude) in hits) {
                val len = (seconds * sampleRate).toInt()
                val decayFrames = seconds * 0.35 * sampleRate
                for (j in 0 until len) {
                    val idx = start + j
                    if (idx >= totalFrames) break
                    val s = sin(2 * PI * freq * j / sampleRate) * exp(-j / decayFrames)
                    pcm[idx] = (pcm[idx] + (s * amplitude).toInt()).toShort().coerceAudio()
                }
            }
            eighth++
            t += eighthFrames
        }

        val wave = WaveformData(FloatArray(1), FloatArray(1), 0L, sampleRate, 1)
        val (peak, rms) = Gain.measure(pcm)
        return DecodedAudio(pcm, 1, sampleRate, wave, peak, rms)
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

    /** Melodic material with overlapping soft-attack notes — the case spectral flux exists for. */
    @Test fun detectsMelodic112() {
        val est = BeatDetector.detect(melodicTrack(112.0))
        assertNotNull("detector returned null for melodic 112 BPM", est)
        val error = kotlin.math.abs(est!!.bpm - 112f)
        assertTrue("detected ${est.bpm} for melodic 112 BPM (error $error)", error <= 3f)
    }

    /**
     * Compound meter (6/8): the pulse people count is the **dotted** quarter, two per bar, with the
     * eighths running three to a beat underneath. A duple-minded detector reports the quarter note
     * — a real periodicity, but one that falls *between* the beats, so its grid is useless. Modelled
     * on the user's "Apache": Logic says 100 BPM 6/8, so the dotted beat is 100 / 1.5 = 66.7.
     */
    @Test fun detectsCompound66InSixEight() {
        val est = BeatDetector.detect(compoundTrack(dottedBpm = 66.67))
        assertNotNull("detector returned null for 6/8", est)
        val error = kotlin.math.abs(est!!.bpm - 66.67f)
        assertTrue("detected ${est.bpm} for a 6/8 track whose dotted beat is 66.7 BPM", error <= 2.5f)
    }

    /** …and the compound test must keep its hands off a plain duple track at the same tempo. */
    @Test fun straightFourFourIsNotMistakenForCompound() = assertTempo(100.0)

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
