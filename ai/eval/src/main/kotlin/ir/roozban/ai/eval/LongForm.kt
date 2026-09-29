package ir.roozban.ai.eval

import ir.roozban.ai.core.audio.Pcm
import ir.roozban.ai.core.audio.PieceTranscriber
import ir.roozban.ai.core.audio.Segmenter
import ir.roozban.ai.core.audio.Transcript
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Long-form dictation eval: runs minutes of speech through the app's own dictation steps (the
 * [Segmenter] cutting pieces at pauses, [PieceTranscriber], [Transcript.clean]) with the speech
 * code of the app (roozban-speech-bench, driven over stdin), and prints one line per recording:
 * `RESULT <path> <ms> <text>` plus piece statistics. tools/speech_eval/eval.py scores it.
 *
 * usage: LongForm <bench> <model> <list.txt of long WAVs> <config: old|new> [threads]
 * ROOZBAN_FIT_CTX=1 in the environment makes the bench encode only what each piece fills.
 */
fun main(args: Array<String>) {
    val (bench, model, list, config) = args
    val threads = args.getOrNull(4) ?: "4"
    val old = config == "old"
    val process = ProcessBuilder(bench, model, threads, "1", "-", "-").redirectError(ProcessBuilder.Redirect.INHERIT).start()
    val toBench = process.outputStream.bufferedWriter()
    val fromBench = process.inputStream.bufferedReader()
    val tmp = File.createTempFile("piece", ".wav")

    fun bench(piece: ShortArray, prompt: String?): Pair<String, Double> {
        tmp.writeBytes(Pcm.toWav(piece))
        toBench.write(tmp.path + (prompt?.let { "\t" + it.replace('\t', ' ').replace('\n', ' ') } ?: "") + "\n")
        toBench.flush()
        val line = fromBench.readLine() ?: error("bench stopped")
        val parts = line.split('\t', limit = 4)
        val text = parts.getOrElse(3) { "" }
        if (text.startsWith("ERROR ")) throw IllegalStateException(text)
        return text to parts[2].toDouble()
    }

    val pieceMs = mutableListOf<Double>()
    val pieceSeconds = mutableListOf<Double>()
    for (path in File(list).readLines().filter { it.isNotBlank() }) {
        val samples = Pcm.fromWav(File(path).readBytes())
        val segmenter = if (old) Segmenter(softMaxMs = 20_000, hardMaxMs = 28_000) else Segmenter()
        val pieces = mutableListOf<ShortArray>()
        val frame = Pcm.SAMPLE_RATE * 30 / 1000
        var i = 0
        while (i + frame <= samples.size) {
            segmenter.feed(samples.copyOfRange(i, i + frame))?.let(pieces::add)
            i += frame
        }
        segmenter.flush()?.let(pieces::add)

        var totalMs = 0.0
        val text = StringBuilder()
        val transcriber = PieceTranscriber({ piece ->
            val (t, ms) = bench(piece, null)
            totalMs += ms
            pieceMs += ms
            Transcript.clean(t)
        })
        runBlocking {
            for (piece in pieces) {
                pieceSeconds += piece.size / Pcm.SAMPLE_RATE.toDouble()
                val t = if (old) {
                    // As the app did before: the end of the text so far as a prompt, one attempt.
                    val (raw, ms) = bench(piece, text.toString().takeLast(120).ifBlank { null })
                    totalMs += ms
                    pieceMs += ms
                    Transcript.clean(raw)
                } else {
                    transcriber(piece)
                }
                if (t.isNotBlank()) text.append(if (text.isEmpty()) "" else " ").append(t)
            }
        }
        println("RESULT\t$path\t${"%.0f".format(totalMs)}\t$text")
        System.out.flush()
    }
    toBench.close()
    process.waitFor()
    tmp.delete()
    val sorted = pieceMs.sorted()
    println(
        "PIECES n=${pieceMs.size} avgLen=${"%.1f".format(pieceSeconds.average())}s maxLen=${"%.1f".format(pieceSeconds.maxOrNull() ?: 0.0)}s " +
            "avgWait=${"%.1f".format(pieceMs.average() / 1000)}s p90Wait=${"%.1f".format(sorted.getOrElse((sorted.size * 0.9).toInt()) { 0.0 } / 1000)}s",
    )
}
