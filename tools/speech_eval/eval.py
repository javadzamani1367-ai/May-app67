#!/usr/bin/env python3
"""Persian speech eval: word and character error rates of the app's speech code on FLEURS fa_ir.

prepare DIR N        download N FLEURS fa_ir test clips as 16 kHz PCM16 WAV + refs.tsv
score DIR RESULTS    score a roozban-speech-bench output against DIR/refs.tsv
"""
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


def edits(a, b):
    prev = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        cur = [i]
        for j, y in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (x != y)))
        prev = cur
    return prev[-1]


def score(d, results):
    refs = dict(line.rstrip("\n").split("\t", 1) for line in open(os.path.join(d, "refs.tsv"), encoding="utf-8"))
    we = wn = ce = cn = 0
    ms = 0.0
    n = 0
    for line in open(results, encoding="utf-8"):
        if not line.startswith("RESULT\t"):
            continue
        _, path, t, text = line.rstrip("\n").split("\t", 3)
        if path not in refs:
            continue
        r, h = norm(refs[path]), norm(text)
        we += edits(r.split(), h.split())
        wn += len(r.split())
        ce += edits(r.replace(" ", ""), h.replace(" ", ""))
        cn += len(r.replace(" ", ""))
        ms += float(t)
        n += 1
        if n <= 3:
            print(f"  ref: {refs[path]}\n  hyp: {text}")
    print(f"SCORE clips={n} WER={100 * we / max(wn, 1):.1f}% CER={100 * ce / max(cn, 1):.1f}% avg={ms / max(n, 1) / 1000:.1f}s")


if __name__ == "__main__":
    if sys.argv[1] == "prepare":
        prepare(sys.argv[2], int(sys.argv[3]))
    else:
        score(sys.argv[2], sys.argv[3])
