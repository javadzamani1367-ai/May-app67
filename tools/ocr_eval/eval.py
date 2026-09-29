#!/usr/bin/env python3
"""Persian OCR eval: renders Persian pages (clean scans and phone-photo-like shots) and scores
OCR engines by character error rate and time per page.

make DIR           build the test pages in DIR (needs Pillow with libraqm for Persian shaping)
score DIR RESULTS  score an engine's output (lines "RESULT<TAB>image<TAB>ms<TAB>text")
"""
import csv
import io
import json
import os
import random
import re
import sys
import urllib.request

FLEURS = "https://huggingface.co/datasets/google/fleurs/resolve/main/data/fa_ir"

EXTRA = [
    "جلسهٔ هیئت‌مدیره ساعت ۱۰:۳۰ روز دوشنبه ۱۲ مهر ۱۴۰۵ برگزار می‌شود.",
    "مبلغ ۲٬۵۰۰٬۰۰۰ ریال بابت قبض برق تا پایان هفته پرداخت شود.",
    "شماره تماس: ۰۲۱-۸۸۷۷۶۶۵۵ و کد پستی ۱۴۳۵۶۷۸۹۱۰",
    "لطفاً گزارش ماهانه را تا بیست‌وپنجم هر ماه ارسال کنید.",
    "آدرس: تهران، خیابان ولیعصر، کوچهٔ نسترن، پلاک ۱۸",
]


def sentences():
    tsv = urllib.request.urlopen(f"{FLEURS}/test.tsv").read().decode("utf-8")
    rows = [r for r in csv.reader(io.StringIO(tsv), delimiter="\t") if len(r) >= 3]
    seen, out = set(), []
    for r in rows:
        if r[2] not in seen:
            seen.add(r[2])
            out.append(r[2])
    return out[:60] + EXTRA


def wrap(draw, text, font, width):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=font, direction="rtl", language="fa") <= width:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def make(d):
    from PIL import Image, ImageDraw, ImageFilter, ImageFont, features
    assert features.check("raqm"), "Pillow needs libraqm to shape Persian"
    os.makedirs(d, exist_ok=True)
    fonts = [f for f in os.environ["FONTS"].split(",") if f]
    rnd = random.Random(3)
    sents = sentences()
    pages = []
    for i in range(12):
        font_path = fonts[i % len(fonts)]
        size = [30, 36, 26][i % 3]
        font = ImageFont.truetype(font_path, size)
        chosen = rnd.sample(sents, 5)
        W, margin = 1240, 80
        img = Image.new("L", (W, 1754), 255)
        draw = ImageDraw.Draw(img)
        y, lines_out = margin, []
        for s in chosen:
            for line in wrap(draw, s, font, W - 2 * margin):
                draw.text((W - margin, y), line, font=font, fill=0, direction="rtl", language="fa", anchor="ra")
                lines_out.append(line)
                y += int(size * 1.8)
            y += size
        img = img.crop((0, 0, W, min(1754, y + margin)))
        kind = ["scan", "photo", "photo"][i % 3]
        if kind == "photo":
            img = img.rotate(rnd.uniform(-2.5, 2.5), expand=True, fillcolor=235, resample=Image.BICUBIC)
            # uneven light, blur and sensor noise, then JPEG
            w, h = img.size
            grad = Image.linear_gradient("L").resize((w, h)).point(lambda v: 200 + v * 55 // 255)
            img = Image.composite(img, grad, img.point(lambda v: 255 - v))
            img = img.filter(ImageFilter.GaussianBlur(0.8))
            px = img.load()
            for _ in range(w * h // 40):
                x, yy = rnd.randrange(w), rnd.randrange(h)
                px[x, yy] = max(0, min(255, px[x, yy] + rnd.randint(-40, 40)))
            buf = io.BytesIO()
            img.convert("RGB").save(buf, "JPEG", quality=70)
            img = Image.open(io.BytesIO(buf.getvalue()))
        name = os.path.join(d, f"page{i:02d}-{kind}-{os.path.basename(font_path).split('.')[0]}.png")
        img.save(name)
        pages.append({"image": name, "text": "\n".join(lines_out), "kind": kind})
    json.dump(pages, open(os.path.join(d, "pages.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    open(os.path.join(d, "list.txt"), "w").write("\n".join(p["image"] for p in pages) + "\n")
    print(f"made {len(pages)} pages")


DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")


def norm(t):
    t = t.replace("ي", "ی").replace("ى", "ی").replace("ك", "ک").replace("ة", "ه").replace("‌", "")
    t = t.translate(DIGITS)
    t = re.sub(r"[ً-ٰٟ]", "", t)
    t = re.sub(r"[^\w]", "", t)
    return t


def edits(a, b):
    prev = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        cur = [i]
        for j, y in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (x != y)))
        prev = cur
    return prev[-1]


def score(d, results):
    pages = {p["image"]: p for p in json.load(open(os.path.join(d, "pages.json"), encoding="utf-8"))}
    by = {}
    for line in open(results, encoding="utf-8"):
        if not line.startswith("RESULT\t"):
            continue
        _, img, ms, text = line.rstrip("\n").split("\t", 3)
        p = pages[img]
        r, h = norm(p["text"]), norm(text.replace("\\n", "\n"))
        e = by.setdefault(p["kind"], [0, 0, 0.0, 0])
        e[0] += edits(r, h)
        e[1] += len(r)
        e[2] += float(ms)
        e[3] += 1
    for k, (ed, n, ms, c) in sorted(by.items()):
        print(f"SCORE {k}: pages={c} CER={100 * ed / max(n, 1):.1f}% avg={ms / max(c, 1) / 1000:.1f}s")


if __name__ == "__main__":
    if sys.argv[1] == "make":
        make(sys.argv[2])
    else:
        score(sys.argv[2], sys.argv[3])
