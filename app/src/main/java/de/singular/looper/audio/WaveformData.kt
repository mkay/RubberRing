package de.singular.looper.audio

/**
 * A downsampled amplitude envelope of a decoded audio file, ready for display.
 *
 * [minima] and [maxima] hold one normalized sample (range -1f..1f) per display bucket;
 * a waveform column is drawn from minima[i] to maxima[i]. Channels are mixed to mono.
 */
data class WaveformData(
    val minima: FloatArray,
    val maxima: FloatArray,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
) {
    val bucketCount: Int get() = minima.size

    // data class with arrays: override equals/hashCode by identity to avoid heavy comparisons.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
