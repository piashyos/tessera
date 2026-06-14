package app.tessera.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.tessera.ui.editor.NoteEditorScreen
import app.tessera.ui.notes.NotesListScreen
import app.tessera.ui.notes.NotesListViewModel
import kotlinx.coroutines.launch

private const val NotesListRoute = "notesList"
private const val EditorRoute = "editor"
private const val NoteIdArg = "noteId"

@Composable
fun NotesNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = NotesListRoute,
        modifier = modifier,
    ) {
        notesListDestination(
            onOpenEditor = { noteId ->
                navController.navigate("$EditorRoute/$noteId")
            },
        )
        composable(
            route = "$EditorRoute/{$NoteIdArg}",
            arguments = listOf(navArgument(NoteIdArg) { type = NavType.StringType }),
        ) { backStackEntry ->
            val noteId = remember(backStackEntry) { backStackEntry.arguments?.getString(NoteIdArg).orEmpty() }
            NoteEditorScreen(
                noteId = noteId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun NavGraphBuilder.notesListDestination(onOpenEditor: (String) -> Unit) {
    composable(route = NotesListRoute) {
        val viewModel: NotesListViewModel = hiltViewModel()
        val state by viewModel.state.collectAsState()
        val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
        val scope = rememberCoroutineScope()

        NotesListScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onQueryChange = viewModel::onQueryChange,
            onTagToggle = viewModel::onTagToggle,
            onCreateNote = {
                scope.launch {
                    onOpenEditor(viewModel.createNoteDraft().value)
                }
            },
            onNoteClick = { noteId ->
                onOpenEditor(noteId.value)
            },
            onTogglePin = viewModel::togglePin,
            onDeleteNote = { note -> viewModel.deleteNote(note.id) },
            onUndoDelete = viewModel::restoreNote,
        )
    }
}
