package app.tessera.ui.navigation

import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
                enterTransition = { TesseraTransitions.enter() },
                exitTransition = { TesseraTransitions.exit() },
                popEnterTransition = { TesseraTransitions.popEnter() },
                popExitTransition = { TesseraTransitions.popExit() },
            ) {
                notesListDestination(
                    navController = navController,
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
    navController: NavHostController,
    onOpenEditor: (String, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    composable(route = NotesListRoute) { entry ->
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
          ScreenMotionWrapper(navController = navController, entry = entry, animatedVisibilityScope = this) {
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
                onShowPinned = viewModel::showPinnedNotes,
                onShowAllNotes = viewModel::showAllNotes,
                onMakeShared = viewModel::shareNote,
            )
          }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private fun NavGraphBuilder.settingsDestination(navController: NavHostController) {
    composable(route = SettingsRoute) {
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val state by viewModel.state.collectAsState()
            val context = LocalContext.current

            SettingsScreen(
                state = state,
                // BuildConfig is not generated here; the manifest's debuggable flag is the debug-build switch.
                showDebugOptions = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                onSetUseNativeEditor = viewModel::setUseNativeEditor,
                onBack = { navController.popBackStack() },
                onSignIn = viewModel::onSignIn,
                onSignOut = viewModel::onSignOut,
                onConfirmMove = viewModel::onConfirmMove,
                onDeleteLocal = viewModel::onDeleteLocal,
                onDismissDialog = viewModel::onDismissDialog,
                onSelectThemeMode = viewModel::setThemeMode,
                onSetDynamicColor = viewModel::setDynamicColor,
                onSelectPalette = viewModel::setThemePalette,
                onSelectStyle = viewModel::setThemeStyle,
                onSetAmoled = viewModel::setAmoled,
                onSetGrainEnabled = viewModel::setGrainEnabled,
                onSetGrainIntensity = viewModel::setGrainIntensity,
                onSelectLanguage = viewModel::setLanguage,
                onRetrySync = viewModel::onRetrySync,
                onExport = viewModel::onExport,
                onEmptyTrash = viewModel::onEmptyTrash,
                onMessageShown = viewModel::onMessageShown,
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
                    viewModel.flushAndLeave()
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
                onFlush = viewModel::flush,
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
                imageGetter = viewModel.imageGetter,
                imageSource = viewModel.imageSource,
                onInsertImageUri = viewModel::insertImage,
                insertedImageIds = viewModel.imageInserts,
                consentRequests = viewModel.consentRequests,
            )
        }
    }
}
