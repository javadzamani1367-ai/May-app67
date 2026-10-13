#!/usr/bin/env python3
"""
Turns the user guides in docs/guides/*.md into printable, right-to-left PDFs.

    python3 tools/guides/build.py OUTPUT_DIR [--font-dir DIR] [--chrome PATH]

Each guide becomes one HTML page with the Vazirmatn font embedded (base64, the
same approach the Android report uses), printed to A4 PDF by headless Chromium.
The font is not in the repository; give a folder holding Vazirmatn-Regular.ttf
and Vazirmatn-Bold.ttf, or the script downloads release v33.003 (SIL OFL).

Needs: markdown-it-py (pip install markdown-it-py) and a Chromium or Chrome.
"""
import argparse
import base64
import io
import pathlib
import shutil
import subprocess
import sys
import urllib.request
import zipfile

from markdown_it import MarkdownIt

ROOT = pathlib.Path(__file__).resolve().parents[2]
GUIDES = ROOT / "docs" / "guides"
ORDER = ["expert", "field", "manager", "units", "deploy"]
FONT_URL = "https://github.com/rastikerdar/vazirmatn/releases/download/v33.003/vazirmatn-v33.003.zip"

CSS = """
@font-face { font-family: Vazirmatn; src: url(data:font/ttf;base64,{regular}); font-weight: 400; }
@font-face { font-family: Vazirmatn; src: url(data:font/ttf;base64,{bold}); font-weight: 700; }
@page { size: A4; margin: 16mm 15mm 18mm; }
body { font-family: Vazirmatn, Tahoma, sans-serif; direction: rtl; color: #0f172a; font-size: 11pt; line-height: 1.85; }
h1 { color: #0b1f3a; font-size: 19pt; border-bottom: 3px solid #0d9488; padding-bottom: 6px; margin-top: 0; }
h2 { color: #0b1f3a; font-size: 14.5pt; margin-top: 20px; page-break-after: avoid; }
h3 { color: #0f766e; font-size: 12pt; page-break-after: avoid; }
table { border-collapse: collapse; width: 100%; margin: 8px 0 12px; page-break-inside: avoid; }
th, td { border: 1px solid #cbd5e1; padding: 5px 8px; text-align: right; vertical-align: top; }
th { background: #f1f5f9; }
code { direction: ltr; unicode-bidi: embed; background: #f1f5f9; padding: 0 4px; border-radius: 4px; font-size: 9.5pt; }
pre { direction: ltr; text-align: left; background: #f1f5f9; padding: 8px; border-radius: 6px; }
blockquote { margin: 10px 0; padding: 6px 12px; border-inline-start: 4px solid #d97706; background: #fffbeb; }
hr { border: 0; border-top: 1px solid #e2e8f0; margin: 16px 0; }
li { margin: 2px 0; }
"""


def fonts(folder):
    if folder:
        base = pathlib.Path(folder)
        return (base / "Vazirmatn-Regular.ttf").read_bytes(), (base / "Vazirmatn-Bold.ttf").read_bytes()
    data = urllib.request.urlopen(FONT_URL, timeout=60).read()
    archive = zipfile.ZipFile(io.BytesIO(data))

    def pick(name):
        match = [n for n in archive.namelist() if n.endswith("fonts/ttf/" + name)]
        if not match:
            raise SystemExit(f"{name} not found in the font archive")
        return archive.read(match[0])
    return pick("Vazirmatn-Regular.ttf"), pick("Vazirmatn-Bold.ttf")


def html(text, title, regular, bold):
    # CommonMark, as GitHub renders these files: nested lists by their real indent.
    body = MarkdownIt("commonmark").enable("table").render(text)
    css = CSS.replace("{regular}", base64.b64encode(regular).decode()).replace("{bold}", base64.b64encode(bold).decode())
    return (f'<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><title>{title}</title>'
            f"<style>{css}</style></head><body>{body}</body></html>")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("out")
    parser.add_argument("--font-dir")
    parser.add_argument("--chrome", default=shutil.which("chromium") or shutil.which("google-chrome") or "chromium")
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    regular, bold = fonts(args.font_dir)
    for name in ORDER:
        source = (GUIDES / f"{name}.md").read_text(encoding="utf-8")
        title = source.splitlines()[0].lstrip("# ").strip()
        page = out / f"{name}.html"
        page.write_text(html(source, title, regular, bold), encoding="utf-8")
        pdf = out / f"tavankav-guide-{name}.pdf"
        subprocess.run([args.chrome, "--headless=new", "--no-sandbox", "--disable-gpu", "--no-pdf-header-footer",
                        f"--print-to-pdf={pdf}", page.as_uri()], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
        page.unlink()
        print(f"{pdf.name}: {pdf.stat().st_size // 1024} KB")


if __name__ == "__main__":
    sys.exit(main())
