#!/usr/bin/env python3
"""Builds the Persian spelling data the app uses to correct dictation: word frequencies and
word-pair (bigram) frequencies from Persian Wikipedia plus a subtitle word list (everyday
speech). Output: OUT/fa_words.txt.gz ("word count") and OUT/fa_pairs.txt.gz ("w1 w2 count").

usage: build.py OUT WIKI_BZ2 [SUBTITLE_WORDS]
"""
import bz2
import collections
import gzip
import os
import re
import sys

PERSIAN = re.compile(r"[ء-غف-يپچژکگیآ‌]+")
MARKUP = re.compile(r"\{\{[^{}]*\}\}|<ref[^>]*>.*?</ref>|<[^>]+>|\[\[(?:[^|\]]*\|)?([^\]]*)\]\]|\[https?://[^\]]*\]|&[a-z]+;|'''?|={2,}")


def norm(w):
    w = w.replace("ي", "ی").replace("ى", "ی").replace("ك", "ک").replace("ة", "ه")
    w = w.strip("‌")
    return w


def main():
    out, wiki = sys.argv[1], sys.argv[2]
    subs = sys.argv[3] if len(sys.argv) > 3 else None
    os.makedirs(out, exist_ok=True)
    uni = collections.Counter()
    bi = collections.Counter()
    tokens = 0
    in_text = False
    with bz2.open(wiki, "rt", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if "<text" in line:
                in_text = True
            if not in_text:
                continue
            if "</text>" in line:
                in_text = False
            line = MARKUP.sub(lambda m: m.group(1) or " ", line)
            # sentence-ish chunks so pairs do not cross punctuation
            for chunk in re.split(r"[.!?؟،؛:\n()\[\]«»\"|*#]", line):
                words = [norm(w) for w in PERSIAN.findall(chunk)]
                words = [w for w in words if 1 < len(w) <= 24 or w in ("و", "ز", "ک")]
                if not words:
                    continue
                uni.update(words)
                bi.update(zip(words, words[1:]))
                tokens += len(words)
    print(f"wiki tokens {tokens:,} words {len(uni):,} pairs {len(bi):,}")
    if subs:
        n = 0
        for line in open(subs, encoding="utf-8", errors="ignore"):
            parts = line.split()
            if len(parts) != 2 or not parts[1].isdigit():
                continue
            w = norm(parts[0])
            if PERSIAN.fullmatch(w):
                # Subtitles are spoken language: give them weight next to the encyclopedia.
                uni[w] += int(parts[1]) // 4
                n += 1
        print(f"subtitle words {n:,}")
    words = [(w, c) for w, c in uni.most_common() if c >= 3][:250_000]
    keep = {w for w, _ in words}
    pairs = [(p, c) for p, c in bi.most_common() if c >= 4 and p[0] in keep and p[1] in keep][:1_200_000]
    with gzip.open(os.path.join(out, "fa_words.txt.gz"), "wt", encoding="utf-8", compresslevel=9) as f:
        for w, c in words:
            f.write(f"{w} {c}\n")
    with gzip.open(os.path.join(out, "fa_pairs.txt.gz"), "wt", encoding="utf-8", compresslevel=9) as f:
        for (a, b), c in pairs:
            f.write(f"{a} {b} {c}\n")
    for name in ("fa_words.txt.gz", "fa_pairs.txt.gz"):
        print(name, os.path.getsize(os.path.join(out, name)))
    print("top words", " ".join(w for w, _ in words[:40]))


if __name__ == "__main__":
    main()
