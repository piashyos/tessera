package app.tessera.ui.notes

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import app.tessera.core.ui.tessera.TesseraNoteCard
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.text.markdownBodyPreviewPlainText
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.tessera.TesseraBottomActionBar
import app.tessera.core.ui.tessera.TesseraChip
import app.tessera.core.ui.tessera.LocalTagAppearances
import app.tessera.core.ui.tessera.TesseraChipRow
import app.tessera.core.ui.tessera.cardTint
import app.tessera.core.ui.tessera.toRaw
import app.tessera.core.ui.theme.AccentColor
import app.tessera.core.ui.tessera.TesseraDialog
import app.tessera.core.ui.tessera.MemberAvatarStack
import app.tessera.core.ui.tessera.TesseraTopBar
import app.tessera.core.ui.tessera.TesseraTopBarIconButton
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderSaveResult
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareState
import app.tessera.domain.model.UserId
import app.tessera.domain.model.canDeleteForAll
import app.tessera.domain.model.myRole
import app.tessera.ui.notes.components.AppearancePopup
import app.tessera.ui.editor.components.TagPickerPopup
import app.tessera.ui.notes.components.FolderPickerPopup
import app.tessera.ui.notes.components.NoteActionsSheet
import app.tessera.ui.notes.components.SearchOption
import app.tessera.ui.notes.components.SearchOptionPopup
import app.tessera.ui.notes.components.SelectionFloatingToolbar
import app.tessera.ui.notes.components.SearchScreen
import app.tessera.ui.notes.components.SwipeAction
import app.tessera.ui.notes.components.TesseraNotesDrawer
import app.tessera.ui.notes.components.TesseraSwipeableNoteCard
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin as PushPinOutlined
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Unarchive

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    state: NotesListState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onTagToggle: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onCreateNote: () -> Unit,
    onNoteClick: (NoteId) -> Unit = {},
    onTogglePin: (NoteId) -> Unit = {},
    onDeleteNote: (Note) -> Unit = {},
    onPermanentlyDelete: (Note) -> Unit = {},
    onRestoreNote: (NoteId) -> Unit = {},
    onUndoDelete: (NoteId) -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onSelectFolder: (FolderId?) -> Unit = {},
    // Folder saves return their result: the appearance popup shows NameTaken under the name field.
    onCreateFolder: suspend (String, String?, String?) -> FolderSaveResult = { _, _, _ -> FolderSaveResult.Invalid("not wired") },
    onUpdateFolder: suspend (FolderId, String, String?, String?) -> FolderSaveResult = { _, _, _, _ -> FolderSaveResult.Invalid("not wired") },
    onSetTagAppearance: (String, String?, String?) -> Unit = { _, _, _ -> },
    onDeleteFolder: (FolderId) -> Unit = {},
    onMoveNoteToFolder: (NoteId, FolderId?) -> Unit = { _, _ -> },
    onEnterSelectionMode: (NoteId) -> Unit = {},
    onExitSelectionMode: () -> Unit = {},
    onToggleSelection: (NoteId) -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
    onDeleteSelected: () -> List<NoteId> = { emptyList() },
    onPermanentlyDeleteSelected: () -> Unit = {},
    onMoveSelectedToFolder: (FolderId?) -> Unit = {},
    onRestoreNotes: (List<NoteId>) -> Unit = {},
    onExpandSearch: () -> Unit = {},
    onCollapseSearch: () -> Unit = {},
    onSetDatePeriod: (DatePeriod) -> Unit = {},
    onSetTagFilter: (String?) -> Unit = {},
    onAddRecentSearch: (String) -> Unit = {},
    onRemoveRecentSearch: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    onCreateSubfolder: suspend (FolderId, String, String?, String?) -> FolderSaveResult = { _, _, _, _ -> FolderSaveResult.Invalid("not wired") },
    onMoveFolder: (FolderId, FolderId?) -> Unit = { _, _ -> },
    onDuplicateNote: (NoteId) -> Unit = {},
    onArchiveNote: (NoteId, Boolean) -> Unit = { _, _ -> },
    onAddTagToNote: (NoteId, String) -> Unit = { _, _ -> },
    onShowArchive: () -> Unit = {},
    onShowTrash: () -> Unit = {},
    onShowShared: () -> Unit = {},
    onShowPinned: () -> Unit = {},
    onShowAllNotes: () -> Unit = {},
    onMakeShared: (NoteId) -> Unit = {},
    isRefreshing: Boolean = false,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() } // popups: the source is the whole screen (header, list, search, bar)

    var sheetNote by remember { mutableStateOf<Note?>(null) }
    var folderCreatorVisible by remember { mutableStateOf(false) }
    var folderEditorTarget by remember { mutableStateOf<Folder?>(null) }
    var folderPickerNote by remember { mutableStateOf<Note?>(null) }
    var tagPickerNote by remember { mutableStateOf<Note?>(null) }
    var selectionMovePickerVisible by remember { mutableStateOf(false) }
    var subfolderParent by remember { mutableStateOf<FolderId?>(null) }
    var folderPendingDelete by remember { mutableStateOf<Folder?>(null) }
    var tagEditorTarget by remember { mutableStateOf<String?>(null) }
    var folderNameTaken by remember { mutableStateOf(false) }
    var folderSaving by remember { mutableStateOf(false) }
    var tagsVisible by remember { mutableStateOf(false) }
    var searchFolderPickerVisible by remember { mutableStateOf(false) }
    var searchTagPickerVisible by remember { mutableStateOf(false) }
    var searchPeriodPickerVisible by remember { mutableStateOf(false) }

    val selectionMode = state.selectionActive()
    val selection = state.selectedIds()
    val visibleNotes = (state as? NotesListState.Content)?.notes.orEmpty()
    val currentUserId = (state as? NotesListState.Content)?.currentUserId
    val allSelected = visibleNotes.isNotEmpty() && selection.size >= visibleNotes.size
    val tagChips = state.toTesseraChips()

    val deletedMessage = stringResource(R.string.note_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val selectionDeletedMessage = stringResource(R.string.notes_selection_deleted)

    val handleDelete: (Note) -> Unit = { note ->
        onDeleteNote(note)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onUndoDelete(note.id)
        }
    }

    val permanentlyDeletedMessage = stringResource(R.string.notes_permanently_deleted)
    val handlePermanentDelete: (Note) -> Unit = { note ->
        onPermanentlyDelete(note)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = permanentlyDeletedMessage,
                duration = SnackbarDuration.Short,
            )
        }
    }

    val archivedMessage = stringResource(R.string.note_archived)
    val handleArchive: (Note) -> Unit = { note ->
        onArchiveNote(note.id, true)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = archivedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onArchiveNote(note.id, false)
        }
    }

    val restoredMessage = stringResource(R.string.note_restored)
    val handleUnarchive: (Note) -> Unit = { note ->
        onArchiveNote(note.id, false)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = restoredMessage,
                duration = SnackbarDuration.Short,
            )
        }
    }
    val handleRestoreFromTrash: (Note) -> Unit = { note ->
        onRestoreNote(note.id)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = restoredMessage,
                duration = SnackbarDuration.Short,
            )
        }
    }

    BackHandler(enabled = selectionMode) { onExitSelectionMode() }
    BackHandler(enabled = state.searchExpanded) { onCollapseSearch() }

    val allNotesLabel = stringResource(R.string.notes_all_notes)
    val archiveLabel = stringResource(R.string.notes_archive)
    val trashLabel = stringResource(R.string.drawer_trash)
    val sharedLabel = stringResource(R.string.drawer_shared)
    val pinnedLabel = stringResource(R.string.drawer_pinned)
    val headerTitle = when {
        selectionMode -> stringResource(R.string.notes_selection_count, selection.size)
        state.isTrashView -> trashLabel
        state.isArchiveView -> archiveLabel
        state.isSharedView -> sharedLabel
        state.isPinnedView -> pinnedLabel
        state.selectedFolderId != null ->
            state.folders.firstOrNull { it.id == state.selectedFolderId }?.name ?: allNotesLabel
        else -> allNotesLabel
    }

    // Derived from the state (not remembered): the screen leaves composition on Settings and comes back with the
    // ViewModel still showing e.g. Trash.
    val selectedSystemKey = when {
        state.isTrashView -> "trash"
        state.isArchiveView -> "archive"
        state.isPinnedView -> "pinned"
        state.isSharedView -> "shared"
        else -> "all"
    }

    TesseraNotesDrawer(
        drawerState = drawerState,
        folderTree = state.folderTree,
        selectedFolderId = state.selectedFolderId,
        selectedSystemKey = selectedSystemKey,
        showSharedSection = state.hasSharedNotes,
        onSystemSelect = { key ->
            when (key) {
                "all" -> onShowAllNotes()
                "pinned" -> onShowPinned()
                "archive" -> onShowArchive()
                "trash" -> onShowTrash()
                "shared" -> onShowShared()
            }
            scope.launch { drawerState.close() }
        },
        onFolderSelect = { id ->
            onSelectFolder(id)
            scope.launch { drawerState.close() }
        },
        onCreateRootFolder = {
            folderNameTaken = false
            folderCreatorVisible = true
        },
        onCreateSubfolder = { parentId ->
            folderNameTaken = false
            subfolderParent = parentId
        },
        onDeleteFolder = { id ->
            folderPendingDelete = state.folders.firstOrNull { it.id == id }
        },
        // The drawer stays open: the popup is a sibling drawn over it, and the change shows in the drawer at once.
        onEditFolder = { id ->
            folderNameTaken = false
            folderEditorTarget = state.folders.firstOrNull { it.id == id }
        },
        onSettingsClick = {
            scope.launch { drawerState.close() }
            onOpenSettings()
        },
    ) {
      Box(modifier = modifier.fillMaxSize().testTag(UiTestTags.NotesListRoot)) {
        // Haze draws nothing for an effect that sits INSIDE a hazeSource: the glass bar below is a sibling of the
        // sources, never a descendant. The popups blur this source; the bar blurs it too.
        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // Transparent so the app-wide background + film grain (painted by GrainOverlay) shows
            // through; the note cards paint their own opaque surface on top and stay grain-free.
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TesseraTopBar(
                        title = { Text(headerTitle) },
                        navigationIcon = {
                            if (selectionMode) {
                                // Selection is a modal mode: the nav slot is an explicit exit,
                                // not the drawer (M3 contextual-action-bar pattern).
                                TesseraTopBarIconButton(
                                    onClick = onExitSelectionMode,
                                    contentDescription = stringResource(R.string.notes_exit_selection),
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = null)
                                }
                            } else {
                                TesseraTopBarIconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    contentDescription = stringResource(R.string.folders_open_drawer),
                                ) {
                                    Icon(Icons.Rounded.Menu, contentDescription = null)
                                }
                            }
                        },
                        actions = {
                            if (selectionMode) {
                                IconButton(onClick = onToggleSelectAll) {
                                    Icon(
                                        Icons.Rounded.SelectAll,
                                        contentDescription = stringResource(R.string.notes_select_all),
                                        tint = if (allSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            LocalContentColor.current
                                        },
                                    )
                                }
                            } else {
                                val isGrid = (state as? NotesListState.Content)?.viewMode == NotesViewMode.Tiles
                                IconButton(onClick = onToggleViewMode) {
                                    Icon(
                                        if (isGrid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                        contentDescription = stringResource(R.string.notes_toggle_view_mode),
                                    )
                                }
                                if (tagChips.isNotEmpty()) {
                                    IconButton(
                                        onClick = { tagsVisible = !tagsVisible },
                                        modifier = Modifier.testTag(UiTestTags.TagFilterToggle),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Sell,
                                            contentDescription = stringResource(R.string.notes_toggle_tags),
                                            tint = if (tagsVisible) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                LocalContentColor.current
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                    AnimatedVisibility(
                        visible = tagsVisible && tagChips.isNotEmpty() && !selectionMode,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        TesseraChipRow(chips = tagChips, onChipClick = onTagToggle, onChipLongClick = { tagEditorTarget = it })
                    }
                }
            },
            // Lift snackbars clear of the bottom action bar / selection toolbar — the bar is a
            // content-Box child (for the search overlay), so the Scaffold can't do it itself.
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = 96.dp),
                )
            },
        ) { padding ->
            // Only inset the TOP (below the app bar). The content fills to the screen bottom so the
            // list scrolls behind the transparent nav bar (no empty band under it); the floating
            // bottom bar and the list's bottom contentPadding handle the nav-bar clearance instead.
            Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                Box(Modifier.fillMaxSize()) {
                NotesBody(
                    state = state,
                    onNoteClick = onNoteClick,
                    onLongClickNote = { sheetNote = it },
                    onArchive = {
                        when {
                            state.isTrashView -> handleRestoreFromTrash(it)
                            state.isArchiveView -> handleUnarchive(it)
                            else -> handleArchive(it)
                        }
                    },
                    onDeleteNote = { if (state.isTrashView) handlePermanentDelete(it) else handleDelete(it) },
                    onTogglePin = onTogglePin,
                    selection = selection,
                    selectionMode = selectionMode,
                    onToggleSelection = onToggleSelection,
                    currentUserId = currentUserId,
                )
                }
            }
        }
        }

        if (!selectionMode) {
            TesseraBottomActionBar(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                searchQuery = state.query(),
                onSearchClick = onExpandSearch,
                onFabClick = onCreateNote,
                fabIcon = {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.create_note),
                        modifier = Modifier.testTag(UiTestTags.CreateNoteFab),
                    )
                },
                showFab = !state.searchExpanded,
                hazeState = hazeState,
                // Thumb-zone drawer access: folders are the most common top-corner reach.
                leadingAction = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = stringResource(R.string.folders_open_drawer),
                        )
                    }
                },
            )
        } else {
            // Batch actions live in a reachable floating toolbar (M3E contextual pattern)
            // instead of the top-right dead zone.
            SelectionFloatingToolbar(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                isTrashView = state.isTrashView,
                onMove = { selectionMovePickerVisible = true },
                onDelete = {
                    if (state.isTrashView) {
                        val count = selection.size
                        onPermanentlyDeleteSelected()
                        if (count > 0) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = permanentlyDeletedMessage,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    } else {
                        val ids = onDeleteSelected()
                        if (ids.isNotEmpty()) {
                            scope.launch {
                                val r = snackbarHostState.showSnackbar(
                                    message = selectionDeletedMessage,
                                    actionLabel = undoLabel,
                                    duration = SnackbarDuration.Short,
                                )
                                if (r == SnackbarResult.ActionPerformed) onRestoreNotes(ids)
                            }
                        }
                    }
                },
            )
        }

        // Full-screen search surface that overlays the list while search is expanded.
        val searchQuery = state.query()
        val selectedTag = state.selectedTags().firstOrNull()
        val showResults = searchQuery.isNotBlank() ||
            state.selectedTags().isNotEmpty() ||
            state.datePeriod != DatePeriod.All ||
            state.selectedFolderId != null
        AnimatedVisibility(
            visible = state.searchExpanded,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize().hazeSource(hazeState, zIndex = 1f),
        ) {
            SearchScreen(
                query = searchQuery,
                onQueryChange = onQueryChange,
                onSubmitQuery = { if (searchQuery.isNotBlank()) onAddRecentSearch(searchQuery) },
                onCollapse = onCollapseSearch,
                showResults = showResults,
                folderValue = state.folders.firstOrNull { it.id == state.selectedFolderId }?.name
                    ?: stringResource(R.string.search_filter_all_folders),
                tagValue = selectedTag?.let { "#$it" } ?: stringResource(R.string.search_filter_none),
                periodValue = stringResource(state.datePeriod.labelRes()),
                recentSearches = state.recentSearches,
                popularTags = state.availableTags(),
                onPickFolder = { searchFolderPickerVisible = true },
                onPickTag = { searchTagPickerVisible = true },
                onPickPeriod = { searchPeriodPickerVisible = true },
                onRecentClick = { entry ->
                    onQueryChange(entry)
                    onAddRecentSearch(entry)
                },
                onRecentRemove = onRemoveRecentSearch,
                onClearHistory = onClearRecentSearches,
                onPopularTagClick = { tag -> onSetTagFilter(tag) },
                resultsContent = {
                    NotesBody(
                        state = state,
                        onNoteClick = onNoteClick,
                        onLongClickNote = { sheetNote = it },
                        onArchive = { handleArchive(it) },
                        onDeleteNote = { handleDelete(it) },
                        onTogglePin = onTogglePin,
                        selection = emptySet(),
                        selectionMode = false,
                        onToggleSelection = {},
                        currentUserId = currentUserId,
                    )
                },
            )
        }
      }
    }

    sheetNote?.let { note ->
        NoteActionsSheet(
            noteTitle = note.title.ifBlank { stringResource(R.string.note_untitled) },
            isPinned = note.isPinned,
            isArchived = note.isArchived,
            onPinToggle = { onTogglePin(note.id) },
            onSelect = { onEnterSelectionMode(note.id) },
            onMoveToFolder = { folderPickerNote = note },
            onAddTags = { tagPickerNote = note },
            onDuplicate = { onDuplicateNote(note.id) },
            onArchiveToggle = { onArchiveNote(note.id, !note.isArchived) },
            onDelete = { if (state.isTrashView) handlePermanentDelete(note) else handleDelete(note) },
            onRestore = if (state.isTrashView) {
                { handleRestoreFromTrash(note) }
            } else {
                null
            },
            showDelete = state.isTrashView || note.canDeleteFromList(),
            onDismiss = { sheetNote = null },
            onMakeShared =
                if (note.shareState is ShareState.Private &&
                    !state.isTrashView && !state.isArchiveView && !state.isSharedView
                ) {
                    { onMakeShared(note.id) }
                } else {
                    null
                },
        )
    }

    // Runs a folder save and reacts to its result: Saved closes, NameTaken shows the error, Invalid
    // (unreachable from this UI) is logged and closes.
    val saveFolder: (suspend () -> FolderSaveResult, () -> Unit) -> Unit = { save, close ->
        // A second tap while the first save runs would race it (Saved, then NameTaken for the same name).
        if (!folderSaving) {
            folderSaving = true
            scope.launch {
                try {
                    when (val result = save()) {
                        is FolderSaveResult.Saved -> { folderNameTaken = false; close() }
                        FolderSaveResult.NameTaken -> folderNameTaken = true
                        is FolderSaveResult.Invalid -> {
                            Log.w("NotesListScreen", "Folder save rejected: ${result.reason}")
                            folderNameTaken = false
                            close()
                        }
                    }
                } finally {
                    folderSaving = false
                }
            }
        }
    }

    AppearancePopup(
        visible = folderCreatorVisible,
        title = stringResource(R.string.folders_new),
        name = "",
        initialColor = null,
        initialIcon = null,
        onSave = { name, color, icon ->
            saveFolder({ onCreateFolder(name, color, icon) }) { folderCreatorVisible = false }
        },
        onDismiss = { folderCreatorVisible = false; folderNameTaken = false },
        hazeState = hazeState,
        nameTaken = folderNameTaken,
        onNameEdited = { folderNameTaken = false },
    )

    val subfolderParentId = subfolderParent
    AppearancePopup(
        visible = subfolderParentId != null,
        title = stringResource(R.string.folders_new),
        name = "",
        initialColor = null,
        initialIcon = null,
        onSave = { name, color, icon ->
            if (subfolderParentId != null) {
                saveFolder({ onCreateSubfolder(subfolderParentId, name, color, icon) }) { subfolderParent = null }
            }
        },
        onDismiss = { subfolderParent = null; folderNameTaken = false },
        hazeState = hazeState,
        nameTaken = folderNameTaken,
        onNameEdited = { folderNameTaken = false },
    )

    val editTarget = folderEditorTarget
    AppearancePopup(
        visible = editTarget != null,
        title = stringResource(R.string.folders_edit_title),
        name = editTarget?.name.orEmpty(),
        initialColor = editTarget?.color,
        initialIcon = editTarget?.icon,
        showReset = true,
        onSave = { name, color, icon ->
            if (editTarget != null) {
                saveFolder({ onUpdateFolder(editTarget.id, name, color, icon) }) { folderEditorTarget = null }
            }
        },
        onDismiss = { folderEditorTarget = null; folderNameTaken = false },
        hazeState = hazeState,
        nameTaken = folderNameTaken,
        onNameEdited = { folderNameTaken = false },
    )

    val tagTarget = tagEditorTarget
    val tagStyle = tagTarget?.let { LocalTagAppearances.current[it] }
    AppearancePopup(
        visible = tagTarget != null,
        title = "#${tagTarget.orEmpty()}",
        name = null,
        initialColor = tagStyle?.color?.key,
        initialIcon = tagStyle?.icon?.toRaw(),
        showReset = true,
        onSave = { _, color, icon ->
            // Nothing changed (also: a key of a newer version reads as none): do not write a fresh null.
            if (tagTarget != null && (color != tagStyle?.color?.key || icon != tagStyle?.icon?.toRaw())) {
                onSetTagAppearance(tagTarget, color, icon)
            }
            tagEditorTarget = null
        },
        onDismiss = { tagEditorTarget = null },
        hazeState = hazeState,
    )

    val pickerNote = folderPickerNote
    FolderPickerPopup(
        visible = pickerNote != null,
        folders = state.folders,
        onPick = { folderId ->
            if (pickerNote != null) onMoveNoteToFolder(pickerNote.id, folderId)
        },
        onDismiss = { folderPickerNote = null },
        hazeState = hazeState,
        onDeleteFolder = onDeleteFolder,
    )

    FolderPickerPopup(
        visible = selectionMovePickerVisible,
        folders = state.folders,
        onPick = { folderId -> onMoveSelectedToFolder(folderId) },
        onDismiss = { selectionMovePickerVisible = false },
        hazeState = hazeState,
        onDeleteFolder = onDeleteFolder,
    )

    val tagNote = tagPickerNote
    TagPickerPopup(
        visible = tagNote != null,
        availableTags = tagNote?.let { note ->
            val existing = note.tags.map { it.name }.toSet()
            (state.availableTags() + existing).distinct().filterNot(existing::contains)
        }.orEmpty(),
        onAddTag = { tag -> if (tagNote != null) onAddTagToNote(tagNote.id, tag) },
        onDismiss = { tagPickerNote = null },
        hazeState = hazeState,
    )

    FolderPickerPopup(
        visible = searchFolderPickerVisible,
        folders = state.folders,
        onPick = { folderId ->
            if (folderId == null) onShowAllNotes() else onSelectFolder(folderId)
        },
        onDismiss = { searchFolderPickerVisible = false },
        hazeState = hazeState,
        onDeleteFolder = onDeleteFolder,
    )

    SearchOptionPopup(
        visible = searchTagPickerVisible,
        title = stringResource(R.string.search_filter_tag),
        options = listOf(SearchOption("", stringResource(R.string.search_filter_none))) +
            state.availableTags().map { SearchOption(it, "#$it") },
        selectedKey = state.selectedTags().firstOrNull() ?: "",
        onPick = { key -> onSetTagFilter(key.ifEmpty { null }) },
        onDismiss = { searchTagPickerVisible = false },
        hazeState = hazeState,
    )

    SearchOptionPopup(
        visible = searchPeriodPickerVisible,
        title = stringResource(R.string.search_filter_period),
        options = DatePeriod.values().map { SearchOption(it.name, stringResource(it.labelRes())) },
        selectedKey = state.datePeriod.name,
        onPick = { key -> onSetDatePeriod(DatePeriod.valueOf(key)) },
        onDismiss = { searchPeriodPickerVisible = false },
        hazeState = hazeState,
    )

    val deleteFolderTarget = folderPendingDelete
    if (deleteFolderTarget != null) {
        TesseraDialog(
            onDismissRequest = { folderPendingDelete = null },
            title = stringResource(R.string.folders_delete_confirm_title),
            text = stringResource(R.string.folders_delete_confirm_message, deleteFolderTarget.name),
            confirmLabel = stringResource(R.string.folders_delete),
            onConfirm = {
                onDeleteFolder(deleteFolderTarget.id)
                folderPendingDelete = null
            },
            dismissLabel = stringResource(R.string.folders_cancel),
            destructive = true,
        )
    }
}

