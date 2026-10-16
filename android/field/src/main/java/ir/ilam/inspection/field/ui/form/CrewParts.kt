package ir.ilam.inspection.field.ui.form

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.Crew
import ir.ilam.inspection.field.data.Pick
import ir.ilam.inspection.field.data.Roster
import ir.ilam.inspection.field.data.RosterEntry
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.theme.Tone

/**
 * Who is on the crew, before the work starts: the lead and the lineman are
 * required, the driver and the vehicle are not. Names come from the
 * manager's list, so one person is always one record in the reports; until
 * the list has reached the phone, they are typed.
 */
@Composable
fun CrewSection(crew: Crew, roster: Roster, onChange: (Crew) -> Unit) {
    SectionCard(
        title = stringResource(R.string.crew_title),
        subtitle = stringResource(if (roster.crew.isEmpty()) R.string.crew_hint_typed else R.string.crew_hint),
        icon = Icons.Filled.Groups,
        tone = Tone.BRAND
    ) {
        Column {
            PersonPicker(stringResource(R.string.crew_lead), crew.lead, roster.crewFor(Roster.ROLE_LEAD), required = true) {
                onChange(crew.copy(lead = it))
            }
            PersonPicker(stringResource(R.string.crew_lineman), crew.lineman, roster.crewFor(Roster.ROLE_LINEMAN), required = true) {
                onChange(crew.copy(lineman = it))
            }
            PersonPicker(stringResource(R.string.crew_driver), crew.driver, roster.crewFor(Roster.ROLE_DRIVER), required = false) {
                onChange(crew.copy(driver = it))
            }
            AppTextField(stringResource(R.string.crew_vehicle), crew.vehicle, { onChange(crew.copy(vehicle = it)) })
        }
    }
}

/** The crew tile of a hub: required until the lead and the lineman are chosen. */
@Composable
fun crewSection(key: String, crew: Crew, mvFeeder: Pick?): HubSection = HubSection(
    key = key,
    title = stringResource(R.string.crew_and_feeder_title),
    summary = if (crew.complete) stringResource(R.string.crew_summary, crew.lead!!.name, crew.lineman!!.name) +
        (mvFeeder?.let { " · " + it.name } ?: "")
    else stringResource(R.string.crew_missing),
    icon = Icons.Filled.Groups,
    tone = Tone.BRAND,
    status = if (crew.complete) SectionStatus.DONE else SectionStatus.MISSING
)

/** A name from the list, or typed when there is no list yet. */
@Composable
private fun PersonPicker(label: String, selected: Pick?, options: List<RosterEntry>, required: Boolean, onPick: (Pick?) -> Unit) {
    val error = if (required && selected == null) stringResource(R.string.crew_required) else null
    if (options.isEmpty()) {
        AppTextField(label, selected?.name.orEmpty(), { text -> onPick(text.takeIf { it.isNotBlank() }?.let { Pick(null, it) }) },
            error = error)
        return
    }
    DropdownField(
        label = label,
        options = options,
        selected = options.firstOrNull { it.id == selected?.id } ?: selected?.let { RosterEntry(it.id.orEmpty(), it.name, -1, null) },
        optionLabel = { entry -> entry.county?.let { stringResource(R.string.crew_with_county, entry.name, it) } ?: entry.name },
        onSelect = { onPick(Pick(it.id, it.name)) },
        error = error
    )
    if (!required && selected != null) {
        TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.crew_clear)) }
    }
}

/**
 * The medium-voltage feeder, optional: chosen from the manager's list, or
 * typed under «سایر» for one the list does not have yet.
 */
@Composable
fun MvFeederSection(selected: Pick?, roster: Roster, onChange: (Pick?) -> Unit) {
    var typing by rememberSaveable { mutableStateOf(selected != null && selected.id == null && roster.feeders.isNotEmpty()) }
    SectionCard(
        title = stringResource(R.string.mv_feeder_title),
        subtitle = stringResource(R.string.mv_feeder_hint),
        icon = Icons.Filled.SettingsInputComponent,
        tone = Tone.INFO
    ) {
        Column {
            if (roster.feeders.isNotEmpty()) {
                val other = RosterEntry("", stringResource(R.string.mv_feeder_other), -1, null)
                DropdownField(
                    label = stringResource(R.string.mv_feeder_label),
                    options = roster.feeders + other,
                    selected = if (typing) other else roster.feeders.firstOrNull { it.id == selected?.id },
                    optionLabel = { entry -> entry.county?.let { stringResource(R.string.crew_with_county, entry.name, it) } ?: entry.name },
                    onSelect = { entry ->
                        typing = entry.id.isEmpty()
                        onChange(if (typing) null else Pick(entry.id, entry.name))
                    }
                )
            }
            if (typing || roster.feeders.isEmpty()) {
                AppTextField(stringResource(R.string.mv_feeder_name), if (selected?.id == null) selected?.name.orEmpty() else "",
                    { text -> onChange(text.takeIf { it.isNotBlank() }?.let { Pick(null, it) }) })
            }
            if (selected != null) {
                TextButton(onClick = { typing = false; onChange(null) }) { Text(stringResource(R.string.crew_clear)) }
            }
            Text(stringResource(R.string.mv_feeder_optional), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
