package ir.ilam.inspection.field.ui.thermal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.ui.form.HubSection
import ir.ilam.inspection.field.ui.form.SectionStatus
import ir.ilam.inspection.field.ui.form.crewSection
import ir.ilam.inspection.field.ui.form.preview
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

internal const val PAGE_CREW = "crew"
internal const val PAGE_ROUTE = "route"
internal const val PAGE_FIND = "find"
internal const val PAGE_FILES = "files"
internal const val PAGE_ADDRESS = "address"
internal const val PAGE_EXPORT = "export"

/** The thermal inspection's tiles: the route, finding the files, assigning them, the address, the export. */
@Composable
fun thermalSections(viewModel: ThermalViewModel, recording: Boolean, livePoints: Int): List<HubSection> {
    val item = viewModel.item ?: return emptyList()
    val payload = viewModel.payload
    val originals = viewModel.originals
    val started = payload.startedAt != null
    val unassigned = originals.count { it.assetType == null || it.plate.isNullOrBlank() }
    val withTemps = originals.count { payload.files[it.id]?.temperatures?.isEmpty() == false }
    val points = PersianNumbers.toPersian(if (recording) livePoints else payload.points)
    return listOf(
        crewSection(PAGE_CREW, payload.crew, null, stringResource(R.string.crew_title)),
        HubSection(
            key = PAGE_ROUTE,
            title = stringResource(R.string.thermal_route_title),
            summary = when {
                recording -> stringResource(R.string.thermal_hub_route_live, points)
                started -> stringResource(R.string.thermal_hub_route_done, points)
                else -> stringResource(R.string.thermal_hub_route_idle)
            } + (payload.mvFeeder?.let { " · " + it.name } ?: ""),
            icon = Icons.Filled.GpsFixed,
            tone = if (recording) Tone.SUCCESS else Tone.INFO,
            status = when {
                recording -> SectionStatus.ATTENTION
                started -> SectionStatus.DONE
                else -> SectionStatus.MISSING
            }
        ),
        HubSection(
            key = PAGE_FIND,
            title = stringResource(R.string.thermal_files_title),
            summary = if (started) stringResource(R.string.thermal_files_hint) else stringResource(R.string.thermal_hub_find_locked),
            icon = Icons.Filled.FolderOpen,
            tone = Tone.ACCENT,
            status = when {
                originals.isNotEmpty() -> SectionStatus.DONE
                started -> SectionStatus.MISSING
                else -> SectionStatus.OPTIONAL
            }
        ),
        HubSection(
            key = PAGE_FILES,
            title = stringResource(R.string.thermal_list_title, PersianNumbers.toPersian(originals.size)),
            summary = if (originals.isEmpty()) stringResource(R.string.thermal_list_hint)
            else stringResource(R.string.thermal_hub_files_summary, PersianNumbers.toPersian(originals.size),
                PersianNumbers.toPersian(unassigned), PersianNumbers.toPersian(withTemps)),
            icon = Icons.Filled.Thermostat,
            tone = Tone.DANGER,
            status = when {
                originals.isEmpty() -> SectionStatus.OPTIONAL
                unassigned > 0 -> SectionStatus.MISSING
                else -> SectionStatus.DONE
            }
        ),
        HubSection(
            key = PAGE_ADDRESS,
            title = stringResource(R.string.report_place),
            summary = preview(item.address) ?: stringResource(R.string.hub_empty),
            icon = Icons.Filled.Place,
            tone = Tone.INFO,
            status = if (item.address.isNullOrBlank()) SectionStatus.OPTIONAL else SectionStatus.DONE
        ),
        HubSection(
            key = PAGE_EXPORT,
            title = stringResource(R.string.thermal_export),
            summary = stringResource(R.string.thermal_export_hint),
            icon = Icons.Filled.Share,
            tone = Tone.BRAND,
            status = SectionStatus.OPTIONAL
        )
    )
}
