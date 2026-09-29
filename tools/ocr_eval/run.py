#!/usr/bin/env python3
"""Runs one OCR engine over the pages in LIST and prints RESULT lines for eval.py.

usage: run.py ENGINE LIST
engines: tesseract-best, tesseract-fast, paddle, easyocr
"""
import os
import subprocess
import sys
import time


def lines_to_text(items):
    """items: (box[x0,y0,x1,y1], text). Groups into lines top to bottom, right to left inside."""
    items = sorted(items, key=lambda it: (it[0][1] + it[0][3]) / 2)
    lines, cur, cy, ch = [], [], None, None
    for box, text in items:
        y = (box[1] + box[3]) / 2
        h = box[3] - box[1]
        if cy is not None and abs(y - cy) > max(h, ch) * 0.6:
            lines.append(cur)
            cur = []
        cur.append((box, text))
        cy, ch = y, h
    if cur:
        lines.append(cur)
    return "\n".join(" ".join(t for _, t in sorted(line, key=lambda it: -it[0][2])) for line in lines)


def main():
    engine, lst = sys.argv[1], sys.argv[2]
    images = [l.strip() for l in open(lst) if l.strip()]
    if engine.startswith("tesseract"):
        tessdata = os.environ["TESSDATA_" + engine.split("-")[1].upper()]
        def run(img):
            out = subprocess.run(["tesseract", img, "-", "-l", "fas", "--psm", "6", "--tessdata-dir", tessdata], capture_output=True, text=True)
            return out.stdout
    elif engine == "paddle":
        from paddleocr import PaddleOCR
        ocr = PaddleOCR(lang="fa", use_doc_orientation_classify=False, use_doc_unwarping=False, use_textline_orientation=False)
        def run(img):
            res = ocr.predict(img)[0]
            boxes = res["rec_boxes"]
            return lines_to_text([(list(map(float, b)), t) for b, t in zip(boxes, res["rec_texts"])])
    elif engine == "easyocr":
        import easyocr
        reader = easyocr.Reader(["fa"], gpu=False)
        def run(img):
            res = reader.readtext(img)
            items = []
            for pts, text, _ in res:
                xs = [p[0] for p in pts]
                ys = [p[1] for p in pts]
                items.append(([min(xs), min(ys), max(xs), max(ys)], text))
            return lines_to_text(items)
    else:
        raise SystemExit("unknown engine " + engine)
    run(images[0])  # warm up
    for img in images:
        t = time.time()
        try:
            text = run(img)
        except Exception as e:  # keep going, score as empty
            print("ERROR", img, e, file=sys.stderr)
            text = ""
        ms = (time.time() - t) * 1000
        print(f"RESULT\t{img}\t{ms:.0f}\t" + text.replace("\t", " ").replace("\n", "\\n"), flush=True)


if __name__ == "__main__":
    main()
