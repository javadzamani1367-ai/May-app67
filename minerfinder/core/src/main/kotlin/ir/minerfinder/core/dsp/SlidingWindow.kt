package ir.minerfinder.core.dsp

/**
 * Turns a stream of audio chunks of any length into overlapping windows of
 * [size] samples: the first once [size] samples have arrived, then one every
 * [hop] samples. The array handed to the callback is reused; copy it if it
 * must outlive the call.
 */
class SlidingWindow(val size: Int, val hop: Int) {
    init {
        require(hop in 1..size)
    }

    private val buffer = FloatArray(size)
    private var filled = 0
    private var sinceLast = 0

    fun push(chunk: FloatArray, count: Int = chunk.size, onWindow: (FloatArray) -> Unit) {
        var i = 0
        while (i < count) {
            if (filled < size) {
                val n = minOf(count - i, size - filled)
                System.arraycopy(chunk, i, buffer, filled, n)
                filled += n
                i += n
                if (filled == size) {
                    sinceLast = 0
                    onWindow(buffer)
                }
            } else {
                val n = minOf(count - i, hop - sinceLast)
                System.arraycopy(buffer, n, buffer, 0, size - n)
                System.arraycopy(chunk, i, buffer, size - n, n)
                i += n
                sinceLast += n
                if (sinceLast == hop) {
                    sinceLast = 0
                    onWindow(buffer)
                }
            }
        }
    }

    fun reset() {
        filled = 0
        sinceLast = 0
    }
}
