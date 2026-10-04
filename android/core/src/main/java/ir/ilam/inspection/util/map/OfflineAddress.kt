package ir.ilam.inspection.util.map

import android.content.Context
import ir.ilam.inspection.core.R
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.geo.AddressText
import ir.ilam.inspection.util.geo.AddressWords
import ir.ilam.inspection.util.geo.GeoIndex
import ir.ilam.inspection.util.geo.lookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * "Where is this point", with no network: county, village or city,
 * neighbourhood and the nearest named street, from the installed map's
 * address index. The index is read once (under a second) and kept while the
 * same map version is installed.
 */
object OfflineAddress {

    private val lock = Mutex()
    private var cached: Pair<String, GeoIndex>? = null

    /** Null when no map is installed or nothing is known about the point. */
    suspend fun describe(context: Context, latitude: Double, longitude: Double): String? = withContext(Dispatchers.Default) {
        val index = index(context) ?: return@withContext null
        val match = index.lookup(latitude, longitude)
        if (match.isEmpty) null else AddressText.compose(match, words(context)).ifBlank { null }
    }

    private suspend fun index(context: Context): GeoIndex? = lock.withLock {
        val version = OfflineMap.current(context)?.version ?: return@withLock null.also { cached = null }
        cached?.takeIf { it.first == version }?.second ?: withContext(Dispatchers.IO) {
            runCatching { OfflineMap.geoFile(context)?.inputStream()?.use { GeoIndex.read(it) } }.getOrNull()
        }?.also { cached = version to it }
    }

    private fun words(context: Context) = AddressWords(
        county = context.getString(R.string.geo_county),
        district = context.getString(R.string.geo_district),
        village = context.getString(R.string.geo_village),
        nearPlace = context.getString(R.string.geo_near_place),
        nearStreet = context.getString(R.string.geo_near_street),
        villagePrefixes = context.getString(R.string.geo_village_prefixes).split('|').map { it.trim() }.filter { it.isNotEmpty() },
        countyPrefix = context.getString(R.string.geo_county_prefix),
        districtPrefix = context.getString(R.string.geo_district_prefix),
        separator = context.getString(R.string.geo_separator),
        digits = { PersianNumbers.toPersian(it) }
    )
}
