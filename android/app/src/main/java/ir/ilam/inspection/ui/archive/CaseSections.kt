package ir.ilam.inspection.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.LocationFixEntity
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.data.model.TapPoint
import ir.ilam.inspection.data.model.TariffType
import ir.ilam.inspection.data.model.TechnicalInput
import ir.ilam.inspection.ui.common.InfoTile
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.common.countyWithArea
import ir.ilam.inspection.ui.common.meterTypeLabel
import ir.ilam.inspection.ui.common.phaseTypeLabel
import ir.ilam.inspection.ui.common.reportTypeLabel
import ir.ilam.inspection.ui.common.statusLabel
import ir.ilam.inspection.ui.common.tapPointLabel
import ir.ilam.inspection.ui.common.tariffTypeLabel
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.ui.visit.AttendeeRow
import ir.ilam.inspection.ui.visit.DeviceCard
import ir.ilam.inspection.ui.visit.LocationFacts
import ir.ilam.inspection.ui.visit.MiniMap
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/*
 * The case, laid out as the official form is: the same seven sections in the
 * same order, so what the expert reads on the phone is what the unit will
 * read on paper. Each section carries its number and an icon.
 */

/** Four figures before any section: what was found, and how much evidence backs it. */
@Composable
fun CaseOverview(detail: ReportDetail) {
    val power = TechnicalInput.from(detail.report).power()
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
        InfoTile(
            label = stringResource(R.string.case_devices_tile),
            value = PersianNumbers.toPersian(detail.deviceCount),
            icon = Icons.Filled.Memory,
            tone = if (detail.deviceCount > 0) Tone.DANGER else Tone.NEUTRAL,
            modifier = Modifier.weight(1f)
        )
        InfoTile(
            label = stringResource(R.string.case_measured_tile),
            value = power.totalKilowatt?.let { stringResource(R.string.unit_kilowatt, PersianNumbers.toPersian(it)) }
                ?: stringResource(R.string.value_empty),
            icon = Icons.Filled.Bolt,
            tone = Tone.ACCENT,
            modifier = Modifier.weight(1.3f)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
        InfoTile(
            label = stringResource(R.string.case_device_power_tile),
            value = stringResource(R.string.unit_watt, PersianNumbers.grouped(detail.totalPower)),
            icon = Icons.Filled.ElectricalServices,
            tone = Tone.BRAND,
            modifier = Modifier.weight(1.3f)
        )
        InfoTile(
            label = stringResource(R.string.case_media_tile),
            value = PersianNumbers.toPersian(detail.media.size),
            icon = Icons.Filled.PhotoLibrary,
            tone = Tone.INFO,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CaseFileSection(detail: ReportDetail) {
    val r = detail.report
    SectionCard(title = stringResource(R.string.form_section_1), icon = Icons.Filled.Info) {
        ValueRow(stringResource(R.string.form_report_type), reportTypeLabel(r.reportType))
        ValueRow(stringResource(R.string.form_status), statusLabel(r.status))
        ValueRow(stringResource(R.string.form_report_date), PersianDate.format(r.reportDate))
        ValueRow(stringResource(R.string.form_visit_date), r.visitDate?.let { PersianDate.format(it) })
        ValueRow(stringResource(R.string.case_expert_code), PersianNumbers.toPersian(r.expertCode))
    }
}

@Composable
fun CasePlaceSection(detail: ReportDetail, meta: LocationFixEntity?, onOpenMap: () -> Unit) {
    val r = detail.report
    SectionCard(title = stringResource(R.string.form_section_2), icon = Icons.Filled.Place, tone = Tone.INFO) {
        ValueRow(stringResource(R.string.field_county), countyWithArea(r.county, r.areaCode))
        ValueRow(stringResource(R.string.field_district), r.district)
        ValueRow(stringResource(R.string.field_address), r.address)
        ValueRow(stringResource(R.string.field_postal_code), PersianNumbers.toPersian(r.postalCode))
        ValueRow(stringResource(R.string.field_file_number), PersianNumbers.toPersian(r.fileNumber))
        ValueRow(stringResource(R.string.field_bill_number), PersianNumbers.toPersian(r.billNumber))
        ValueRow(stringResource(R.string.field_subscription_number), PersianNumbers.toPersian(r.subscriptionNumber))
        ValueRow(stringResource(R.string.field_usage_type), r.usageType)
        if (r.latitude != null && r.longitude != null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp).padding(vertical = Spacing.sm)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                MiniMap(r.latitude, r.longitude, r.gpsAccuracy, Tavan.colors.info.strong, onClick = onOpenMap)
            }
            LocationFacts(report = r, meta = meta)
        }
    }
}

@Composable
fun CaseOwnerSection(detail: ReportDetail) {
    val r = detail.report
    SectionCard(title = stringResource(R.string.form_section_3), icon = Icons.Filled.PersonPin) {
        ValueRow(stringResource(R.string.field_owner_name), r.ownerName)
        ValueRow(stringResource(R.string.field_owner_national_id), PersianNumbers.toPersian(r.ownerNationalId))
        ValueRow(stringResource(R.string.field_owner_phone), PersianNumbers.toPersian(r.ownerPhone), ltr = true)
        ValueRow(stringResource(R.string.field_owner_relation), r.ownerRelation)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaseTechnicalSection(detail: ReportDetail) {
    val input = TechnicalInput.from(detail.report)
    val power = input.power()
    SectionCard(title = stringResource(R.string.form_section_4), icon = Icons.Filled.ElectricMeter, tone = Tone.ACCENT) {
        // The findings first, as badges: what a reader of this case most needs to know.
        val findings = buildList {
            if (input.tapPoint == TapPoint.BEFORE_METER) add(tapPointLabel(TapPoint.BEFORE_METER) to Tone.WARNING)
            if (input.tariffType == TariffType.UNMETERED_ILLEGAL) add(tariffTypeLabel(TariffType.UNMETERED_ILLEGAL) to Tone.DANGER)
            if (input.tampered == true) add(stringResource(R.string.dashboard_tampered) to Tone.DANGER)
        }
        if (findings.isNotEmpty()) {
            Text(stringResource(R.string.case_findings), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                findings.forEach { (text, tone) -> StatusBadge(text = text, tone = tone, icon = Icons.Filled.GppMaybe) }
            }
        }
        ValueRow(stringResource(R.string.field_tap_point), input.tapPoint?.let { tapPointLabel(it) })
        ValueRow(stringResource(R.string.field_phase_type), input.phaseType?.let { phaseTypeLabel(it) })
        ValueRow(
            stringResource(R.string.field_measured_amperage),
            power.totalAmperage?.let { stringResource(R.string.unit_ampere, PersianNumbers.toPersian(it)) }
        )
        ValueRow(
            stringResource(R.string.field_power_kilowatt),
            power.totalKilowatt?.let { stringResource(R.string.unit_kilowatt, PersianNumbers.toPersian(it)) },
            emphasize = true
        )
        ValueRow(stringResource(R.string.field_tariff_type), input.tariffType?.let { tariffTypeLabel(it) })
        ValueRow(stringResource(R.string.field_meter_type), input.meterType?.let { meterTypeLabel(it) })
        ValueRow(stringResource(R.string.question_tampered), yesNo(input.tampered))
        ValueRow(stringResource(R.string.question_seal_external), yesNo(input.sealExternal))
        ValueRow(stringResource(R.string.question_seal_internal), yesNo(input.sealInternal))
        ValueRow(stringResource(R.string.question_appearance_ok), yesNo(input.appearanceOk))
    }
}

@Composable
fun CaseDevicesSection(detail: ReportDetail) {
    SectionCard(title = stringResource(R.string.form_section_5), icon = Icons.Filled.Memory, tone = Tone.DANGER) {
        if (detail.devices.isEmpty()) Muted(stringResource(R.string.devices_empty))
        detail.devices.forEach { DeviceCard(it) }
    }
    SectionCard(title = stringResource(R.string.form_section_6), icon = Icons.Filled.Groups, tone = Tone.INFO) {
        if (detail.attendees.isEmpty()) Muted(stringResource(R.string.attendees_empty))
        detail.attendees.forEach { AttendeeRow(it) }
    }
}

@Composable
fun CaseNarrativeSection(detail: ReportDetail) {
    val r = detail.report
    SectionCard(title = stringResource(R.string.form_section_7), icon = Icons.Filled.Description) {
        Text(stringResource(R.string.field_description), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(r.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.value_empty), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.field_actions_taken),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md)
        )
        Text(r.actionsTaken?.takeIf { it.isNotBlank() } ?: stringResource(R.string.value_empty), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun yesNo(value: Boolean?): String? = value?.let {
    stringResource(if (it) R.string.answer_yes else R.string.answer_no)
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
