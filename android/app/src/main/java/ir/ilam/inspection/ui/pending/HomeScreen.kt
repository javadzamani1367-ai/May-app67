package ir.ilam.inspection.ui.pending

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.EmptyState

/**
 * The three tabs of the app: pending, visited, archived. The floating button
 * is the second tap of the shortest path there is — report intake.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewReport: () -> Unit,
    onOpenCase: (String) -> Unit,
    onContinueVisit: (String) -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit
) {
    val container = LocalContext.current.container
    val viewModel: CaseListViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(container) { CaseListViewModel(it) } }
    )
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val cases by viewModel.cases.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onStats) {
                        Icon(Icons.Filled.QueryStats, contentDescription = stringResource(R.string.nav_stats))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry.status,
                        onClick = { viewModel.selectTab(entry.status) },
                        icon = { Icon(entry.icon(), contentDescription = null) },
                        label = { Text(stringResource(entry.label)) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewReport) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_report))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::search,
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )
            if (cases.isEmpty()) {
                EmptyState(
                    message = stringResource(
                        when (tab) {
                            ReportStatus.PENDING -> R.string.list_empty_pending
                            ReportStatus.VISITED -> R.string.list_empty_visited
                            ReportStatus.ARCHIVED -> R.string.list_empty_archive
                        }
                    )
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(cases, key = { it.id }) { report ->
                        CaseCard(
                            report = report,
                            daysWaiting = viewModel.daysWaiting(report),
                            onClick = {
                                if (report.status == ReportStatus.PENDING.code) {
                                    onContinueVisit(report.id)
                                } else {
                                    onOpenCase(report.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private enum class HomeTab(val status: ReportStatus, val label: Int) {
    PENDING(ReportStatus.PENDING, R.string.tab_pending),
    VISITED(ReportStatus.VISITED, R.string.tab_visited),
    ARCHIVE(ReportStatus.ARCHIVED, R.string.tab_archive);

    @Composable
    fun icon() = when (this) {
        PENDING -> Icons.Filled.Assignment
        VISITED -> Icons.Filled.CheckCircle
        ARCHIVE -> Icons.Filled.Inventory
    }
}
