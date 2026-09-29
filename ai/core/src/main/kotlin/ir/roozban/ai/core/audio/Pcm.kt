package ir.roozban.ai.core.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16 kHz mono PCM16 helpers: raw files for the speech engine and WAV for tests and export. */
object Pcm {
    const val SAMPLE_RATE = 16_000

    fun writeRaw(file: File, samples: ShortArray, count: Int = samples.size) {
        val buf = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) buf.putShort(samples[i])
        file.writeBytes(buf.array())
    }

    fun readRaw(file: File): ShortArray {
        val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(buf.remaining() / 2) { buf.short }
    }

    fun toWav(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val data = samples.size * 2
        val buf = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(data)
        samples.forEach { buf.putShort(it) }
        return buf.array()
    }

    /** Reads a PCM16 mono WAV (the only kind the app writes). */
    fun fromWav(bytes: ByteArray): ShortArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "not a WAV file" }
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val len = buf.getInt(pos + 4)
            if (id == "data") {
                val out = ShortArray(minOf(len, bytes.size - pos - 8) / 2)
                for (i in out.indices) out[i] = buf.getShort(pos + 8 + i * 2)
                return out
            }
            pos += 8 + len + (len and 1)
        }
        error("no data chunk")
    }

    /** Leading and trailing silence cut to [padMs], so the recognizer sees less empty audio. */
    /**
     * Splits in two at the quietest 100 ms between 30 % and 70 % of the way through, so a word is
     * not cut in half.
     */
    fun splitAtQuietest(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): Pair<ShortArray, ShortArray> {
        val window = sampleRate / 10
        val from = (samples.size * 0.3).toInt()
        val to = (samples.size * 0.7).toInt() - window
        var best = samples.size / 2
        var quietest = Double.MAX_VALUE
        var i = from
        while (i <= to) {
            val db = Vad.dbfs(samples.copyOfRange(i, i + window))
            if (db < quietest) {
                quietest = db
                best = i + window / 2
            }
            i += window / 2
        }
        return samples.copyOfRange(0, best) to samples.copyOfRange(best, samples.size)
    }

    fun trim(samples: ShortArray, thresholdDb: Double = -45.0, padMs: Int = 200, frameMs: Int = 20): ShortArray {
        val frame = SAMPLE_RATE * frameMs / 1000
        val frames = samples.size / frame
        if (frames == 0) return samples
        fun loud(f: Int) = Vad.dbfs(samples.copyOfRange(f * frame, (f + 1) * frame)) > thresholdDb
        val first = (0 until frames).firstOrNull(::loud) ?: return ShortArray(0)
        val last = (frames - 1 downTo 0).first(::loud)
        val pad = SAMPLE_RATE * padMs / 1000
        val start = maxOf(0, first * frame - pad)
        val end = minOf(samples.size, (last + 1) * frame + pad)
        return samples.copyOfRange(start, end)
    }
}
