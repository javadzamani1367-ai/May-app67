package ir.ilam.inspection.data.model

/**
 * A county of Ilam province and the area code used inside tracking codes.
 *
 * Names live in `strings.xml` (arrays `county_names` / `county_codes`); the
 * default codes can be overridden from settings so they can follow the
 * distribution company's official numbering.
 */
data class County(val index: Int, val name: String, val code: String)

/** Settings key under which a customised code for county [index] is stored. */
fun countyCodeSettingKey(index: Int): String = "county_code_$index"
