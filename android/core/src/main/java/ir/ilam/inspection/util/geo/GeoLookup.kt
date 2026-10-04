package ir.ilam.inspection.util.geo

import kotlin.math.cos
import kotlin.math.floor

/** A place found near a point, and how far it is. */
data class NearPlace(val name: String, val kind: Int, val metres: Double, val inside: Boolean)

/** Everything the index can say about one point. Any part may be missing. */
data class GeoMatch(
    val county: String? = null,
    val district: String? = null,
    /** City, town, village or hamlet. */
    val settlement: NearPlace? = null,
    /** Suburb or neighbourhood, only inside a city or town. */
    val neighbourhood: NearPlace? = null,
    val street: String? = null,
    val streetMetres: Double? = null
) {
    val isEmpty: Boolean get() = county == null && settlement == null && street == null
}

object PlaceKind {
    const val CITY = 0
    const val TOWN = 1
    const val VILLAGE = 2
    const val HAMLET = 3
    const val SUBURB = 4
    const val NEIGHBOURHOOD = 5

    /** How far a place of this kind reaches, in metres: a city is big, a hamlet a few houses. */
    fun reach(kind: Int): Double = when (kind) {
        CITY -> 7000.0
        TOWN -> 3500.0
        VILLAGE -> 1800.0
        HAMLET -> 900.0
        SUBURB -> 1500.0
        NEIGHBOURHOOD -> 800.0
        else -> 600.0
    }

    /** Within this share of its reach, a point is in the place, not just near it. */
    const val INSIDE_SHARE = 0.4
}

/** Admin levels as Iran uses them in OpenStreetMap. */
object AdminLevel {
    const val COUNTY = 5
    const val DISTRICT = 6
}

/** A street further than this is not "the street the point is on". */
const val STREET_REACH_METRES = 250.0

fun GeoIndex.lookup(latitude: Double, longitude: Double): GeoMatch {
    val settlement = nearest(latitude, longitude, setOf(PlaceKind.CITY, PlaceKind.TOWN, PlaceKind.VILLAGE, PlaceKind.HAMLET))
    val urban = settlement == null || settlement.kind == PlaceKind.CITY || settlement.kind == PlaceKind.TOWN
    val neighbourhood = if (urban) nearest(latitude, longitude, setOf(PlaceKind.SUBURB, PlaceKind.NEIGHBOURHOOD)) else null
    val (street, streetMetres) = nearestStreet(latitude, longitude)
    return GeoMatch(
        county = area(latitude, longitude, AdminLevel.COUNTY),
        district = area(latitude, longitude, AdminLevel.DISTRICT),
        settlement = settlement,
        neighbourhood = neighbourhood,
        street = street,
        streetMetres = streetMetres
    )
}

/** The place whose reach the point is deepest within, of the given kinds. */
private fun GeoIndex.nearest(lat: Double, lon: Double, kinds: Set<Int>): NearPlace? {
    var best: GeoPlace? = null
    var bestShare = Double.MAX_VALUE
    var bestMetres = 0.0
    for (p in places) {
        if (p.kind !in kinds) continue
        val d = metres(lat, lon, p.latitude, p.longitude)
        val share = d / PlaceKind.reach(p.kind)
        if (share <= 1.0 && share < bestShare) {
            best = p; bestShare = share; bestMetres = d
        }
    }
    return best?.let { NearPlace(it.name, it.kind, bestMetres, bestShare <= PlaceKind.INSIDE_SHARE) }
}

/** The closest named street within [STREET_REACH_METRES]; ties go to the more important road. */
private fun GeoIndex.nearestStreet(lat: Double, lon: Double): Pair<String?, Double?> {
    val kx = 111_320.0 * cos(Math.toRadians(lat))
    val ky = 111_320.0
    val span = 1 + (STREET_REACH_METRES / (GeoIndex.CELL * ky)).toInt()
    val cy = floor(lat / GeoIndex.CELL)
    val cx = floor(lon / GeoIndex.CELL)
    var best = -1
    var bestMetres = Double.MAX_VALUE
    val seen = HashSet<Int>()
    for (dy in -span..span) for (dx in -span..span) {
        val key = GeoIndex.cell((cy + dy) * GeoIndex.CELL + GeoIndex.CELL / 2, (cx + dx) * GeoIndex.CELL + GeoIndex.CELL / 2)
        val segments = grid[key] ?: continue
        for (s in segments) {
            if (!seen.add(s)) continue
            val d = segmentMetres(lat, lon, s, kx, ky)
            val street = segStreet[s]
            val better = d < bestMetres - TIE_METRES ||
                (d < bestMetres + TIE_METRES && best >= 0 && streetRanks[street] < streetRanks[segStreet[best]])
            if (better) { best = s; bestMetres = d }
        }
    }
    if (best < 0 || bestMetres > STREET_REACH_METRES) return null to null
    return streetNames[segStreet[best]] to bestMetres
}

private fun GeoIndex.segmentMetres(lat: Double, lon: Double, s: Int, kx: Double, ky: Double): Double {
    val ax = (segA[s * 2 + 1] - lon) * kx; val ay = (segA[s * 2] - lat) * ky
    val bx = (segB[s * 2 + 1] - lon) * kx; val by = (segB[s * 2] - lat) * ky
    val dx = bx - ax; val dy = by - ay
    val length2 = dx * dx + dy * dy
    val t = if (length2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / length2).coerceIn(0.0, 1.0)
    val px = ax + t * dx; val py = ay + t * dy
    return Math.sqrt(px * px + py * py)
}

/** The outline of this level the point lies in, holes excluded. */
private fun GeoIndex.area(lat: Double, lon: Double, level: Int): String? =
    areas.filter { it.level == level && lat in it.minLat..it.maxLat && lon in it.minLon..it.maxLon }
        .firstOrNull { contains(it, lat, lon) }?.name

internal fun contains(area: GeoArea, lat: Double, lon: Double): Boolean {
    var inOuter = false
    area.rings.forEachIndexed { i, ring ->
        if (inRing(ring, lat, lon)) {
            if (area.outer[i]) inOuter = true else return false
        }
    }
    return inOuter
}

private fun inRing(ring: DoubleArray, lat: Double, lon: Double): Boolean {
    val n = ring.size / 2
    var hit = false
    var j = n - 1
    for (i in 0 until n) {
        val ai = ring[i * 2]; val oi = ring[i * 2 + 1]
        val aj = ring[j * 2]; val oj = ring[j * 2 + 1]
        if ((ai > lat) != (aj > lat) && lon < oi + (lat - ai) * (oj - oi) / (aj - ai)) hit = !hit
        j = i
    }
    return hit
}

/** Two streets this close count as equally near; the bigger road names the place. */
private const val TIE_METRES = 3.0
