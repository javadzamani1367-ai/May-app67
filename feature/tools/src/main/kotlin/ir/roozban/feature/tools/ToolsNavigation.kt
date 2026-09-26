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
data class NoteRoute(val id: String? = null)

fun NavGraphBuilder.toolsScreens(
    onOpenNotes: () -> Unit,
    onOpenNote: (String?) -> Unit,
    onOpenSpeechModels: () -> Unit,
    onOpenVoices: () -> Unit,
    onBack: () -> Unit,
) {
    composable<ToolsRoute> { ToolsScreen(onOpenNotes = onOpenNotes, onBack = onBack) }
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
