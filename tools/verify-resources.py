#!/usr/bin/env python3
"""Checks that catch, offline, what CI otherwise finds five minutes later."""
import collections
import pathlib
import re
import sys

root = pathlib.Path("android/app/src/main")
# Every module whose sources and resources the checks cover. The shared
# module's strings reach the apps through a transitive R, so a name defined in
# both would silently resolve to the app's copy: a duplicate across modules is
# as much a mistake as one inside a folder.
core = pathlib.Path("android/core/src/main")
field = pathlib.Path("android/field/src/main")
modules = [core, root, field]
problems = []
# The two apps never share a process, so a name defined in both is fine; a
# name defined in core and in either app is not.
pairs = [(core, root), (core, field)]

# 1. duplicate resource names — the merger refuses these, within one file and
#    across the files of one values folder (strings.xml and strings_ui.xml).
for pair in pairs:
    seen = collections.defaultdict(list)
    for module in pair:
        for folder in (module / "res").glob("values*"):
            for f in folder.glob("*.xml"):
                for name in re.findall(r'<(?:string|string-array|plurals) name="([\w_]+)"', f.read_text(encoding="utf-8")):
                    seen[(folder.name, name)].append(f"{module.parts[1]}/{f.name}")
    for (folder, name), files in seen.items():
        if len(files) > 1:
            problems.append(f"duplicate resource {name} in {folder}: {', '.join(files)}")

# 2. references to resources that do not exist — each module against what it
#    can actually see: itself and core.
def defined_in(*mods):
    names = {"app_name"}  # supplied per build flavour in the inspection app
    for module in mods:
        for f in (module / "res").rglob("*.xml"):
            names |= set(re.findall(r'<(?:string|string-array|plurals) name="([\w_]+)"', f.read_text(encoding="utf-8")))
    return names

visible = {core: defined_in(core) - {"app_name"}, root: defined_in(core, root), field: defined_in(core, field)}
kotlin = [f for module in modules for f in (module / "java").rglob("*.kt")]
for module in modules:
    for f in (module / "java").rglob("*.kt"):
        for name in re.findall(r"R\.(?:string|array|plurals)\.([\w_]+)", f.read_text(encoding="utf-8")):
            if name not in visible[module]:
                problems.append(f"missing resource {name} referenced by {f}")

# 3. duplicate declarations within one scope. Split on top level declarations
#    first: one `of()` per enum companion and one `listFor()` per DAO are both
#    legal, and a check that flags them is a check nobody will keep running.
for f in kotlin:
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
for f in kotlin:
    lines = len(f.read_text(encoding="utf-8").splitlines())
    if lines > 300:
        problems.append(f"{f} is {lines} lines, over the 300 line rule")

# 5. the county arrays must line up
#
# `county_codes` is read by position: the code of a county is whatever sits at
# the same index in the other array. If the two ever differ in length, the
# counties past the end quietly fall back to 401 and every tracking code they
# produce is wrong, with nothing anywhere saying so.
strings = (root / "res" / "values" / "strings.xml").read_text(encoding="utf-8")


def array_items(name):
    block = re.search(
        rf'<string-array name="{name}">(.*?)</string-array>', strings, re.S)
    return re.findall(r"<item>(.*?)</item>", block.group(1), re.S) if block else None


county_names = array_items("county_names")
county_codes = array_items("county_codes")
if county_names is None or county_codes is None:
    problems.append("county_names or county_codes is missing from strings.xml")
elif len(county_names) != len(county_codes):
    problems.append(
        f"county_names has {len(county_names)} entries but county_codes has "
        f"{len(county_codes)}; codes are matched by position"
    )
else:
    for code, count in collections.Counter(county_codes).items():
        if count > 1:
            problems.append(f"area code {code} appears {count} times in county_codes")
    for code in county_codes:
        if not re.fullmatch(r"\d{3}", code.strip()):
            problems.append(f"area code {code!r} is not three digits")

# 6. every folder FileStore writes to must be declared to FileProvider
#
# Sharing a file from a folder that file_paths.xml does not list throws
# IllegalArgumentException and takes the app down with it — and only when
# someone actually shares that kind of file, which is how `attachments/` went
# missing until a dispatch with a document killed the app in the field.
store = (modules[0] / "java" / "ir" / "ilam" / "inspection" / "util" / "FileStore.kt")
paths_xml = (root / "res" / "xml" / "file_paths.xml")
# A check whose inputs moved must fail, not pass quietly: when FileStore went
# to the shared module this one would otherwise have stopped checking anything.
for required in (store, paths_xml):
    if not required.exists():
        problems.append(f"{required} is missing; the FileProvider check cannot run")
if store.exists() and paths_xml.exists():
    folders = set(re.findall(r'const val [A-Z_]+ = "([a-z]+)"', store.read_text(encoding="utf-8")))
    declared = set(re.findall(r'<files-path[^>]*path="([^"/]+)', paths_xml.read_text(encoding="utf-8")))
    for folder in sorted(folders - declared):
        problems.append(
            f"FileStore writes to '{folder}/' but file_paths.xml does not declare it; "
            "sharing from there crashes the app"
        )

for problem in problems:
    print("FAIL:", problem)
print(f"{len(problems)} problem(s)")
sys.exit(1 if problems else 0)
