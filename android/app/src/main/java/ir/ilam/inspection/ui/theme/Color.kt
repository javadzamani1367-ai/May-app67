package ir.ilam.inspection.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * The TavanKav palette.
 *
 * Six families, each with one job, so colour carries meaning instead of
 * decoration:
 *
 *   Navy   — the brand: trust, authority, the frame everything sits in
 *   Teal   — energy and technology: the accent, progress, the active state
 *   Amber  — needs attention: waiting too long, incomplete, a warning
 *   Red    — a violation or a critical state
 *   Blue   — information and position
 *   Green  — approved, complete, normal
 *
 * Only these raw tones live here. Screens never use them directly; they go
 * through the colour scheme and [TavanColors], which pick the right tone for
 * light or dark.
 */

val Navy950 = Color(0xFF06122A)
val Navy900 = Color(0xFF0B1F3A)
val Navy800 = Color(0xFF12305A)
val Navy700 = Color(0xFF1B3F73)
val Navy600 = Color(0xFF25518F)
val Navy300 = Color(0xFF8FB0E0)
val Navy200 = Color(0xFFB9CDEB)
val Navy100 = Color(0xFFDCE6F5)
val Navy50 = Color(0xFFEEF3FA)

val Teal900 = Color(0xFF0B4A45)
val Teal800 = Color(0xFF0B5D57)
val Teal700 = Color(0xFF0F766E)
val Teal600 = Color(0xFF0D9488)
val Teal400 = Color(0xFF2DD4BF)
val Teal300 = Color(0xFF5EEAD4)
val Teal100 = Color(0xFFCCFBF1)

val Amber900 = Color(0xFF6B3108)
val Amber700 = Color(0xFFB45309)
val Amber600 = Color(0xFFD97706)
val Amber400 = Color(0xFFFBBF24)
val Amber100 = Color(0xFFFEF3C7)

val Red900 = Color(0xFF6E1717)
val Red700 = Color(0xFFB91C1C)
val Red600 = Color(0xFFDC2626)
val Red400 = Color(0xFFF87171)
val Red100 = Color(0xFFFEE2E2)

val Blue900 = Color(0xFF173172)
val Blue700 = Color(0xFF1D4ED8)
val Blue600 = Color(0xFF2563EB)
val Blue400 = Color(0xFF60A5FA)
val Blue100 = Color(0xFFDBEAFE)

val Green900 = Color(0xFF124A27)
val Green700 = Color(0xFF15803D)
val Green600 = Color(0xFF16A34A)
val Green400 = Color(0xFF4ADE80)
val Green100 = Color(0xFFDCFCE7)

val Slate950 = Color(0xFF0A1322)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val White = Color(0xFFFFFFFF)

/**
 * One fixed colour per report type, used on cards, badges and in the report
 * header. Chosen to stay apart from the six semantic families, so a type tag
 * is never mistaken for a warning or an approval.
 */
private val ReportTypeLight = listOf(
    Color(0xFF334155), // Soragh system — slate
    Color(0xFF1D4ED8), // 121 system — blue
    Color(0xFF7C3AED), // public report — violet
    Color(0xFF0E7490), // colleague report — cyan
    Color(0xFFC2410C), // Tavanir report — burnt orange
    Color(0xFF0F766E)  // field visit — teal
)

private val ReportTypeDark = listOf(
    Color(0xFF94A3B8),
    Color(0xFF7FA6FF),
    Color(0xFFB69CFF),
    Color(0xFF5CC8DE),
    Color(0xFFFF9A6B),
    Color(0xFF4FD1C5)
)

fun colorForReportType(code: Int, dark: Boolean = false): Color =
    (if (dark) ReportTypeDark else ReportTypeLight).getOrElse(code - 1) { Slate500 }
