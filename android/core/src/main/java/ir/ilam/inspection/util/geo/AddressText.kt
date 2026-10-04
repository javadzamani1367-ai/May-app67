package ir.ilam.inspection.util.geo

import kotlin.math.roundToInt

/**
 * The words an address is built from. They come from string resources; this
 * class only puts them together, so the order and the rules can be tested
 * without Android.
 */
data class AddressWords(
    /** "County %1$s". */
    val county: String,
    /** "District %1$s", given only where no settlement is near. */
    val district: String,
    /** "Village %1$s", for a village or hamlet whose name does not already say so. */
    val village: String,
    /** "Near %1$s", for a place the point is near but not in. */
    val nearPlace: String,
    /** "Near %1$s (%2$s m)", for a street the point is not on. */
    val nearStreet: String,
    /** The prefixes that already say "village" in a name. */
    val villagePrefixes: List<String>,
    /** The words OpenStreetMap names already start with: "county", "district". */
    val countyPrefix: String,
    val districtPrefix: String,
    val separator: String,
    val digits: (String) -> String
)

/**
 * "County, village or city, neighbourhood, street": from the general to the
 * particular, the way an address is written in Persian. A part the index does
 * not know is left out, never guessed. The result is a draft for the user to
 * correct, which is why the distances are shown.
 */
object AddressText {

    /** Closer than this, the point is on the street and no distance is given. */
    private const val ON_STREET_METRES = 25.0

    fun compose(match: GeoMatch, words: AddressWords): String {
        val parts = mutableListOf<String>()
        val settlement = match.settlement
        // OpenStreetMap usually names the county "Shahrestan-e Ilam" already;
        // the word is added only where it is missing.
        val county = match.county?.let { bare(it, words.countyPrefix) }
        // "Ilam county, Ilam" says the same thing twice.
        if (county != null && !sameName(county, settlement?.name)) parts += words.county.format(county)
        // Out in the country, the district is the only other thing that narrows it down.
        if (settlement == null) match.district?.let { parts += words.district.format(bare(it, words.districtPrefix)) }
        if (settlement != null) {
            val village = settlement.kind == PlaceKind.VILLAGE || settlement.kind == PlaceKind.HAMLET
            val named = if (village && words.villagePrefixes.none { settlement.name.startsWith(it) })
                words.village.format(settlement.name) else settlement.name
            parts += if (settlement.inside) named else words.nearPlace.format(named)
        }
        match.neighbourhood?.let { parts += it.name }
        val street = match.street
        val metres = match.streetMetres
        if (street != null && metres != null) {
            parts += if (metres < ON_STREET_METRES) street
            else words.nearStreet.format(street, words.digits(roundTo(metres).toString()))
        }
        return parts.distinct().joinToString(words.separator)
    }

    /** Spaces and zero-width joiners are spelled both ways in the data: "دره‌شهر", "دره‌ شهر". */
    private fun sameName(a: String, b: String?): Boolean =
        b != null && a.filterNot { it.isWhitespace() || it == '\u200C' } == b.filterNot { it.isWhitespace() || it == '\u200C' }

    private fun bare(name: String, prefix: String): String =
        if (prefix.isNotEmpty() && name.startsWith(prefix)) name.removePrefix(prefix).trim() else name

    /** Distances to the nearest 5 m: the position itself is no better than that. */
    private fun roundTo(metres: Double): Int = ((metres / 5).roundToInt() * 5).coerceAtLeast(5)
}
