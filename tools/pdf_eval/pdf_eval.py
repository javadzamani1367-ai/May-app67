#!/usr/bin/env python3
"""PDF → Word eval: builds Persian documents (headings, paragraphs, numbers, English), prints them
to PDF with Chrome and with LibreOffice, then scores the .docx the app makes from each PDF
(and plain PDFBox text, for comparison) against the source text.

make DIR           write DIR/*.html (+ sources.json)
score SRC DOCX     character error rate and paragraph count of one conversion
"""
import csv
import html
import io
import json
import os
import re
import sys
import urllib.request
import zipfile

FLEURS = "https://huggingface.co/datasets/google/fleurs/resolve/main/data/fa_ir"
MIXED = [
    "گزارش مالی سال ۱۴۰۴ شامل ۱۲ بخش است و مبلغ کل ۲٬۵۰۰٬۰۰۰ ریال محاسبه شده است.",
    "برای دریافت فایل Report-2026.pdf به سایت www.example.com مراجعه کنید.",
    "نسخهٔ Android 14 از قابلیت Dark Mode پشتیبانی می‌کند (در تنظیمات).",
]


def sentences():
    tsv = urllib.request.urlopen(f"{FLEURS}/test.tsv").read().decode("utf-8")
    seen, out = set(), []
    for r in csv.reader(io.StringIO(tsv), delimiter="\t"):
        if len(r) >= 3 and r[2] not in seen:
            seen.add(r[2])
            out.append(r[2])
    return out


def make(d):
    os.makedirs(d, exist_ok=True)
    s = sentences()
    docs = {}

    def doc(name, blocks, font):
        body = "".join(f"<h{lvl}>{html.escape(t)}</h{lvl}>" if lvl else f"<p>{html.escape(t)}</p>" for lvl, t in blocks)
        page = f"""<!doctype html><html dir="rtl" lang="fa"><head><meta charset="utf-8"><style>
        @font-face {{ font-family: F; src: url('{font}'); }}
        body {{ font-family: F, 'Noto Naskh Arabic', sans-serif; font-size: 14pt; line-height: 1.7; margin: 2cm; text-align: justify; }}
        h1 {{ font-size: 24pt; }} h2 {{ font-size: 18pt; }}
        </style></head><body>{body}</body></html>"""
        open(os.path.join(d, name + ".html"), "w", encoding="utf-8").write(page)
        docs[name] = [t for _, t in blocks]

    k = 0

    def para(n):
        nonlocal k
        out = " ".join(s[k:k + n])
        k += n
        return out

    font = os.path.abspath(os.environ.get("FONT", "core/designsystem/src/main/res/font/vazirmatn_regular.ttf"))
    doc("short", [(1, "گزارش آزمایشی"), (0, para(3)), (2, "بخش دوم"), (0, para(2)), (0, MIXED[0]), (0, para(3)), (0, MIXED[1]), (0, MIXED[2])], font)
    blocks = []
    for i in range(40):
        blocks.append((2, f"فصل {i + 1}"))
        for _ in range(4):
            if k + 3 >= len(s):
                k = 0
            blocks.append((0, para(3)))
    doc("long", blocks, font)
    json.dump(docs, open(os.path.join(d, "sources.json"), "w", encoding="utf-8"), ensure_ascii=False)
    print("made", ", ".join(docs))


DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")


def norm(t):
    t = t.replace("ي", "ی").replace("ى", "ی").replace("ك", "ک").replace("‌", "").translate(DIGITS)
    return re.sub(r"\s+", "", re.sub(r"[^\w\s]", " ", t))


def edits(a, b):
    # banded edit distance would be faster; texts are up to ~100k characters, so compare in chunks
    prev = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        cur = [i]
        for j, y in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (x != y)))
        prev = cur
    return prev[-1]


def chunked_cer(ref, hyp, size=2000):
    # Align chunk by chunk (sources are long); good enough to compare converters.
    total, errs, j = 0, 0, 0
    for i in range(0, len(ref), size):
        r = ref[i:i + size]
        h = hyp[j:j + size + size // 10]
        e = edits(r, h[:len(r) + len(r) // 10])
        errs += e
        total += len(r)
        j += len(r)
    return errs / max(total, 1)


def docx_text(path):
    with zipfile.ZipFile(path) as z:
        xml = z.read("word/document.xml").decode("utf-8")
    paras = []
    for p in re.findall(r"<w:p>.*?</w:p>|<w:p [^>]*>.*?</w:p>", xml, flags=re.S):
        t = "".join(html.unescape(x) for x in re.findall(r"<w:t[^>]*>(.*?)</w:t>", p, flags=re.S))
        if t.strip():
            paras.append(t)
    return paras


def score(src, name, out):
    ref_paras = json.load(open(src, encoding="utf-8"))[name]
    ref = norm("".join(ref_paras))
    if out.endswith(".docx"):
        paras = docx_text(out)
    else:
        paras = [l for l in open(out, encoding="utf-8").read().split("\n") if l.strip()]
    hyp = norm("".join(paras))
    print(f"SCORE {name} {os.path.basename(out)}: CER={100 * chunked_cer(ref, hyp):.1f}% paragraphs={len(paras)}/{len(ref_paras)}")
    for p in paras[:3]:
        print("   ", p[:160])


if __name__ == "__main__":
    if sys.argv[1] == "make":
        make(sys.argv[2])
    else:
        score(sys.argv[2], sys.argv[3], sys.argv[4])
