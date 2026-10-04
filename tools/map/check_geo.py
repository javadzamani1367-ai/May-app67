#!/usr/bin/env python3
"""
Reads an address index back and checks it is fit to ship:

    python3 check_geo.py ilam.geo            # the real extract: Ilam must be in it
    python3 check_geo.py sample.geo --sample # the hand-made sample in this folder

It is also the reference reader for the format, independent of the app's.
"""
import math
import struct
import sys

ILAM = (33.6374, 46.4227)


def read(path):
    data = open(path, "rb").read()
    pos = 0

    def take(fmt):
        nonlocal pos
        values = struct.unpack_from(fmt, data, pos)
        pos += struct.calcsize(fmt)
        return values

    if data[:5] != b"TKGEO":
        raise SystemExit("not an address index")
    pos = 5
    (version,) = take(">B")
    bbox = take(">iiii")
    (n,) = take(">i")
    strings = []
    for _ in range(n):
        (length,) = take(">H")
        strings.append(data[pos:pos + length].decode("utf-8"))
        pos += length
    places = []
    for _ in range(take(">i")[0]):
        name, kind, lat, lon = take(">iBii")
        places.append((strings[name], kind, lat / 1e6, lon / 1e6))
    streets = []
    for _ in range(take(">i")[0]):
        name, rank, count = take(">iBi")
        points = [take(">ii") for _ in range(count)]
        streets.append((strings[name], rank, [(a / 1e6, b / 1e6) for a, b in points]))
    areas = []
    for _ in range(take(">i")[0]):
        name, level, rings = take(">iBi")
        ring_list = []
        for _ in range(rings):
            outer, count = take(">Bi")
            ring_list.append((outer, [(a / 1e6, b / 1e6) for a, b in (take(">ii") for _ in range(count))]))
        areas.append((strings[name], level, ring_list))
    if pos != len(data):
        raise SystemExit(f"trailing bytes: read {pos} of {len(data)}")
    return version, [v / 1e6 for v in bbox], places, streets, areas


def metres(a, b):
    k = 111_320.0
    return math.hypot((a[0] - b[0]) * k, (a[1] - b[1]) * k * math.cos(math.radians(a[0])))


def inside(point, ring):
    lat, lon = point
    hit = False
    for (a_lat, a_lon), (b_lat, b_lon) in zip(ring, ring[1:] + ring[:1]):
        if (a_lat > lat) != (b_lat > lat):
            if lon < a_lon + (lat - a_lat) * (b_lon - a_lon) / (b_lat - a_lat):
                hit = not hit
    return hit


def main():
    version, bbox, places, streets, areas = read(sys.argv[1])
    sample = "--sample" in sys.argv
    print(f"version={version} bbox={bbox} places={len(places)} streets={len(streets)} areas={len(areas)}")
    problems = []
    if not any(kind == 0 and metres((lat, lon), ILAM) < 5000 for _, kind, lat, lon in places):
        problems.append("no city within 5 km of Ilam's centre")
    if len(streets) < (2 if sample else 300):
        problems.append(f"too few named streets: {len(streets)}")
    if not any(level == 5 and any(o and inside(ILAM, r) for o, r in rings) for _, level, rings in areas):
        problems.append("no county boundary around Ilam's centre")
    if sample and "پیاده‌رو" in {s[0] for s in streets}:
        problems.append("a footway was kept")
    for p in problems:
        print("PROBLEM:", p)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
