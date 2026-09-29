package ir.roozban.feature.tools

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data object ToolsRoute

@Serializable
data object NotesRoute

@Serializable
data object AttendanceRoute

@Serializable
data class DateToolsRoute(val span: Boolean = false)

@Serializable
data class NoteRoute(val id: String? = null)

fun NavGraphBuilder.toolsScreens(
    onOpenNotes: () -> Unit,
    onOpenNote: (String?) -> Unit,
    onOpenSpeechModels: () -> Unit,
    onOpenVoices: () -> Unit,
    onBack: () -> Unit,
    /** Opens another screen of this module (its routes are declared here). */
    onNavigate: (Any) -> Unit,
) {
    composable<ToolsRoute> { ToolsScreen(onOpenNotes = onOpenNotes, onOpenDates = { onNavigate(DateToolsRoute(it)) }, onOpen = onNavigate, onBack = onBack) }
    composable<AttendanceRoute> { AttendanceScreen(onBack = onBack) }
    composable<DateToolsRoute> { entry -> DateToolsScreen(startOnSpan = entry.toRoute<DateToolsRoute>().span, onBack = onBack) }
    composable<NotesRoute> { NotesScreen(onOpenNote = onOpenNote, onBack = onBack) }
    composable<NoteRoute> { entry ->
        NoteEditorScreen(
            noteId = entry.toRoute<NoteRoute>().id,
            onOpenSpeechModels = onOpenSpeechModels,
            onOpenVoices = onOpenVoices,
            onBack = onBack,
        )
    }
}
