package ir.ilam.inspection.util

import ir.ilam.inspection.data.model.PhaseType

/** One phase's reading and the power it carries. */
data class PhasePower(val amperage: Double?, val voltage: Double?, val watt: Double?)

/**
 * The measured load, per phase and in total.
 *
 * The expert measures each phase against neutral with a clamp meter, so the
 * power of a phase is simply its own volts times its own amps and the total is
 * the sum of the three. That is deliberately not the √3 line-voltage formula:
 * that one assumes a balanced load measured line to line, and a stolen supply
 * feeding miners is rarely balanced — using it would overstate the load.
 * No power factor is applied, because none is measured; the figure is apparent
 * power, which is what a seizure report should state.
 */
object PowerCalc {

    fun of(
        phaseType: PhaseType?,
        amperage: List<Double?>,
        voltage: List<Double?>
    ): PowerResult {
        val count = phaseType?.phases ?: 1
        val phases = (0 until count).map { index ->
            val amps = amperage.getOrNull(index)
            val volts = voltage.getOrNull(index)
            PhasePower(
                amperage = amps,
                voltage = volts,
                watt = if (amps != null && volts != null) amps * volts else null
            )
        }
        val measured = phases.mapNotNull { it.watt }
        return PowerResult(
            phases = phases,
            totalAmperage = phases.mapNotNull { it.amperage }.takeIf { it.isNotEmpty() }?.sum(),
            totalWatt = measured.takeIf { it.isNotEmpty() }?.sum()
        )
    }
}

data class PowerResult(
    val phases: List<PhasePower>,
    val totalAmperage: Double?,
    val totalWatt: Double?
) {
    val totalKilowatt: Double? get() = totalWatt?.let { it / 1000.0 }

    /** True once there is at least one complete phase reading to report. */
    val hasReading: Boolean get() = totalWatt != null
}
