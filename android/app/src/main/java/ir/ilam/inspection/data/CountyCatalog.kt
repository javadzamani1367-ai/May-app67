package ir.ilam.inspection.data

import android.content.Context
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.County

/**
 * County names and their area codes. Names come from resources — no Persian
 * text lives in code — and codes can be overridden from settings so they match
 * the distribution company's official numbering.
 */
class CountyCatalog(context: Context) {

    private val names: List<String> =
        context.resources.getStringArray(R.array.county_names).toList()

    private val defaultCodes: List<String> =
        context.resources.getStringArray(R.array.county_codes).toList()

    val defaults: List<County> = names.mapIndexed { index, name ->
        County(index, name, defaultCodes.getOrElse(index) { "%02d".format(index + 1) })
    }

    fun withOverrides(overrides: Map<Int, String>): List<County> =
        defaults.map { county -> overrides[county.index]?.let { county.copy(code = it) } ?: county }

    fun indexOfName(name: String?): Int? =
        name?.let { needle -> defaults.firstOrNull { it.name == needle }?.index }

    fun nameOf(index: Int?): String? = index?.let { defaults.getOrNull(it)?.name }

    fun defaultCodeOf(index: Int?): String =
        index?.let { defaults.getOrNull(it)?.code } ?: "01"
}
