package ir.minerfinder.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.minerfinder.AppGraph
import ir.minerfinder.BuildConfig
import ir.minerfinder.R
import ir.minerfinder.core.geo.BearingLine
import ir.minerfinder.core.geo.LatLon
import ir.minerfinder.core.geo.Triangulation
import ir.minerfinder.data.SessionSummary
import ir.minerfinder.export.SessionExport
import ir.minerfinder.export.Share
import ir.minerfinder.graph
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.common.Field
import ir.minerfinder.ui.common.SecondaryButton
import ir.minerfinder.ui.common.Section
import ir.minerfinder.ui.map.sessionTitle
import ir.minerfinder.ui.search.CenteredNote
import ir.minerfinder.ui.theme.Palette
import kotlinx.coroutines.launch

/** Past searches: open on the map, export, delete. [onShowMap] switches to the map tab. */
@Composable
fun SessionsScreen(onShowMap: () -> Unit) {
    val graph = LocalContext.current.graph
    val sessions by graph.database.sessions().summaries().collectAsState(initial = emptyList())
    val recording by graph.recorder.current.collectAsState()
    var deleting by remember { mutableStateOf<SessionSummary?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val chooser = stringResource(R.string.share_title)

    if (sessions.isEmpty()) {
        CenteredNote(stringResource(R.string.sessions_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(sessions, key = { it.id }) { s ->
            Section(sessionTitle(s)) {
                if (s.id == recording) Text(stringResource(R.string.session_recording), color = Palette.Ok)
                Field(stringResource(R.string.map_points), Fa.int(s.points))
                Field(stringResource(R.string.map_bearings), Fa.int(s.bearings))
                Field(stringResource(R.string.session_max), Fa.num(s.maxProbability ?: 0f) + stringResource(R.string.unit_percent))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondaryButton(stringResource(R.string.tab_map), onClick = {
                        graph.mapSession.value = s.id
                        onShowMap()
                    })
                    SecondaryButton("CSV", onClick = { scope.launch { export(graph, s.id, csv = true, context, chooser) } })
                    SecondaryButton("JSON", onClick = { scope.launch { export(graph, s.id, csv = false, context, chooser) } })
                }
                if (s.id != recording) {
                    TextButton(onClick = { deleting = s }) { Text(stringResource(R.string.delete), color = Palette.Hot) }
                }
            }
        }
        item { Column(Modifier.padding(8.dp)) {} }
    }

    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.session_delete_title)) },
            text = { Text(sessionTitle(s)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { graph.database.sessions().delete(s.id) }
                }) { Text(stringResource(R.string.delete), color = Palette.Hot) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private suspend fun export(graph: AppGraph, id: Long, csv: Boolean, context: android.content.Context, chooser: String) {
    val dao = graph.database.sessions()
    val session = dao.get(id) ?: return
    val points = dao.pointsOnce(id)
    val bearings = dao.bearingsOnce(id)
    val name = "minerfinder-session-$id"
    if (csv) {
        Share.text(context, "$name.csv", "text/csv", SessionExport.csv(points, bearings), chooser)
    } else {
        val estimate = Triangulation.intersect(bearings.map { BearingLine(LatLon(it.lat, it.lon), it.bearingDeg, 0.3 + it.sharpness) })
        Share.text(context, "$name.json", "application/json", SessionExport.json(session, points, bearings, estimate, BuildConfig.VERSION_NAME), chooser)
    }
}
