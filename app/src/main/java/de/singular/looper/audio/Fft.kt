package de.singular.looper.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A minimal in-place iterative radix-2 Cooley–Tukey FFT — just enough for the spectral-flux onset
 * front-end in [BeatDetector], with no external dependency. Operates on parallel real/imaginary
 * arrays whose length must be a power of two.
 */
object Fft {

    /** In-place forward FFT. [re] and [im] are overwritten with the transform; sizes must match. */
    fun transform(re: FloatArray, im: FloatArray) {
        val n = re.size

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        // Butterflies, doubling the transform length each stage.
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenR = cos(ang).toFloat()
            val wLenI = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wR = 1f
                var wI = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vR = re[b] * wR - im[b] * wI
                    val vI = re[b] * wI + im[b] * wR
                    re[b] = re[a] - vR
                    im[b] = im[a] - vI
                    re[a] += vR
                    im[a] += vI
                    val nwR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nwR
                }
                i += len
            }
            len = len shl 1
        }
    }
}
