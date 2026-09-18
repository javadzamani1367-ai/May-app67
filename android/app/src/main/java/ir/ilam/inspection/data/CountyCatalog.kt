package ir.ilam.inspection.data

import android.content.Context
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.County

/**
 * The counties and districts of the distribution company, with the area code
 * each one puts into a tracking code. The list is fixed: these are the
 * company's official numbers, not a preference, so nothing in the app can
 * edit them and two experts can never produce codes that disagree.
 *
 * Names live in resources — no Persian text in code — in `county_names`, with
 * `county_codes` giving the code at the same position.
 */
class CountyCatalog(context: Context) {

    private val names: List<String> =
        context.resources.getStringArray(R.array.county_names).toList()

    private val codes: List<String> =
        context.resources.getStringArray(R.array.county_codes).toList()

    val defaults: List<County> = names.mapIndexed { index, name ->
        County(index, name, codes.getOrElse(index) { FALLBACK_CODE })
    }

    fun indexOfName(name: String?): Int? =
        name?.let { needle -> defaults.firstOrNull { it.name == needle }?.index }

    fun nameOf(index: Int?): String? = index?.let { defaults.getOrNull(it)?.name }

    fun codeOf(index: Int?): String =
        index?.let { defaults.getOrNull(it)?.code } ?: FALLBACK_CODE

    companion object {
        const val FALLBACK_CODE = "401"
    }
}
