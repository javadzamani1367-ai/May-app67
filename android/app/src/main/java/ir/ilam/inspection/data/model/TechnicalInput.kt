package ir.ilam.inspection.data.model

import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.PowerCalc

/**
 * Everything step three collects, exactly as it stands in the form. The step
 * owns all of these together, so it writes them together: keeping the parsing
 * and the power arithmetic here leaves the view model to do nothing but save.
 */
data class TechnicalInput(
    val tapPoint: TapPoint? = null,
    val phaseType: PhaseType? = null,
    val amperage: List<String> = List(3) { "" },
    val voltage: List<String> = List(3) { "" },
    val tariffType: TariffType? = null,
    val meterType: MeterType? = null,
    val sealExternal: Boolean? = null,
    val sealExternalSerial: String = "",
    val sealInternal: Boolean? = null,
    val appearanceOk: Boolean? = null,
    val tampered: Boolean? = null
) {

    fun withAmperage(index: Int, value: String): TechnicalInput =
        copy(amperage = amperage.replacing(index, value))

    fun withVoltage(index: Int, value: String): TechnicalInput =
        copy(voltage = voltage.replacing(index, value))

    private fun List<String>.replacing(index: Int, value: String): List<String> =
        List(3) { if (it == index) value else getOrElse(it) { "" } }

    private fun numbers(values: List<String>): List<Double?> =
        List(3) { index -> PersianNumbers.parseDoubleOrNull(values.getOrNull(index).orEmpty()) }

    /** The reading as the report will state it, recomputed on every edit. */
    fun power() = PowerCalc.of(phaseType, numbers(amperage), numbers(voltage))

    fun applyTo(current: ReportEntity): ReportEntity {
        val amps = numbers(amperage)
        val volts = numbers(voltage)
        val result = PowerCalc.of(phaseType, amps, volts)
        // A single-phase case must not keep S and T readings from a three-phase
        // one the expert corrected: the report would show three phases of load
        // on a supply that has one.
        val used = phaseType?.phases ?: 0
        fun amp(index: Int) = if (index < used) amps.getOrNull(index) else null
        fun volt(index: Int) = if (index < used) volts.getOrNull(index) else null
        return current.copy(
            tapPoint = tapPoint?.code,
            phaseType = phaseType?.code,
            amperageR = amp(0),
            amperageS = amp(1),
            amperageT = amp(2),
            voltageR = volt(0),
            voltageS = volt(1),
            voltageT = volt(2),
            measuredAmperage = result.totalAmperage,
            totalWatt = result.totalWatt,
            tariffType = tariffType?.code,
            meterType = meterType?.code,
            sealExternal = YesNo.code(sealExternal),
            sealExternalSerial = sealExternalSerial.trim().ifBlank { null },
            sealInternal = YesNo.code(sealInternal),
            meterAppearanceOk = YesNo.code(appearanceOk),
            meterTampered = YesNo.code(tampered)
        )
    }

    companion object {
        /** Reads the form back out of a saved case, so editing resumes intact. */
        fun from(report: ReportEntity): TechnicalInput = TechnicalInput(
            tapPoint = TapPoint.of(report.tapPoint),
            phaseType = PhaseType.of(report.phaseType),
            amperage = listOf(report.amperageR, report.amperageS, report.amperageT)
                .map { PersianNumbers.plain(it) },
            voltage = listOf(report.voltageR, report.voltageS, report.voltageT)
                .map { PersianNumbers.plain(it) },
            tariffType = TariffType.of(report.tariffType),
            meterType = MeterType.of(report.meterType),
            sealExternal = YesNo.of(report.sealExternal),
            sealExternalSerial = report.sealExternalSerial.orEmpty(),
            sealInternal = YesNo.of(report.sealInternal),
            appearanceOk = YesNo.of(report.meterAppearanceOk),
            tampered = YesNo.of(report.meterTampered)
        )
    }
}
