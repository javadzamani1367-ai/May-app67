package ir.ilam.inspection.util

/**
 * How a county and its area are written together: «ایلام — ناحیه ۴۰۱».
 *
 * One rule, used by the screens and by the exporters alike. They resolve the
 * wording from resources in different ways — Compose through `stringResource`,
 * the exporters through a `Context` — so the rule takes the formatter rather
 * than the string, and cannot drift between a form on screen and the same form
 * as a PDF.
 *
 * Ilam is two areas under one name, so a report naming only the county does
 * not say whose area the case belongs to.
 */
object CountyLabel {

    /**
     * Null when there is no county to name. A case filed before the area was
     * recorded has no area code, and is written as the plain county name
     * rather than with an empty area beside it.
     *
     * `inline` on purpose: Compose resolves its wording with `stringResource`,
     * which is `@Composable` and so may only be called from a lambda the
     * compiler inlines. A plain function parameter is rejected outright — do
     * not make [format] `crossinline`, and do not store it.
     */
    inline fun of(county: String?, areaCode: String?, format: (String, String) -> String): String? {
        val name = county?.trim().orEmpty()
        if (name.isEmpty()) return null

        val area = PersianNumbers.toLatin(areaCode).filter { it.isDigit() }
        return if (area.isEmpty()) name else format(name, PersianNumbers.toPersian(area))
    }
}
