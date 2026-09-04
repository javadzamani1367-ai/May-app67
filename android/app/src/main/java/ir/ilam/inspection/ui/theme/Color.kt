package ir.ilam.inspection.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A high contrast light palette: the app is used outdoors, in daylight, often
 * through gloves and sunglasses.
 */
val Teal700 = Color(0xFF00695C)
val Teal900 = Color(0xFF004D40)
val Teal100 = Color(0xFFB2DFDB)
val Amber700 = Color(0xFFB26A00)
val Amber100 = Color(0xFFFFE7B3)
val Red700 = Color(0xFFC62828)
val Red100 = Color(0xFFFFCDD2)
val Grey900 = Color(0xFF1A1C1E)
val Grey700 = Color(0xFF44474A)
val Grey200 = Color(0xFFDDE1E4)
val Grey50 = Color(0xFFF7F8FA)
val Surface = Color(0xFFFFFFFF)

/** One fixed colour per report type, used on cards and in the report header. */
val ReportTypeColors = listOf(
    Color(0xFF37474F), // Soragh system
    Color(0xFF1565C0), // 121 system
    Color(0xFF2E7D32), // public report
    Color(0xFF6A1B9A), // colleague report
    Color(0xFFEF6C00), // Tavanir report
    Color(0xFF00838F)  // field visit
)

fun colorForReportType(code: Int): Color =
    ReportTypeColors.getOrElse(code - 1) { Grey700 }
