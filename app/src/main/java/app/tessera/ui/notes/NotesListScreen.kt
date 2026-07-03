package app.tessera.ui.notes

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.text.markdownBodyPreviewPlainText
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.tessera.TesseraBottomActionBar
import app.tessera.core.ui.tessera.TesseraChip
import app.tessera.core.ui.tessera.TesseraChipRow
import app.tessera.core.ui.tessera.TesseraDialog
import app.tessera.core.ui.tessera.MemberAvatarStack
import app.tessera.core.ui.tessera.TesseraTopBar
import app.tessera.core.ui.tessera.TesseraTopBarIconButton
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareState
import app.tessera.domain.model.UserId
import app.tessera.domain.model.canDeleteForAll
import app.tessera.domain.model.myRole
import app.tessera.ui.notes.components.FolderNamePopup
import app.tessera.ui.editor.components.TagPickerPopup
import app.tessera.ui.notes.components.FolderPickerPopup
import app.tessera.ui.notes.components.NoteActionsSheet
import app.tessera.ui.notes.components.SearchOption
import app.tessera.ui.notes.components.SearchOptionPopup
import app.tessera.ui.notes.components.SearchScreen
import app.tessera.ui.notes.components.SwipeAction
import app.tessera.ui.notes.components.TesseraNotesDrawer
import app.tessera.ui.notes.components.TesseraSwipeableNoteCard
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.PinOff
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

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
    onCreateFolder: (String) -> Unit = {},
    onRenameFolder: (FolderId, String) -> Unit = { _, _ -> },
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
    onCreateSubfolder: (FolderId, String) -> Unit = { _, _ -> },
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
    val hazeState = remember { HazeState() }

    var sheetNote by remember { mutableStateOf<Note?>(null) }
    var folderCreatorVisible by remember { mutableStateOf(false) }
    var folderEditorTarget by remember { mutableStateOf<Folder?>(null) }
    var folderPickerNote by remember { mutableStateOf<Note?>(null) }
    var tagPickerNote by remember { mutableStateOf<Note?>(null) }
    var selectionMovePickerVisible by remember { mutableStateOf(false) }
    var subfolderParent by remember { mutableStateOf<FolderId?>(null) }
    var folderPendingDelete by remember { mutableStateOf<Folder?>(null) }
    var selectedSystemKey by remember { mutableStateOf("all") }
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

    TesseraNotesDrawer(
        drawerState = drawerState,
        folderTree = state.folderTree,
        selectedFolderId = state.selectedFolderId,
        selectedSystemKey = selectedSystemKey,
        showSharedSection = state.hasSharedNotes,
        onSystemSelect = { key ->
            selectedSystemKey = key
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
            selectedSystemKey = "all"
            onSelectFolder(id)
            scope.launch { drawerState.close() }
        },
        onCreateRootFolder = { folderCreatorVisible = true },
        onCreateSubfolder = { parentId ->
            subfolderParent = parentId
        },
        onDeleteFolder = { id ->
            folderPendingDelete = state.folders.firstOrNull { it.id == id }
        },
        onSettingsClick = {
            scope.launch { drawerState.close() }
            onOpenSettings()
        },
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag(UiTestTags.NotesListRoot),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TesseraTopBar(
                        title = { Text(headerTitle) },
                        navigationIcon = {
                            TesseraTopBarIconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                contentDescription = stringResource(R.string.folders_open_drawer),
                            ) {
                                Icon(Lucide.Menu, contentDescription = null)
                            }
                        },
                        actions = {
                            if (selectionMode) {
                            if (!state.isTrashView) {
                                IconButton(onClick = { selectionMovePickerVisible = true }) {
                                    Icon(
                                        Lucide.FolderInput,
                                        contentDescription = stringResource(R.string.note_action_move),
                                    )
                                }
                            }
                            IconButton(onClick = {
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
                            }) {
                                Icon(
                                    Lucide.Trash2,
                                    contentDescription = stringResource(R.string.note_action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                            } else if (tagChips.isNotEmpty()) {
                                IconButton(
                                    onClick = { tagsVisible = !tagsVisible },
                                    modifier = Modifier.testTag(UiTestTags.TagFilterToggle),
                                ) {
                                    Icon(
                                        Lucide.Tag,
                                        contentDescription = stringResource(R.string.notes_toggle_tags),
                                        tint = if (tagsVisible) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            LocalContentColor.current
                                        },
                                    )
                                }
                            }
                        },
                    )
                    AnimatedVisibility(
                        visible = tagsVisible && tagChips.isNotEmpty() && !selectionMode,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        TesseraChipRow(chips = tagChips, onChipClick = onTagToggle)
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                TesseraBottomActionBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    searchQuery = state.query(),
                    onSearchClick = onExpandSearch,
                    onFabClick = onCreateNote,
                    fabIcon = {
                        Icon(
                            Lucide.Plus,
                            contentDescription = stringResource(R.string.create_note),
                            modifier = Modifier.testTag(UiTestTags.CreateNoteFab),
                        )
                    },
                    showFab = !state.searchExpanded && !selectionMode,
                )
            }
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
            modifier = Modifier.fillMaxSize(),
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

    FolderNamePopup(
        visible = folderCreatorVisible,
        initialName = "",
        onSubmit = { name ->
            onCreateFolder(name)
            folderCreatorVisible = false
        },
        onDismiss = { folderCreatorVisible = false },
        hazeState = hazeState,
    )

    val subfolderParentId = subfolderParent
    FolderNamePopup(
        visible = subfolderParentId != null,
        initialName = "",
        onSubmit = { name ->
            if (subfolderParentId != null) onCreateSubfolder(subfolderParentId, name)
            subfolderParent = null
        },
        onDismiss = { subfolderParent = null },
        hazeState = hazeState,
    )

    val renameTarget = folderEditorTarget
    FolderNamePopup(
        visible = renameTarget != null,
        initialName = renameTarget?.name.orEmpty(),
        onSubmit = { name ->
            if (renameTarget != null) onRenameFolder(renameTarget.id, name)
            folderEditorTarget = null
        },
        onDismiss = { folderEditorTarget = null },
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

            // Single-open coordination: at most one card is revealed at a time. Opening one card
            // (or scrolling) collapses any other, because every card reads/writes this shared id.
            var openNoteId by rememberSaveable { mutableStateOf<String?>(null) }
            val listState = rememberLazyListState()
            LaunchedEffect(listState.isScrollInProgress) {
                if (listState.isScrollInProgress) openNoteId = null
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(UiTestTags.NotesGrid),
                contentPadding = PaddingValues(bottom = 120.dp),
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
                                icon = Lucide.ArchiveRestore,
                                label = stringResource(R.string.note_action_restore),
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { onArchive(note) },
                            )
                            state.isArchiveView -> SwipeAction(
                                key = "unarchive",
                                icon = Lucide.ArchiveRestore,
                                label = stringResource(R.string.note_action_unarchive),
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { onArchive(note) },
                            )
                            else -> SwipeAction(
                                key = "archive",
                                icon = Lucide.Archive,
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
                                        icon = if (note.isPinned) Lucide.PinOff else Lucide.Pin,
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
                                        icon = Lucide.Trash2,
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