@Composable
private fun NotesBody(
    state: NotesListState,
    onNoteClick: (NoteId) -> Unit,
    onLongClickNote: (Note) -> Unit,
    onArchive: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onTogglePin: (NoteId) -> Unit,
    selection: Set<NoteId>,
    selectionMode: Boolean,
    onToggleSelection: (NoteId) -> Unit,
    currentUserId: UserId? = null,
) {
    when (state) {
        NotesListState.Loading ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is NotesListState.Empty ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (state.query.isBlank()) {
                            stringResource(R.string.notes_empty_title)
                        } else {
                            stringResource(R.string.notes_search_empty_title, state.query)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (state.query.isBlank()) {
                            stringResource(R.string.notes_empty_subtitle)
                        } else {
                            stringResource(R.string.notes_search_empty_subtitle)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        is NotesListState.Content -> {
            val sections = groupNotesByDate(state.notes)
            // Pin is a private-only affordance; it never appears in archive/trash/shared views.
            val showPin = !state.isTrashView && !state.isArchiveView && !state.isSharedView
            // Since the list now extends behind the transparent nav bar, reserve its height at the
            // bottom (on top of the floating-bar clearance) so the last item can scroll clear.
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            // Single-open coordination: at most one card is revealed at a time. Opening one card
            // (or scrolling) collapses any other, because every card reads/writes this shared id.
            var openNoteId by rememberSaveable { mutableStateOf<String?>(null) }
            val listState = rememberLazyListState()
            LaunchedEffect(listState.isScrollInProgress) {
                if (listState.isScrollInProgress) openNoteId = null
            }

            if (state.viewMode == NotesViewMode.Tiles) {
                // Tiles view: a 2-column grid of plain cards (no swipe actions — long-press
                // opens the same actions sheet, matching the Keep grid pattern).
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(UiTestTags.NotesGrid),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp + navBottom),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sections.forEach { section ->
                        item(key = "header-${section.group}", span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(group = section.group)
                        }
                        items(items = section.notes, key = { it.id.value }) { note ->
                            val shareState = note.shareState
                            TesseraNoteCard(
                                title = note.title.ifBlank { stringResource(R.string.note_untitled) },
                                preview = markdownBodyPreviewPlainText(note.content),
                                dateLabel = noteDateLabel(note.updatedAt),
                                tags = note.tags.map { it.name },
                                isPinned = note.isPinned,
                                onClick = {
                                    if (selectionMode) onToggleSelection(note.id) else onNoteClick(note.id)
                                },
                                onLongClick = { onLongClickNote(note) },
                                selected = selectionMode && note.id in selection,
                                avatars = if (shareState is ShareState.Shared) {
                                    {
                                        MemberAvatarStack(
                                            members = shareState.members,
                                            currentUserId = currentUserId,
                                        )
                                    }
                                } else {
                                    null
                                },
                                tint = AccentColor.fromKey(state.noteTints[note.id])?.cardTint(),
                            )
                        }
                    }
                }
                return
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(UiTestTags.NotesGrid),
                contentPadding = PaddingValues(bottom = 120.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sections.forEach { section ->
                    item(key = "header-${section.group}") {
                        SectionHeader(
                            group = section.group,
                            modifier = Modifier.animateItem(
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                fadeOutSpec = tween(durationMillis = 200),
                            ),
                        )
                    }
                    items(items = section.notes, key = { it.id.value }) { note ->
                        val preview = markdownBodyPreviewPlainText(note.content)
                        val shareState = note.shareState

                        // Left action (swipe right) is always a single archive/unarchive/restore
                        // affordance whose meaning depends on the view; onArchive is view-aware.
                        val leftAction = when {
                            state.isTrashView -> SwipeAction(
                                key = "restore",
                                icon = Icons.Rounded.Unarchive,
                                label = stringResource(R.string.note_action_restore),
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { onArchive(note) },
                            )
                            state.isArchiveView -> SwipeAction(
                                key = "unarchive",
                                icon = Icons.Rounded.Unarchive,
                                label = stringResource(R.string.note_action_unarchive),
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { onArchive(note) },
                            )
                            else -> SwipeAction(
                                key = "archive",
                                icon = Icons.Rounded.Archive,
                                label = stringResource(R.string.note_action_archive),
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { onArchive(note) },
                            )
                        }

                        // Right actions (swipe left): Pin/Unpin first (private views only), then
                        // Delete (routes to permanent delete in trash). Both may be absent, leaving
                        // the swipe-left direction inert (e.g. a shared note a viewer can't delete).
                        val canDelete = state.isTrashView || note.canDeleteFromList()
                        val rightActions = buildList {
                            if (showPin) {
                                add(
                                    SwipeAction(
                                        key = "pin",
                                        icon = if (note.isPinned) Icons.Outlined.PushPinOutlined else Icons.Rounded.PushPin,
                                        label = stringResource(
                                            if (note.isPinned) R.string.note_action_unpin
                                            else R.string.note_action_pin,
                                        ),
                                        container = MaterialTheme.colorScheme.secondaryContainer,
                                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                                        onClick = { onTogglePin(note.id) },
                                    ),
                                )
                            }
                            if (canDelete) {
                                add(
                                    SwipeAction(
                                        key = "delete",
                                        icon = Icons.Rounded.Delete,
                                        label = stringResource(
                                            if (state.isTrashView) R.string.note_action_delete_forever
                                            else R.string.note_action_delete,
                                        ),
                                        container = MaterialTheme.colorScheme.errorContainer,
                                        content = MaterialTheme.colorScheme.onErrorContainer,
                                        onClick = { onDeleteNote(note) },
                                    ),
                                )
                            }
                        }
                        TesseraSwipeableNoteCard(
                            modifier = Modifier
                                // Collapse the removed row and glide neighbours up with a
                                // bounce-free spring so deletes/archives never snap.
                                .animateItem(
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                    fadeOutSpec = tween(durationMillis = 200),
                                )
                                .padding(horizontal = 16.dp),
                            noteId = note.id.value,
                            title = note.title.ifBlank { stringResource(R.string.note_untitled) },
                            preview = preview,
                            dateLabel = noteDateLabel(note.updatedAt),
                            tags = note.tags.map { it.name },
                            isPinned = note.isPinned,
                            isOpen = openNoteId == note.id.value,
                            onOpenChange = { openNoteId = if (it) note.id.value else null },
                            onClick = {
                                if (selectionMode) onToggleSelection(note.id)
                                else onNoteClick(note.id)
                            },
                            onLongClick = { onLongClickNote(note) },
                            leftActions = listOf(leftAction),
                            rightActions = rightActions,
                            selected = selectionMode && note.id in selection,
                            avatars = if (shareState is ShareState.Shared) {
                                {
                                    MemberAvatarStack(
                                        members = shareState.members,
                                        currentUserId = currentUserId,
                                    )
                                }
                            } else {
                                null
                            },
                            tint = AccentColor.fromKey(state.noteTints[note.id])?.cardTint(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(group: NoteGroup, modifier: Modifier = Modifier) {
    val labelRes = when (group) {
        NoteGroup.Pinned -> R.string.note_group_pinned
        NoteGroup.Today -> R.string.note_group_today
        NoteGroup.Yesterday -> R.string.note_group_yesterday
        NoteGroup.ThisWeek -> R.string.note_group_this_week
        NoteGroup.ThisMonth -> R.string.note_group_this_month
        NoteGroup.Earlier -> R.string.note_group_earlier
    }
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            .testTag(UiTestTags.sectionHeader(group.name)),
    )
}

private fun NotesListState.toTesseraChips(): List<TesseraChip> {
    val available = availableTags()
    if (available.isEmpty()) return emptyList()
    val selected = selectedTags()
    return available.map { tag ->
        TesseraChip(
            key = tag,
            label = "#$tag",
            selected = tag in selected,
        )
    }
}

private fun DatePeriod.labelRes(): Int = when (this) {
    DatePeriod.All -> R.string.search_period_all
    DatePeriod.Today -> R.string.search_period_today
    DatePeriod.Week -> R.string.search_period_week
    DatePeriod.Month -> R.string.search_period_month
}

private fun NotesListState.query(): String =
    when (this) {
        NotesListState.Loading -> ""
        is NotesListState.Empty -> query
        is NotesListState.Content -> query
    }

private fun NotesListState.availableTags(): List<String> =
    when (this) {
        NotesListState.Loading -> emptyList()
        is NotesListState.Empty -> availableTags
        is NotesListState.Content -> availableTags
    }

private fun NotesListState.selectedTags(): Set<String> =
    when (this) {
        NotesListState.Loading -> emptySet()
        is NotesListState.Empty -> selectedTags
        is NotesListState.Content -> selectedTags
    }

private fun NotesListState.selectionActive(): Boolean =
    (this as? NotesListState.Content)?.selectionMode == true

private fun NotesListState.selectedIds(): Set<NoteId> =
    (this as? NotesListState.Content)?.selection ?: emptySet()

/**
 * Whether the current user may delete this note from the list. Deleting a shared note is a
 * delete-for-all (it writes a `deletedAt` tombstone to every member), which only owner/manager may
 * do. Private notes are always deletable by their single owner.
 */
private fun Note.canDeleteFromList(): Boolean =
    when (shareState) {
        is ShareState.Private -> true
        is ShareState.Shared -> myRole?.canDeleteForAll == true
    }
