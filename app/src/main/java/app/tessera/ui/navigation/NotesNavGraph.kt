package app.tessera.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import app.tessera.ui.editor.NoteEditorViewModel
import app.tessera.ui.notes.NotesListScreen
import app.tessera.ui.notes.NotesListViewModel
import app.tessera.ui.settings.SettingsScreen
import app.tessera.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private const val NotesListRoute = "notesList"
private const val EditorRoute = "editor"
private const val SettingsRoute = "settings"
private const val NoteIdArg = "noteId"
private const val FromDraftArg = "fromDraft"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NotesNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = NotesListRoute,
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
            ) {
                notesListDestination(
                    onOpenEditor = { noteId, fromDraft ->
                        navController.navigate("$EditorRoute/$noteId?$FromDraftArg=$fromDraft")
                    },
                    onOpenSettings = {
                        navController.navigate(SettingsRoute)
                    },
                )
                settingsDestination(navController)
                editorDestination(navController)
            }
        }
    }
}

private fun NavGraphBuilder.notesListDestination(
    onOpenEditor: (String, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    composable(
        route = NotesListRoute,
        // Returning from editor/settings: the list slides back in from the left
        // (parallax) while the popped screen slides out to the right.
        popEnterTransition = {
            slideInHorizontally(animationSpec = tween(280), initialOffsetX = { -it / 4 }) +
                fadeIn(tween(280))
        },
    ) {
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
            val viewModel: NotesListViewModel = hiltViewModel()
            val state by viewModel.state.collectAsState()
            val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
            val scope = rememberCoroutineScope()

            androidx.compose.runtime.LaunchedEffect(viewModel, snackbarHostState) {
                viewModel.events.collect { event ->
                    when (event) {
                        is app.tessera.ui.notes.NotesListEvent.ShowSnackbar ->
                            snackbarHostState.showSnackbar(event.message)
                    }
                }
            }

            NotesListScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                onQueryChange = viewModel::onQueryChange,
                onTagToggle = viewModel::onTagToggle,
                onToggleViewMode = viewModel::onToggleViewMode,
                onCreateNote = {
                    scope.launch {
                        onOpenEditor(viewModel.createNoteDraft().value, true)
                    }
                },
                onNoteClick = { noteId ->
                    onOpenEditor(noteId.value, false)
                },
                onTogglePin = viewModel::togglePin,
                onDeleteNote = { note -> viewModel.deleteNote(note.id) },
                onPermanentlyDelete = { note -> viewModel.permanentlyDeleteNote(note.id) },
                onRestoreNote = viewModel::restoreNote,
                onUndoDelete = viewModel::restoreNote,
                onOpenSettings = onOpenSettings,
                onSelectFolder = viewModel::selectFolder,
                onCreateFolder = viewModel::createFolder,
                onRenameFolder = viewModel::renameFolder,
                onDeleteFolder = viewModel::deleteFolder,
                onMoveNoteToFolder = viewModel::moveNoteToFolder,
                onEnterSelectionMode = viewModel::enterSelectionMode,
                onExitSelectionMode = viewModel::exitSelectionMode,
                onToggleSelection = viewModel::onSelectionToggle,
                onToggleSelectAll = viewModel::toggleSelectAll,
                onDeleteSelected = viewModel::deleteSelected,
                onPermanentlyDeleteSelected = viewModel::permanentlyDeleteSelected,
                onMoveSelectedToFolder = viewModel::moveSelectedToFolder,
                onRestoreNotes = viewModel::restoreNotes,
                onExpandSearch = viewModel::expandSearch,
                onCollapseSearch = viewModel::collapseSearch,
                onSetDatePeriod = viewModel::setDatePeriod,
                onSetTagFilter = viewModel::setSingleTagFilter,
                onAddRecentSearch = viewModel::addRecentSearch,
                onRemoveRecentSearch = viewModel::removeRecentSearch,
                onClearRecentSearches = viewModel::clearRecentSearches,
                onCreateSubfolder = viewModel::createSubfolder,
                onMoveFolder = viewModel::moveFolder,
                onDuplicateNote = viewModel::duplicateNote,
                onArchiveNote = viewModel::archiveNote,
                onAddTagToNote = viewModel::addTagToNote,
                onShowArchive = viewModel::showArchivedNotes,
                onShowTrash = viewModel::showTrashNotes,
                onShowShared = viewModel::showSharedNotes,
                onShowAllNotes = viewModel::showAllNotes,
                onMakeShared = viewModel::shareNote,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private fun NavGraphBuilder.settingsDestination(navController: NavHostController) {
    composable(
        route = SettingsRoute,
        enterTransition = {
            slideInHorizontally(animationSpec = tween(280), initialOffsetX = { it }) +
                fadeIn(tween(280))
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = tween(240), targetOffsetX = { it }) +
                fadeOut(tween(240))
        },
    ) {
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val state by viewModel.state.collectAsState()

            SettingsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onSignIn = viewModel::onSignIn,
                onSignOut = viewModel::onSignOut,
                onConfirmMove = viewModel::onConfirmMove,
                onDeleteLocal = viewModel::onDeleteLocal,
                onDismissDialog = viewModel::onDismissDialog,
                onSelectThemeMode = viewModel::setThemeMode,
                onSelectLanguage = viewModel::setLanguage,
                onRetrySync = viewModel::onRetrySync,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private fun NavGraphBuilder.editorDestination(navController: NavHostController) {
    composable(
        route = "$EditorRoute/{$NoteIdArg}?$FromDraftArg={$FromDraftArg}",
        arguments = listOf(
            navArgument(NoteIdArg) { type = NavType.StringType },
            navArgument(FromDraftArg) {
                type = NavType.BoolType
                defaultValue = false
            },
        ),
        enterTransition = {
            slideInHorizontally(animationSpec = tween(280), initialOffsetX = { it }) +
                fadeIn(tween(280))
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = tween(240), targetOffsetX = { it }) +
                fadeOut(tween(240))
        },
    ) { backStackEntry ->
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
            val viewModel: NoteEditorViewModel = hiltViewModel()
            val state by viewModel.state.collectAsState()
            val fromDraft = backStackEntry.arguments?.getBoolean(FromDraftArg) ?: false
            val view = androidx.compose.ui.platform.LocalView.current
            // Always clear focus and dismiss the IME before popping back — otherwise
            // the keyboard outlives the destination and stays open over the list.
            val hideKeyboard: () -> Unit = {
                val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }

            val backAction: () -> Unit = remember(viewModel, navController, view) {
                {
                    hideKeyboard()
                    viewModel.flush()
                    navController.popBackStack()
                }
            }
            val deleteAction: () -> Unit = remember(viewModel, navController, view) {
                {
                    hideKeyboard()
                    viewModel.delete()
                    navController.popBackStack()
                }
            }
            val leaveAction: () -> Unit = remember(viewModel, navController, view) {
                {
                    hideKeyboard()
                    viewModel.leave()
                    navController.popBackStack()
                }
            }

            BackHandler { backAction() }

            NoteEditorScreen(
                state = state,
                isDraftEntry = fromDraft,
                onContentChange = viewModel::onContentChange,
                onAddTag = viewModel::onAddTag,
                onRemoveTag = viewModel::onRemoveTag,
                onTogglePin = viewModel::onTogglePin,
                onBack = backAction,
                onDelete = deleteAction,
                onMakeShared = viewModel::makeShared,
                onManageAccess = viewModel::openMembersSheet,
                onUnshare = viewModel::unshare,
                onLeave = leaveAction,
                onDeleteForAll = deleteAction,
                onInvite = viewModel::invite,
                onChangeRole = viewModel::changeRole,
                onRemoveMember = viewModel::removeMember,
                onInviteInputChanged = viewModel::onInviteInputChanged,
                onDismissMembers = viewModel::dismissMembersSheet,
                onClearShareError = viewModel::clearShareError,
            )
        }
    }
}
