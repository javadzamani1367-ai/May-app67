#!/usr/bin/env python3
"""
Builds the offline address index for the field app from an OSM extract.

    python3 build_geo.py ilam.osm.pbf ilam.geo

What goes in: named streets (as lines), named places (city down to
neighbourhood, as points) and county and district boundaries (as polygons).
That is everything the phone needs to say "Ivan county, village X, near
street Y (40 m)" with no network. The format is a plain binary file the
app reads in one pass; it is described in docs/FIELD-APP.md and in
android/core/.../util/geo/GeoIndex.kt, and the two must change together.

Needs pyosmium (pip install osmium).
"""
import math
import struct
import sys

import osmium

VERSION = 1

# Kinds of place, as the app numbers them.
PLACE_KINDS = {
    "city": 0, "town": 1, "village": 2, "hamlet": 3,
    "suburb": 4, "neighbourhood": 5, "quarter": 5, "locality": 6, "isolated_dwelling": 6,
}

# Street rank: lower is more important. Paths and footways are left out;
# nobody gives an address by a footpath.
STREET_RANKS = {
    "motorway": 0, "trunk": 0, "primary": 1, "secondary": 2, "tertiary": 3,
    "unclassified": 4, "residential": 4, "living_street": 4, "pedestrian": 4, "road": 4,
    "service": 5, "track": 5,
}

# Iran: 5 is the county (shahrestan), 6 the district (bakhsh).
ADMIN_LEVELS = {"5", "6"}

STREET_TOLERANCE_M = 2.0
AREA_TOLERANCE_M = 30.0


def name_of(tags):
    """Persian name where it is given separately, the plain name otherwise."""
    return (tags.get("name:fa") or tags.get("name") or "").strip()


def e6(value):
    return int(round(value * 1_000_000))


def simplify(points, tolerance_m):
    """Douglas-Peucker on (lat, lon) pairs, distances in metres."""
    if len(points) < 3:
        return points
    lat0 = math.radians(sum(p[0] for p in points) / len(points))
    kx = 111_320.0 * math.cos(lat0)
    ky = 111_320.0
    xy = [(p[1] * kx, p[0] * ky) for p in points]
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = xy[a]
        bx, by = xy[b]
        dx, dy = bx - ax, by - ay
        length2 = dx * dx + dy * dy
        worst, worst_i = -1.0, -1
        for i in range(a + 1, b):
            px, py = xy[i]
            if length2 == 0:
                d = math.hypot(px - ax, py - ay)
            else:
                t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length2))
                d = math.hypot(px - (ax + t * dx), py - (ay + t * dy))
            if d > worst:
                worst, worst_i = d, i
        if worst > tolerance_m:
            keep[worst_i] = True
            stack.append((a, worst_i))
            stack.append((worst_i, b))
    return [p for p, k in zip(points, keep) if k]


class Collector(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.places = []    # (name, kind, lat, lon)
        self.streets = []   # (name, rank, [(lat, lon)])
        self.areas = []     # (name, level, [(outer, [(lat, lon)])])

    def node(self, n):
        kind = PLACE_KINDS.get(n.tags.get("place", ""))
        name = name_of(n.tags)
        if kind is not None and name and n.location.valid():
            self.places.append((name, kind, n.location.lat, n.location.lon))

    def way(self, w):
        rank = STREET_RANKS.get(w.tags.get("highway", ""))
        name = name_of(w.tags)
        if rank is None or not name:
            return
        points = [(nd.lat, nd.lon) for nd in w.nodes if nd.location.valid()]
        if len(points) >= 2:
            self.streets.append((name, rank, simplify(points, STREET_TOLERANCE_M)))

    def area(self, a):
        tags = a.tags
        name = name_of(tags)
        if not name:
            return
        if tags.get("boundary") == "administrative" and tags.get("admin_level") in ADMIN_LEVELS:
            rings = []
            for outer in a.outer_rings():
                rings.append((1, simplify([(n.lat, n.lon) for n in outer], AREA_TOLERANCE_M)))
                for inner in a.inner_rings(outer):
                    rings.append((0, simplify([(n.lat, n.lon) for n in inner], AREA_TOLERANCE_M)))
            rings = [r for r in rings if len(r[1]) >= 3]
            if rings:
                self.areas.append((name, int(tags["admin_level"]), rings))
        # A place drawn as an area (a neighbourhood outline, a village
        # boundary) is kept as a point at its middle, like the others.
        kind = PLACE_KINDS.get(tags.get("place", ""))
        if kind is not None:
            points = [(n.lat, n.lon) for outer in a.outer_rings() for n in outer]
            if points:
                lat = sum(p[0] for p in points) / len(points)
                lon = sum(p[1] for p in points) / len(points)
                self.places.append((name, kind, lat, lon))


def write(path, c):
    strings, index = [], {}

    def sid(text):
        if text not in index:
            index[text] = len(strings)
            strings.append(text)
        return index[text]

    for name, *_ in c.places + c.streets + c.areas:
        sid(name)

    all_points = [(p[2], p[3]) for p in c.places] + [pt for s in c.streets for pt in s[2]]
    if not all_points:
        raise SystemExit("no named places or streets found: wrong extract?")
    lats = [p[0] for p in all_points]
    lons = [p[1] for p in all_points]

    with open(path, "wb") as f:
        f.write(b"TKGEO" + struct.pack(">B", VERSION))
        f.write(struct.pack(">iiii", e6(min(lats)), e6(min(lons)), e6(max(lats)), e6(max(lons))))
        f.write(struct.pack(">i", len(strings)))
        for text in strings:
            data = text.encode("utf-8")
            f.write(struct.pack(">H", len(data)) + data)
        f.write(struct.pack(">i", len(c.places)))
        for name, kind, lat, lon in c.places:
            f.write(struct.pack(">iBii", sid(name), kind, e6(lat), e6(lon)))
        f.write(struct.pack(">i", len(c.streets)))
        for name, rank, points in c.streets:
            f.write(struct.pack(">iBi", sid(name), rank, len(points)))
            f.write(b"".join(struct.pack(">ii", e6(lat), e6(lon)) for lat, lon in points))
        f.write(struct.pack(">i", len(c.areas)))
        for name, level, rings in c.areas:
            f.write(struct.pack(">iBi", sid(name), level, len(rings)))
            for outer, points in rings:
                f.write(struct.pack(">Bi", outer, len(points)))
                f.write(b"".join(struct.pack(">ii", e6(lat), e6(lon)) for lat, lon in points))


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    collector = Collector()
    collector.apply_file(sys.argv[1], locations=True)
    write(sys.argv[2], collector)
    points = sum(len(s[2]) for s in collector.streets)
    print(f"places={len(collector.places)} streets={len(collector.streets)} "
          f"street_points={points} areas={len(collector.areas)}")


if __name__ == "__main__":
    main()
