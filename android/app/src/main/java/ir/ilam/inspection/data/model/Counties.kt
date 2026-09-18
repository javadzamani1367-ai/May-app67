package ir.ilam.inspection.data.model

/**
 * A county or district of Ilam province and the area code that goes into a
 * tracking code. Both come from [ir.ilam.inspection.data.CountyCatalog] and
 * are fixed by the distribution company's numbering.
 */
data class County(val index: Int, val name: String, val code: String)
