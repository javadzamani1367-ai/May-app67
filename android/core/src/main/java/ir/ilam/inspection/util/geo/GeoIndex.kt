package ir.ilam.inspection.util.geo

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import kotlin.math.cos

/** A named place as the index keeps it: a point, with what kind of place it is. */
class GeoPlace(val name: String, val kind: Int, val latitude: Double, val longitude: Double)

/** A county or district outline: outer rings and holes, as flat lat/lon arrays. */
class GeoArea(val name: String, val level: Int, val rings: List<DoubleArray>, val outer: BooleanArray) {
    val minLat = rings.minOf { r -> (0 until r.size / 2).minOf { r[it * 2] } }
    val maxLat = rings.maxOf { r -> (0 until r.size / 2).maxOf { r[it * 2] } }
    val minLon = rings.minOf { r -> (0 until r.size / 2).minOf { r[it * 2 + 1] } }
    val maxLon = rings.maxOf { r -> (0 until r.size / 2).maxOf { r[it * 2 + 1] } }
}

/**
 * The offline address index made by tools/map/build_geo.py: named streets,
 * places and county boundaries of Ilam. Read once into memory — a few
 * megabytes at most — with the streets cut into segments and filed in a grid,
 * so finding the nearest one looks at a handful of cells, not the province.
 *
 * The file format is defined in build_geo.py; this reader and that writer
 * change together, and check_geo.py is a second reader to compare against.
 */
class GeoIndex private constructor(
    val places: List<GeoPlace>,
    val areas: List<GeoArea>,
    internal val streetNames: Array<String>,
    internal val streetRanks: IntArray,
    /** Per segment: street index, then the two ends in degrees. */
    internal val segStreet: IntArray,
    internal val segA: DoubleArray,
    internal val segB: DoubleArray,
    internal val grid: Map<Long, IntArray>
) {
    val streetCount: Int get() = streetNames.size

    companion object {
        const val VERSION = 1
        /** Grid cell edge in degrees, about 450 m north to south. */
        internal const val CELL = 0.004

        internal fun cell(lat: Double, lon: Double): Long =
            (Math.floor(lat / CELL).toLong() shl 32) or (Math.floor(lon / CELL).toLong() and 0xffffffffL)

        /** Throws [GeoFormatException] for anything that is not a complete index of this version. */
        fun read(input: InputStream): GeoIndex = try {
            parse(DataInputStream(input.buffered()))
        } catch (e: EOFException) {
            throw GeoFormatException("truncated")
        }

        private fun parse(d: DataInputStream): GeoIndex {
            val magic = ByteArray(5).also { d.readFully(it) }
            if (String(magic, Charsets.US_ASCII) != "TKGEO") throw GeoFormatException("not an address index")
            val version = d.readUnsignedByte()
            if (version != VERSION) throw GeoFormatException("version $version")
            repeat(4) { d.readInt() } // bounding box: informational
            val strings = Array(count(d)) {
                val bytes = ByteArray(d.readUnsignedShort()).also { b -> d.readFully(b) }
                String(bytes, Charsets.UTF_8)
            }
            fun name(d: DataInputStream) = strings.getOrNull(d.readInt()) ?: throw GeoFormatException("bad name")
            val places = List(count(d)) {
                val n = name(d)
                val kind = d.readUnsignedByte()
                GeoPlace(n, kind, d.readInt() / E6, d.readInt() / E6)
            }

            val streetCount = count(d)
            val names = arrayOfNulls<String>(streetCount)
            val ranks = IntArray(streetCount)
            val segStreet = IntBuffer()
            val segA = DoubleBuffer()
            val segB = DoubleBuffer()
            for (s in 0 until streetCount) {
                names[s] = name(d)
                ranks[s] = d.readUnsignedByte()
                val points = readPoints(d)
                for (i in 0 until points.size / 2 - 1) {
                    segStreet.add(s)
                    segA.add(points[i * 2]); segA.add(points[i * 2 + 1])
                    segB.add(points[i * 2 + 2]); segB.add(points[i * 2 + 3])
                }
            }

            val areas = List(count(d)) {
                val n = name(d)
                val level = d.readUnsignedByte()
                val ringCount = count(d)
                val outer = BooleanArray(ringCount)
                val rings = List(ringCount) { r ->
                    outer[r] = d.readUnsignedByte() == 1
                    readPoints(d)
                }
                GeoArea(n, level, rings, outer)
            }
            if (d.read() != -1) throw GeoFormatException("trailing bytes")

            val a = segA.toArray()
            val b = segB.toArray()
            return GeoIndex(places, areas.filter { it.rings.isNotEmpty() }, names.requireNoNulls(), ranks,
                segStreet.toArray(), a, b, buildGrid(a, b))
        }

        private fun count(d: DataInputStream): Int =
            d.readInt().also { if (it < 0 || it > MAX_COUNT) throw GeoFormatException("bad count $it") }

        private fun readPoints(d: DataInputStream): DoubleArray {
            val n = count(d)
            return DoubleArray(n * 2) { d.readInt() / E6 }
        }

        /** Files each segment under every cell its bounding box touches. */
        private fun buildGrid(a: DoubleArray, b: DoubleArray): Map<Long, IntArray> {
            val cells = HashMap<Long, MutableList<Int>>()
            for (s in 0 until a.size / 2) {
                val lat0 = minOf(a[s * 2], b[s * 2]); val lat1 = maxOf(a[s * 2], b[s * 2])
                val lon0 = minOf(a[s * 2 + 1], b[s * 2 + 1]); val lon1 = maxOf(a[s * 2 + 1], b[s * 2 + 1])
                var y = Math.floor(lat0 / CELL)
                while (y <= Math.floor(lat1 / CELL)) {
                    var x = Math.floor(lon0 / CELL)
                    while (x <= Math.floor(lon1 / CELL)) {
                        cells.getOrPut(cell(y * CELL + CELL / 2, x * CELL + CELL / 2)) { ArrayList() }.add(s)
                        x++
                    }
                    y++
                }
            }
            return cells.mapValues { it.value.toIntArray() }
        }

        private const val E6 = 1_000_000.0
        private const val MAX_COUNT = 50_000_000
    }
}

class GeoFormatException(message: String) : Exception(message)

/** Growable primitive arrays: a province of coordinates boxed one by one would be tens of megabytes. */
private class DoubleBuffer {
    private var data = DoubleArray(1024)
    private var size = 0
    fun add(v: Double) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }
    fun toArray(): DoubleArray = data.copyOf(size)
}

private class IntBuffer {
    private var data = IntArray(1024)
    private var size = 0
    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }
    fun toArray(): IntArray = data.copyOf(size)
}

/** Metres between two points: flat-earth, exact enough within a few kilometres. */
internal fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val k = 111_320.0
    val dy = (lat1 - lat2) * k
    val dx = (lon1 - lon2) * k * cos(Math.toRadians((lat1 + lat2) / 2))
    return Math.sqrt(dx * dx + dy * dy)
}
