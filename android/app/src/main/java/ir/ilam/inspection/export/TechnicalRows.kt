package ir.ilam.inspection.export

import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.PhaseType
import ir.ilam.inspection.data.model.TechnicalInput
import ir.ilam.inspection.util.PersianNumbers

private val PHASE_NAMES = listOf("R", "S", "T")

/**
 * Section four of the report form, built once for both exporters so the PDF
 * and the Word file can never disagree about the same case. Rows with nothing
 * in them are dropped: a formal report should not carry empty lines, and an
 * unanswered question must not read as an answered one.
 */
fun technicalReportRows(
    report: ReportEntity,
    labels: ReportLabels
): List<Pair<String, String?>> {
    val input = TechnicalInput.from(report)
    val power = input.power()
    val rows = mutableListOf<Pair<String, String?>>()

    rows += labels.text(R.string.field_tap_point) to labels.tapPoint(report.tapPoint)
    rows += labels.text(R.string.field_phase_type) to labels.phaseType(report.phaseType)

    val single = input.phaseType == PhaseType.SINGLE
    power.phases.forEachIndexed { index, phase ->
        val name = PHASE_NAMES.getOrElse(index) { "" }
        rows += if (single) {
            labels.text(R.string.field_amperage) to PersianNumbers.toPersian(phase.amperage)
        } else {
            labels.text(R.string.field_amperage_phase, name) to
                PersianNumbers.toPersian(phase.amperage)
        }
        rows += if (single) {
            labels.text(R.string.field_voltage) to PersianNumbers.toPersian(phase.voltage)
        } else {
            labels.text(R.string.field_voltage_phase, name) to
                PersianNumbers.toPersian(phase.voltage)
        }
        if (!single) {
            rows += labels.text(R.string.field_power_phase, name) to phase.watt?.let {
                labels.text(R.string.unit_watt, PersianNumbers.toPersian(it))
            }
        }
    }

    rows += labels.text(R.string.field_measured_amperage) to power.totalAmperage?.let {
        labels.text(R.string.unit_ampere, PersianNumbers.toPersian(it))
    }
    rows += labels.text(R.string.field_power_total) to power.totalWatt?.let {
        labels.text(R.string.unit_watt, PersianNumbers.toPersian(it))
    }
    rows += labels.text(R.string.field_power_kilowatt) to power.totalKilowatt?.let {
        labels.text(R.string.unit_kilowatt, PersianNumbers.toPersian(it))
    }

    rows += labels.text(R.string.field_tariff_type) to labels.tariffType(report.tariffType)
    rows += labels.text(R.string.field_meter_type) to labels.meterType(report.meterType)
    rows += labels.text(R.string.question_seal_external) to labels.yesNo(report.sealExternal)
    rows += labels.text(R.string.field_seal_external_serial) to
        PersianNumbers.toPersian(report.sealExternalSerial)
    rows += labels.text(R.string.question_seal_internal) to labels.yesNo(report.sealInternal)
    rows += labels.text(R.string.question_appearance_ok) to
        labels.yesNo(report.meterAppearanceOk)
    rows += labels.text(R.string.question_tampered) to labels.yesNo(report.meterTampered)

    return rows.filter { !it.second.isNullOrBlank() }
}
