package ir.ilam.inspection.ui.pending

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode

/**
 * The home of the app: a dashboard, then the three queues — pending, visited,
 * archived — behind a bottom bar that says how many are waiting. The floating
 * button is the second tap of the shortest path there is: a new visit.
 */
@Composable
fun HomeScreen(
    onNewReport: () -> Unit,
    onOpenCase: (String) -> Unit,
    onContinueVisit: (String) -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    onApprovals: () -> Unit,
    onMap: () -> Unit
) {
    val container = LocalContext.current.container
    val lists: CaseListViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(container) { CaseListViewModel(it) } }
    )
    val dashboard: DashboardViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(container) { DashboardViewModel(it) } }
    )
    val summary by dashboard.state.collectAsStateWithLifecycle()
    val cases by lists.cases.collectAsStateWithLifecycle()
    val query by lists.query.collectAsStateWithLifecycle()
    val deviceCounts by lists.deviceCounts.collectAsStateWithLifecycle()
    val deleteMessage by lists.deleteMessage.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(HomeTab.DASHBOARD) }
    var pendingDelete by remember { mutableStateOf<ReportEntity?>(null) }

    fun show(status: ReportStatus) {
        tab = HomeTab.of(status)
        lists.selectTab(status)
    }

    fun open(report: ReportEntity) {
        if (report.status == ReportStatus.PENDING.code) onContinueVisit(report.id) else onOpenCase(report.id)
    }

    val today = PersianDate.format(System.currentTimeMillis())
    val code = summary.settings.expertCode
    Scaffold(
        topBar = {
            TavanTopBar(
                title = stringResource(R.string.brand_name),
                subtitle = when {
                    UserRole.isManager -> stringResource(R.string.home_subtitle_manager, today)
                    code.isNotBlank() -> stringResource(R.string.home_subtitle_expert, PersianNumbers.toPersian(code), today)
                    else -> today
                },
                showBrand = true
            ) {
                if (UserRole.isManager) {
                    HeaderAction(
                        icon = Icons.Filled.FactCheck,
                        contentDescription = stringResource(R.string.approval_pending_title),
                        onClick = onApprovals,
                        badge = summary.counts.awaitingApproval.toInt()
                    )
                }
                HeaderAction(Icons.Filled.Map, stringResource(R.string.cases_map_title), onMap)
                HeaderAction(Icons.Filled.QueryStats, stringResource(R.string.nav_stats), onStats)
                HeaderAction(Icons.Filled.Settings, stringResource(R.string.nav_settings), onSettings)
            }
        },
        bottomBar = {
            HomeNavigationBar(
                selected = tab,
                pending = summary.counts.pending.toInt(),
                overdue = summary.counts.overdue.toInt(),
                onSelect = { selected ->
                    tab = selected
                    selected.status?.let(lists::selectTab)
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewReport,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_visit), style = MaterialTheme.typography.labelLarge) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (tab == HomeTab.DASHBOARD) {
                DashboardTab(
                    state = summary,
                    daysWaiting = dashboard::daysWaiting,
                    onOpen = ::open,
                    onShowTab = ::show,
                    onNewVisit = onNewReport,
                    onSettings = onSettings,
                    onApprovals = onApprovals
                )
            } else {
                CaseListTab(
                    status = tab.status ?: ReportStatus.PENDING,
                    cases = cases,
                    query = query,
                    deviceCounts = deviceCounts,
                    onQuery = lists::search,
                    daysWaiting = lists::daysWaiting,
                    onOpen = ::open,
                    onDeleteRequest = { pendingDelete = it }
                )
            }
        }
    }

    deleteMessage?.let { message ->
        AlertDialog(
            onDismissRequest = lists::clearDeleteMessage,
            title = { Text(stringResource(R.string.case_delete)) },
            text = { Text(stringResource(message)) },
            confirmButton = {
                TextButton(onClick = lists::clearDeleteMessage) { Text(stringResource(R.string.action_confirm)) }
            }
        )
    }

    pendingDelete?.let { report ->
        val deletable = lists.canDelete(report)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.case_delete_confirm_title)) },
            text = {
                Text(
                    if (deletable) {
                        stringResource(R.string.case_delete_confirm_message, TrackingCode.forDisplay(report.displayCode))
                    } else {
                        stringResource(R.string.case_delete_blocked)
                    }
                )
            },
            confirmButton = {
                if (deletable) {
                    TextButton(
                        onClick = {
                            lists.delete(report)
                            pendingDelete = null
                        },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(stringResource(R.string.action_delete)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
