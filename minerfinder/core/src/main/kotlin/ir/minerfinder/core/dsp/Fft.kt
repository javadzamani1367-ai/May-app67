package ir.minerfinder.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 FFT of one fixed size; twiddles and bit reversal are precomputed. */
class Fft(val size: Int) {
    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two: $size" }
    }

    private val cosTable = DoubleArray(size / 2) { cos(-2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(-2.0 * PI * it / size) }
    private val reversed = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) table[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** Forward transform of [re] + i·[im], both of length [size], in place. */
    fun transform(re: DoubleArray, im: DoubleArray) {
        require(re.size == size && im.size == size)
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                }
                start += len
            }
            len = len shl 1
        }
    }
}

object Windows {
    /** Periodic Hann window, the usual choice for spectral analysis. */
    fun hann(size: Int): DoubleArray = DoubleArray(size) { 0.5 - 0.5 * cos(2.0 * PI * it / size) }
}
