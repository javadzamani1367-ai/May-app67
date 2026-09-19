#!/usr/bin/env python3
"""Checks that catch, offline, what CI otherwise finds five minutes later."""
import collections
import pathlib
import re
import sys

root = pathlib.Path("android/app/src/main")
problems = []

# 1. duplicate resource names — the merger refuses these
for f in (root / "res").rglob("*.xml"):
    names = re.findall(r'<(?:string|string-array|plurals) name="([\w_]+)"', f.read_text(encoding="utf-8"))
    for name, count in collections.Counter(names).items():
        if count > 1:
            problems.append(f"duplicate resource {name} in {f}")

# 2. references to resources that do not exist
defined = set()
for f in (root / "res").rglob("*.xml"):
    t = f.read_text(encoding="utf-8")
    defined |= set(re.findall(r'<(?:string|string-array|plurals) name="([\w_]+)"', t))
defined.add("app_name")  # supplied per build flavour
for f in (root / "java").rglob("*.kt"):
    for name in re.findall(r"R\.(?:string|array|plurals)\.([\w_]+)", f.read_text(encoding="utf-8")):
        if name not in defined:
            problems.append(f"missing resource {name} referenced by {f}")

# 3. duplicate declarations within one scope. Split on top level declarations
#    first: one `of()` per enum companion and one `listFor()` per DAO are both
#    legal, and a check that flags them is a check nobody will keep running.
for f in (root / "java").rglob("*.kt"):
    text = f.read_text(encoding="utf-8")
    boundaries = [m.start() for m in re.finditer(
        r"(?m)^(?:@\w+\s*)?(?:public |internal |private )?(?:abstract |sealed |data |open )?"
        r"(?:class|object|interface|enum class)\s", text)]
    boundaries.append(len(text))
    scopes = [text[:boundaries[0]]] if boundaries and boundaries[0] > 0 else []
    scopes += [text[boundaries[i]:boundaries[i + 1]] for i in range(len(boundaries) - 1)]
    for scope in scopes:
        sigs = re.findall(
            r"^\s*(?:private |internal |public )?(?:suspend )?fun\s+([A-Za-z0-9_]+\s*\([^)]*\))",
            scope, re.M)
        norm = [re.sub(r"\s+", "", sig) for sig in sigs]
        for sig, count in collections.Counter(norm).items():
            if count > 1:
                problems.append(f"duplicate declaration {sig} in {f}")
    for line, count in collections.Counter(re.findall(r"(?m)^import .*$", text)).items():
        if count > 1:
            problems.append(f"duplicate import in {f}: {line}")

# 4. the 300 line rule from CLAUDE.md
for f in list((root / "java").rglob("*.kt")):
    lines = len(f.read_text(encoding="utf-8").splitlines())
    if lines > 300:
        problems.append(f"{f} is {lines} lines, over the 300 line rule")

for problem in problems:
    print("FAIL:", problem)
print(f"{len(problems)} problem(s)")
sys.exit(1 if problems else 0)
