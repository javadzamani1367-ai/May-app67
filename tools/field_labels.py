#!/usr/bin/env python3
"""
Writes server/api/lib/FieldLabels.php from the field app's checklist labels.

    python3 tools/field_labels.py           # write it
    python3 tools/field_labels.py --check   # fail if it is out of date (CI)

The phone sends checklist answers as fixed English keys ("fan_noise"); the
web panel has to show the same Persian words the phone showed. The app's
ui/report/ReportLabels.kt says which string each key uses, and the strings
are in the app's resources — so the PHP file is generated from those, and
never edited by hand.
"""
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
LABELS_KT = ROOT / "android/field/src/main/java/ir/ilam/inspection/field/ui/report/ReportLabels.kt"
RES = [ROOT / "android/field/src/main/res/values", ROOT / "android/core/src/main/res/values"]
OUT = ROOT / "server/api/lib/FieldLabels.php"


def strings():
    found = {}
    for folder in RES:
        for path in sorted(folder.glob("*.xml")):
            for node in ET.parse(path).getroot().iter("string"):
                found[node.get("name")] = "".join(node.itertext()).replace("\\'", "'").strip('"')
    return found


def maps(texts):
    source = LABELS_KT.read_text(encoding="utf-8")
    result = {}
    for name, body in re.findall(r"val (\w+) = mapOf\((.*?)\n    \)", source, re.S):
        pairs = re.findall(r'"([a-z_0-9]+)" to R\.string\.(\w+)', body)
        missing = [res for _, res in pairs if res not in texts]
        if missing:
            raise SystemExit(f"{name}: no string for {missing}")
        result[name] = [(key, texts[res]) for key, res in pairs]
    if not result:
        raise SystemExit("no label maps found in ReportLabels.kt")
    return result


def php(groups):
    def quote(text):
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'"
    lines = [
        "<?php",
        "declare(strict_types=1);",
        "",
        "/**",
        " * برچسب فارسی کلیدهای چک‌لیست گزارش‌های میدانی، همان که گوشی نشان می‌دهد.",
        " * ساخته‌شده با tools/field_labels.py از ReportLabels.kt و رشته‌های اپ؛",
        " * دستی ویرایش نکنید — CI ناهمخوانی را رد می‌کند.",
        " */",
        "final class FieldLabels",
        "{",
    ]
    for name, pairs in groups.items():
        lines.append(f"    public const {name} = [")
        lines += [f"        {quote(key)} => {quote(text)}," for key, text in pairs]
        lines.append("    ];")
        lines.append("")
    lines[-1] = "}"
    return "\n".join(lines) + "\n"


def main():
    text = php(maps(strings()))
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != text:
            raise SystemExit("server/api/lib/FieldLabels.php is out of date: run python3 tools/field_labels.py")
        print("FieldLabels.php is up to date")
        return
    OUT.write_text(text, encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
