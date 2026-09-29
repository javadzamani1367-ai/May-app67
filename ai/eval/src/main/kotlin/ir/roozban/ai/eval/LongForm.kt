package ir.roozban.ai.eval

import ir.roozban.ai.core.audio.Pcm
import ir.roozban.ai.core.audio.PieceTranscriber
import ir.roozban.ai.core.audio.Segmenter
import ir.roozban.ai.core.audio.Transcript
import ir.roozban.ai.core.text.PersianLexicon
import ir.roozban.ai.core.text.SpellCorrector
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream

/**
 * Long-form dictation eval: runs minutes of speech through the app's own dictation steps (the
 * [Segmenter] cutting pieces at pauses, [PieceTranscriber], [Transcript.clean]) with the speech
 * code of the app (roozban-speech-bench, driven over stdin), and prints one line per recording:
 * `RESULT <path> <ms> <text>` plus piece statistics. tools/speech_eval/eval.py scores it.
 *
 * usage: LongForm <bench> <model> <list.txt of long WAVs> <config: old|new> [threads]
 * ROOZBAN_FIT_CTX=1 in the environment makes the bench encode only what each piece fills.
 * ROOZBAN_SPELL_DIR (fa_words/fa_pairs) turns on [SpellCorrector]; ROOZBAN_SPELL="edit,sound,unknown,margin,context"
 * sets its parameters.
 */
fun main(args: Array<String>) {
    val (bench, model, list, config) = args
    val threads = args.getOrNull(4) ?: "4"
    val old = config == "old"
    val process = ProcessBuilder(bench, model, threads, "1", "-", "-").redirectError(ProcessBuilder.Redirect.INHERIT).start()
    val toBench = process.outputStream.bufferedWriter(Charsets.UTF_8)
    val fromBench = process.inputStream.bufferedReader(Charsets.UTF_8)
    val out = PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8)
    val tmp = File.createTempFile("piece", ".wav")
    val speller = System.getenv("ROOZBAN_SPELL_DIR")?.let { dir ->
        val p = System.getenv("ROOZBAN_SPELL")?.split(',')?.map(String::toDouble)
        val params = if (p == null) SpellCorrector.Params() else SpellCorrector.Params(p[0], p[1], p[2], p[3], contextWeight = p[4])
        val t = System.currentTimeMillis()
        SpellCorrector(PersianLexicon.load(File(dir)), params = params).also {
            System.err.println("spelling data loaded in ${System.currentTimeMillis() - t} ms")
        }
    }
    var spellMs = 0L

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
        val ends = mutableListOf<Int>()
        val frame = Pcm.SAMPLE_RATE * 30 / 1000
        var i = 0
        while (i + frame <= samples.size) {
            segmenter.feed(samples.copyOfRange(i, i + frame))?.let {
                pieces += it
                ends += i + frame
            }
            i += frame
        }
        segmenter.flush()?.let {
            pieces += it
            ends += samples.size
        }
        val rate = Pcm.SAMPLE_RATE.toDouble()
        val kept = pieces.sumOf { it.size } / rate
        out.println("KEPT\t$path\t${"%.1f".format(kept)}s of ${"%.1f".format(samples.size / rate)}s in ${pieces.size} pieces")

        var totalMs = 0.0
        val text = StringBuilder()
        val transcriber = PieceTranscriber({ piece ->
            val (t, ms) = bench(piece, null)
            totalMs += ms
            pieceMs += ms
            val clean = Transcript.clean(t)
            if (speller == null) clean else {
                val s0 = System.nanoTime()
                speller.correct(clean).also { spellMs += (System.nanoTime() - s0) / 1_000_000 }
            }
        })
        runBlocking {
            for ((n, piece) in pieces.withIndex()) {
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
                val end = ends[n] / rate
                out.println("PIECE\t${"%.1f".format(end - piece.size / rate)}-${"%.1f".format(end)}s\t${t.count { it.isLetter() }} letters\t${t.take(60)}")
            }
        }
        out.println("RESULT\t$path\t${"%.0f".format(totalMs)}\t$text")
    }
    toBench.close()
    process.waitFor()
    tmp.delete()
    val sorted = pieceMs.sorted()
    out.println(
        "PIECES n=${pieceMs.size} avgLen=${"%.1f".format(pieceSeconds.average())}s maxLen=${"%.1f".format(pieceSeconds.maxOrNull() ?: 0.0)}s " +
            "spell=${spellMs}ms avgWait=${"%.1f".format(pieceMs.average() / 1000)}s p90Wait=${"%.1f".format(sorted.getOrElse((sorted.size * 0.9).toInt()) { 0.0 } / 1000)}s",
    )
}
