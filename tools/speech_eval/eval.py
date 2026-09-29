#!/usr/bin/env python3
"""Persian speech eval: word and character error rates of the app's speech code on FLEURS fa_ir.

prepare DIR N        download N FLEURS fa_ir test clips as 16 kHz PCM16 WAV + refs.tsv
score DIR RESULTS    score a roozban-speech-bench output against DIR/refs.tsv
long DIR PER GAPS    join the clips of DIR, PER at a time, into minute-long recordings of
                     continuous talk (pauses of GAPS ms, e.g. 250,400,600) + long_refs.tsv
score-long DIR RES   score a LongForm output against DIR/long_refs.tsv, with dropped words
"""
import random
import struct
import wave
import csv
import io
import os
import re
import subprocess
import sys
import tarfile
import urllib.request

FLEURS = "https://huggingface.co/datasets/google/fleurs/resolve/main/data/fa_ir"


def prepare(out, n):
    os.makedirs(out, exist_ok=True)
    tsv = urllib.request.urlopen(f"{FLEURS}/test.tsv").read().decode("utf-8")
    rows = [r for r in csv.reader(io.StringIO(tsv), delimiter="\t")]
    wanted = {}
    for r in rows:
        if len(r) >= 3 and r[1] not in wanted:
            wanted[r[1]] = r[2]  # file name -> raw transcription
        if len(wanted) >= n:
            break
    tar_path = os.path.join(out, "test.tar.gz")
    urllib.request.urlretrieve(f"{FLEURS}/audio/test.tar.gz", tar_path)
    refs = []
    with tarfile.open(tar_path) as tar:
        for m in tar:
            name = os.path.basename(m.name)
            if name in wanted:
                src = os.path.join(out, "src-" + name)
                with open(src, "wb") as f:
                    f.write(tar.extractfile(m).read())
                dst = os.path.join(out, name.rsplit(".", 1)[0] + "-fa.wav")
                subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", src, "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le", dst], check=True)
                os.remove(src)
                refs.append((dst, wanted[name]))
            if len(refs) >= n:
                break
    os.remove(tar_path)
    with open(os.path.join(out, "refs.tsv"), "w", encoding="utf-8") as f:
        for p, t in refs:
            f.write(f"{p}\t{t}\n")
    with open(os.path.join(out, "list.txt"), "w") as f:
        f.write("\n".join(p for p, _ in refs) + "\n")
    print(f"prepared {len(refs)} clips")


DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")


def norm(t):
    t = t.replace("ي", "ی").replace("ى", "ی").replace("ك", "ک").replace("ة", "ه").replace("أ", "ا").replace("إ", "ا")
    t = t.translate(DIGITS).lower()
    t = re.sub(r"[ً-ٰٟ]", "", t)  # harakat
    t = t.replace("‌", "")  # ZWNJ: «می‌خواهم» = «میخواهم»
    t = re.sub(r"[^\w\s]", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def long_form(d, per, gaps):
    """Continuous talk from separate sentences: pauses shorter than the app's 700 ms make it
    run on across sentences, as when someone reads a page aloud."""
    rows = [line.rstrip("\n").split("\t", 1) for line in open(os.path.join(d, "refs.tsv"), encoding="utf-8")]
    rnd = random.Random(7)
    out, refs = [], []
    for k in range(0, len(rows) - per + 1, per):
        group = rows[k:k + per]
        frames = b""
        for i, (path, _) in enumerate(group):
            with wave.open(path) as w:
                frames += w.readframes(w.getnframes())
            if i < len(group) - 1:
                gap = rnd.choice(gaps)
                # a quiet room, not digital silence
                frames += b"".join(struct.pack("<h", rnd.randint(-40, 40)) for _ in range(16 * gap))
        name = os.path.join(d, f"long-{k // per:02d}-fa.wav")
        with wave.open(name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(16000)
            w.writeframes(frames)
        out.append(name)
        refs.append((name, " ".join(t for _, t in group)))
        print(f"{name}: {len(frames) / 32000:.0f} s")
    with open(os.path.join(d, "long_refs.tsv"), "w", encoding="utf-8") as f:
        for p, t in refs:
            f.write(f"{p}\t{t}\n")
    with open(os.path.join(d, "long_list.txt"), "w") as f:
        f.write("\n".join(out) + "\n")


def ops(a, b):
    """Edit distance and how many of a's words are missing from b (deletions)."""
    n, m = len(a), len(b)
    dp = [[(0, 0)] * (m + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        dp[i][0] = (i, i)
    for j in range(1, m + 1):
        dp[0][j] = (j, 0)
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            sub = dp[i - 1][j - 1]
            c = [(sub[0] + (a[i - 1] != b[j - 1]), sub[1]), (dp[i - 1][j][0] + 1, dp[i - 1][j][1] + 1), (dp[i][j - 1][0] + 1, dp[i][j - 1][1])]
            dp[i][j] = min(c)
    return dp[n][m]


def edits(a, b):
    prev = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        cur = [i]
        for j, y in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (x != y)))
        prev = cur
    return prev[-1]


def score(d, results, refs_name="refs.tsv"):
    refs = dict(line.rstrip("\n").split("\t", 1) for line in open(os.path.join(d, refs_name), encoding="utf-8"))
    we = wn = ce = cn = dels = 0
    ms = 0.0
    n = 0
    for line in open(results, encoding="utf-8"):
        if not line.startswith("RESULT\t"):
            continue
        _, path, t, text = line.rstrip("\n").split("\t", 3)
        if path not in refs:
            continue
        r, h = norm(refs[path]), norm(text)
        e, dl = ops(r.split(), h.split())
        we += e
        dels += dl
        wn += len(r.split())
        ce += edits(r.replace(" ", ""), h.replace(" ", ""))
        cn += len(r.replace(" ", ""))
        ms += float(t)
        n += 1
        if n <= 3:
            print(f"  ref: {refs[path]}\n  hyp: {text}")
    print(f"SCORE clips={n} WER={100 * we / max(wn, 1):.1f}% CER={100 * ce / max(cn, 1):.1f}% dropped={100 * dels / max(wn, 1):.1f}% avg={ms / max(n, 1) / 1000:.1f}s")


if __name__ == "__main__":
    if sys.argv[1] == "prepare":
        prepare(sys.argv[2], int(sys.argv[3]))
    elif sys.argv[1] == "long":
        long_form(sys.argv[2], int(sys.argv[3]), [int(g) for g in sys.argv[4].split(",")])
    elif sys.argv[1] == "score-long":
        score(sys.argv[2], sys.argv[3], "long_refs.tsv")
    else:
        score(sys.argv[2], sys.argv[3])
