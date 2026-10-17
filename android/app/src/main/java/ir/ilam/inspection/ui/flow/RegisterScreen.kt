package ir.ilam.inspection.ui.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.MultilineField
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing

/** Whom the new report goes to straight away: nobody yet, the area executor, or an expert. */
private const val NOBODY = ""
private const val AREA_EXECUTOR = "executor"

/**
 * Registering a report on the server — a report, not a visit: the case is
 * created "in progress" and the expert does the visit on their phone. The
 * manager may hand it on here; if they do not, it goes to the area executor by
 * itself after the deadline in the manager's settings. An executor registers
 * only in their own areas, and the case stays with them.
 */
@Composable
fun RegisterScreen(viewModel: FlowViewModel, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val state by viewModel.intake.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.openIntake() }
    LaunchedEffect(state.created) { state.created?.let { viewModel.intakeShown(); onCreated(it) } }
    val people = state.people
    var type by rememberSaveable { mutableStateOf<Int?>(null) }
    var area by rememberSaveable { mutableStateOf<String?>(null) }
    var address by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
    var subscription by rememberSaveable { mutableStateOf("") }
    var subscriber by rememberSaveable { mutableStateOf("") }
    var owner by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var soragh by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf(NOBODY) }
    val ready = type != null && area != null && address.isNotBlank() && (type != SORAGH || soragh.isNotBlank()) && !state.busy

    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.fl_intake_title), subtitle = stringResource(R.string.fl_intake_subtitle), onBack = onBack)
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
        ) {
            SectionCard(title = stringResource(R.string.fl_intake_report), icon = Icons.Filled.NoteAdd) {
                Column {
                    DropdownField(stringResource(R.string.intake_report_type), people?.reportTypes.orEmpty().map { it.first }, type,
                        optionLabel = { code -> people?.reportTypes?.firstOrNull { it.first == code }?.second.orEmpty() },
                        onSelect = { type = it })
                    DropdownField(stringResource(R.string.fl_area), people?.areas.orEmpty().map { it.code }, area,
                        optionLabel = { code -> people?.areas?.firstOrNull { it.code == code }?.name.orEmpty() },
                        onSelect = { area = it })
                    if (type == SORAGH) {
                        AppTextField(stringResource(R.string.fl_soragh_code), soragh, { soragh = it }, ltr = true)
                    }
                    MultilineField(stringResource(R.string.fl_address), address, { address = it })
                    AppTextField(stringResource(R.string.fl_district), district, { district = it })
                    NumberField(stringResource(R.string.fl_subscription), subscription, { subscription = it })
                    AppTextField(stringResource(R.string.fl_subscriber), subscriber, { subscriber = it })
                    AppTextField(stringResource(R.string.fl_owner), owner, { owner = it })
                    AppTextField(stringResource(R.string.fl_phone), phone, { phone = it }, keyboardType = KeyboardType.Phone, ltr = true)
                    MultilineField(stringResource(R.string.fl_intake_note), note, { note = it })
                }
            }
            SectionCard(title = stringResource(R.string.fl_intake_target)) {
                Column {
                    val targets = buildList {
                        add(NOBODY)
                        if (UserRole.isFullManager) add(AREA_EXECUTOR)
                        people?.experts.orEmpty().filter { area == null || area in it.areas }.forEach { add(it.id) }
                    }
                    DropdownField(stringResource(R.string.fl_intake_target), targets, target, optionLabel = { id ->
                        when (id) {
                            NOBODY -> stringResource(if (UserRole.isFullManager) R.string.fl_target_later else R.string.fl_target_self)
                            AREA_EXECUTOR -> stringResource(R.string.fl_area_executor)
                            else -> stringResource(R.string.fl_expert_is, people?.experts?.firstOrNull { it.id == id }?.name.orEmpty())
                        }
                    }, onSelect = { target = it })
                    if (UserRole.isFullManager && target == NOBODY) {
                        Text(stringResource(R.string.fl_target_later_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        BottomActionBar {
            PrimaryButton(stringResource(R.string.fl_intake_save), enabled = ready, icon = Icons.Filled.Save, modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.register(buildMap {
                        put("report_type", type.toString())
                        put("area_code", area.orEmpty())
                        put("address", address)
                        put("district", district)
                        put("subscription_number", subscription)
                        put("subscriber_name", subscriber)
                        put("owner_name", owner)
                        put("owner_phone", phone)
                        put("tracking_code", soragh)
                        put("note", note)
                        when (target) {
                            NOBODY -> Unit
                            AREA_EXECUTOR -> put("to_executor", "1")
                            else -> put("expert_id", target)
                        }
                    }, context)
                })
        }
    }
}

/** The Soragh system issues its own tracking code; the others are built from the subscription. */
private const val SORAGH = 1
