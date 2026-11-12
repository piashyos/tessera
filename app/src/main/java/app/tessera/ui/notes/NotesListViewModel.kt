package app.tessera.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tessera.core.preferences.UserPreferences
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderSaveResult
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.UserId
import app.tessera.domain.model.isShared
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.usecase.ArchiveNoteUseCase
import app.tessera.domain.usecase.CreateFolderUseCase
import app.tessera.domain.usecase.CreateNoteUseCase
import app.tessera.domain.usecase.CreateSubfolderUseCase
import app.tessera.domain.usecase.DeleteFolderUseCase
import app.tessera.domain.usecase.DeleteNoteUseCase
import app.tessera.domain.usecase.DuplicateNoteUseCase
import app.tessera.domain.usecase.GetNoteUseCase
import app.tessera.domain.usecase.MoveFolderUseCase
import app.tessera.domain.usecase.MoveNoteToFolderUseCase
import app.tessera.domain.usecase.ObserveArchivedNotesUseCase
import app.tessera.domain.usecase.ObserveDeletedNotesUseCase
import app.tessera.domain.usecase.ObserveFolderTreeUseCase
import app.tessera.domain.usecase.ObserveFolderAssignmentsUseCase
import app.tessera.domain.usecase.ObserveFoldersUseCase
import app.tessera.domain.usecase.ObserveNotesInFolderUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
import app.tessera.domain.usecase.PermanentlyDeleteNoteUseCase
import app.tessera.domain.usecase.RestoreNoteUseCase
import app.tessera.domain.usecase.SearchNotesUseCase
import app.tessera.domain.usecase.SetTagAppearanceUseCase
import app.tessera.domain.usecase.ShareNoteUseCase
import app.tessera.domain.usecase.TogglePinUseCase
import app.tessera.domain.usecase.UpdateFolderUseCase
import app.tessera.domain.usecase.UpdateNoteUseCase
import app.tessera.domain.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SearchDebounceMs = 300L

sealed interface NotesListEvent {
    data class ShowSnackbar(val message: String) : NotesListEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val observeNotesUseCase: ObserveNotesUseCase,
    private val searchNotesUseCase: SearchNotesUseCase,
    private val createNoteUseCase: CreateNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val permanentlyDeleteNoteUseCase: PermanentlyDeleteNoteUseCase,
    private val restoreNoteUseCase: RestoreNoteUseCase,
    private val togglePinUseCase: TogglePinUseCase,
    private val getNoteUseCase: GetNoteUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    private val duplicateNoteUseCase: DuplicateNoteUseCase,
    private val archiveNoteUseCase: ArchiveNoteUseCase,
    private val observeArchivedNotesUseCase: ObserveArchivedNotesUseCase,
    private val observeDeletedNotesUseCase: ObserveDeletedNotesUseCase,
    private val observeFoldersUseCase: ObserveFoldersUseCase,
    private val observeFolderTreeUseCase: ObserveFolderTreeUseCase,
    private val observeFolderAssignmentsUseCase: ObserveFolderAssignmentsUseCase,
    private val observeNotesInFolderUseCase: ObserveNotesInFolderUseCase,
    private val createFolderUseCase: CreateFolderUseCase,
    private val createSubfolderUseCase: CreateSubfolderUseCase,
    private val updateFolderUseCase: UpdateFolderUseCase,
    private val setTagAppearanceUseCase: SetTagAppearanceUseCase,
    private val moveFolderUseCase: MoveFolderUseCase,
    private val deleteFolderUseCase: DeleteFolderUseCase,
    private val moveNoteToFolderUseCase: MoveNoteToFolderUseCase,
    private val shareNoteUseCase: ShareNoteUseCase,
    private val userPreferences: UserPreferences,
    authRepository: AuthRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val selection = MutableStateFlow<Set<NoteId>>(emptySet())
    private val selectionMode = MutableStateFlow(false)
    private val selectedFolderId = MutableStateFlow<FolderId?>(null)
    private val searchExpanded = MutableStateFlow(false)
    private val showArchive = MutableStateFlow(false)
    private val showTrash = MutableStateFlow(false)
    private val showShared = MutableStateFlow(false)
    private val showPinned = MutableStateFlow(false)
    private val datePeriod = MutableStateFlow(DatePeriod.All)

    private val currentUserId: StateFlow<UserId?> = authRepository.currentUser

    private val systemView: Flow<SystemView> =
        combine(showArchive, showTrash, showShared, showPinned) { archive, trash, shared, pinned ->
            when {
                trash -> SystemView.Trash
                archive -> SystemView.Archive
                shared -> SystemView.Shared
                pinned -> SystemView.Pinned
                else -> SystemView.Notes
            }
        }

    /** Whether the user has any shared notes at all — drives the drawer "Shared" section. */
    private val hasSharedNotes: Flow<Boolean> =
        observeNotesUseCase()
            .map { notes -> notes.any(Note::isShared) }
            .distinctUntilChanged()

    private val eventsChannel = Channel<NotesListEvent>(Channel.BUFFERED)
    val events: Flow<NotesListEvent> = eventsChannel.receiveAsFlow()

    private val effectiveQuery =
        query
            .map(String::trim)
            .distinctUntilChanged()
            .transformLatest { value ->
                if (value.isBlank()) {
                    emit("")
                } else {
                    delay(SearchDebounceMs)
                    emit(value)
                }
            }

    private val notesFlow =
        combine(effectiveQuery, selectedFolderId, systemView) { value, folderId, view ->
            Triple(value, folderId, view)
        }
            .flatMapLatest { (value, folderId, view) ->
                // `tinted` is decided here, by the same branch that picks the source: only the list of one
                // folder is not tinted (search from inside a folder reads every folder, so it is).
                when {
                    view == SystemView.Trash -> observeDeletedNotesUseCase().map { NotesSource(it, tinted = true) }
                    view == SystemView.Archive -> observeArchivedNotesUseCase().map { NotesSource(it, tinted = true) }
                    view == SystemView.Shared -> observeNotesUseCase().map { NotesSource(it, tinted = true) }
                    view == SystemView.Pinned -> observeNotesUseCase().map { NotesSource(it, tinted = true) }
                    value.isNotBlank() -> searchNotesUseCase(value).map { NotesSource(it, tinted = true) }
                    folderId != null -> observeNotesInFolderUseCase(folderId).map { NotesSource(it, tinted = false) }
                    else -> observeNotesUseCase().map { NotesSource(it, tinted = true) }
                }
            }

    private val folderInputs =
        combine(
            observeFoldersUseCase(),
            observeFolderTreeUseCase(),
            observeFolderAssignmentsUseCase(),
        ) { folders, folderTree, assignments ->
            FolderInputs(folders, folderTree, assignments)
        }

    val state: StateFlow<NotesListState> =
        combine(
            combine(
                query,
                selectedTags,
                selection,
                selectionMode,
                userPreferences.notesViewMode,
            ) { q, t, sel, selMode, vm ->
                StateInputs(q, t, sel, selMode, vm)
            },
            notesFlow,
            folderInputs,
            combine(
                selectedFolderId,
                searchExpanded,
                combine(systemView, hasSharedNotes, currentUserId) { view, hasShared, uid ->
                    ViewMeta(view, hasShared, uid)
                },
                datePeriod,
                userPreferences.recentSearches,
            ) { fid, expanded, viewMeta, period, recents ->
                SearchExtras(fid, expanded, viewMeta, period, recents)
            },
        ) { inputs, source, folderData, extras ->
            source.notes.toUiState(
                query = inputs.query,
                selectedTags = inputs.tags,
                selection = inputs.selection,
                selectionMode = inputs.selectionMode,
                viewMode = inputs.viewMode,
                folders = folderData.folders,
                folderTree = folderData.folderTree,
                noteTints = if (source.tinted) noteTints(source.notes, folderData.assignments, folderData.folders) else emptyMap(),
                selectedFolderId = extras.folderId,
                searchExpanded = extras.searchExpanded,
                isArchiveView = extras.viewMeta.view == SystemView.Archive,
                isTrashView = extras.viewMeta.view == SystemView.Trash,
                isSharedView = extras.viewMeta.view == SystemView.Shared,
                isPinnedView = extras.viewMeta.view == SystemView.Pinned,
                hasSharedNotes = extras.viewMeta.hasSharedNotes,
                currentUserId = extras.viewMeta.currentUserId,
                datePeriod = extras.datePeriod,
                recentSearches = extras.recentSearches,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = NotesListState.Loading,
        )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun expandSearch() {
        searchExpanded.value = true
    }

    fun collapseSearch() {
        searchExpanded.value = false
        query.value = ""
        datePeriod.value = DatePeriod.All
    }

    fun setDatePeriod(period: DatePeriod) {
        datePeriod.value = period
    }

    /** Replaces the active tag filter with [tag], or clears it when null. */
    fun setSingleTagFilter(tag: String?) {
        selectedTags.value = if (tag == null) emptySet() else setOf(tag)
    }

    fun addRecentSearch(value: String) {
        viewModelScope.launch { userPreferences.addRecentSearch(value) }
    }

    fun removeRecentSearch(value: String) {
        viewModelScope.launch { userPreferences.removeRecentSearch(value) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { userPreferences.clearRecentSearches() }
    }

    fun onTagToggle(tag: String) {
        selectedTags.update { tags ->
            if (tag in tags) {
                tags - tag
            } else {
                tags + tag
            }
        }
    }

    fun onSelectionToggle(id: NoteId) {
        selection.update { selected ->
            if (id in selected) {
                selected - id
            } else {
                selected + id
            }
        }
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun enterSelectionMode(id: NoteId) {
        selection.value = setOf(id)
        selectionMode.value = true
    }

    fun exitSelectionMode() {
        selectionMode.value = false
        selection.value = emptySet()
    }

    fun toggleSelectAll() {
        val content = state.value as? NotesListState.Content ?: return
        val allIds = content.notes.map(Note::id).toSet()
        selection.value = if (allIds.isNotEmpty() && selection.value.containsAll(allIds)) {
            emptySet()
        } else {
            allIds
        }
    }

    fun deleteSelected(): List<NoteId> {
        val ids = selection.value.toList()
        viewModelScope.launch {
            ids.forEach { deleteNoteUseCase(it) }
        }
        exitSelectionMode()
        return ids
    }

    /** Permanently removes the selected notes (used from Trash). Irreversible, no undo. */
    fun permanentlyDeleteSelected() {
        val ids = selection.value.toList()
        viewModelScope.launch {
            permanentlyDeleteNoteUseCase(ids)
        }
        exitSelectionMode()
    }

    fun moveSelectedToFolder(folderId: FolderId?) {
        val ids = selection.value.toList()
        viewModelScope.launch {
            ids.forEach { moveNoteToFolderUseCase(it, folderId) }
        }
        exitSelectionMode()
    }

    fun restoreNotes(ids: List<NoteId>) {
        viewModelScope.launch {
            ids.forEach { restoreNoteUseCase(it) }
        }
    }

    fun onToggleViewMode() {
        viewModelScope.launch {
            val current = userPreferences.notesViewMode.first()
            val next = if (current == NotesViewMode.Tiles) NotesViewMode.List else NotesViewMode.Tiles
            userPreferences.setNotesViewMode(next)
        }
    }

    suspend fun createNoteDraft(): NoteId {
        // Stamp the signed-in user as owner at creation so the note is shareable immediately
        // (a null owner blocks the share gate). Null when signed out — adopted on next sign-in.
        val note = createNoteUseCase(currentUserId.value)
        selectedFolderId.value?.let { folderId ->
            moveNoteToFolderUseCase(note.id, folderId)
        }
        return note.id
    }

    fun togglePin(id: NoteId) {
        viewModelScope.launch {
            togglePinUseCase(id)
        }
    }

    fun deleteNote(id: NoteId) {
        viewModelScope.launch {
            selection.update { it - id }
            deleteNoteUseCase(id)
        }
    }

    fun permanentlyDeleteNote(id: NoteId) {
        viewModelScope.launch {
            selection.update { it - id }
            permanentlyDeleteNoteUseCase(id)
        }
    }

    fun restoreNote(id: NoteId) {
        viewModelScope.launch {
            restoreNoteUseCase(id)
        }
    }

    fun selectFolder(folderId: FolderId?) {
        showArchive.value = false
        showTrash.value = false
        showShared.value = false
        showPinned.value = false
        selectedFolderId.value = folderId
    }

    fun showArchivedNotes() {
        selectedFolderId.value = null
        query.value = ""
        searchExpanded.value = false
        datePeriod.value = DatePeriod.All
        showTrash.value = false
        showShared.value = false
        showPinned.value = false
        showArchive.value = true
    }

    fun showTrashNotes() {
        selectedFolderId.value = null
        query.value = ""
        searchExpanded.value = false
        datePeriod.value = DatePeriod.All
        showArchive.value = false
        showShared.value = false
        showPinned.value = false
        showTrash.value = true
    }

    fun showSharedNotes() {
        selectedFolderId.value = null
        query.value = ""
        searchExpanded.value = false
        datePeriod.value = DatePeriod.All
        showArchive.value = false
        showTrash.value = false
        showPinned.value = false
        showShared.value = true
    }

    fun showPinnedNotes() {
        selectedFolderId.value = null
        query.value = ""
        searchExpanded.value = false
        datePeriod.value = DatePeriod.All
        showArchive.value = false
        showTrash.value = false
        showShared.value = false
        showPinned.value = true
    }

    fun showAllNotes() {
        showArchive.value = false
        showTrash.value = false
        showShared.value = false
        showPinned.value = false
        selectedFolderId.value = null
    }

    fun archiveNote(id: NoteId, archived: Boolean) {
        viewModelScope.launch {
            selection.update { it - id }
            archiveNoteUseCase(id, archived)
        }
    }

    fun duplicateNote(id: NoteId) {
        viewModelScope.launch { duplicateNoteUseCase(id) }
    }

    fun addTagToNote(noteId: NoteId, tag: String) {
        viewModelScope.launch {
            val note = getNoteUseCase(noteId) ?: return@launch
            val merged = (note.tags + Tag(tag)).distinctBy { it.name.trim().lowercase() }
            if (merged.size != note.tags.size) {
                updateNoteUseCase(note.copy(tags = merged))
            }
        }
    }

    // The result goes back to the appearance popup (NameTaken is an error under its name field).
    suspend fun createFolder(name: String, color: String?, icon: String?): FolderSaveResult =
        createFolderUseCase(name, color, icon)

    suspend fun createSubfolder(parentId: FolderId, name: String, color: String?, icon: String?): FolderSaveResult =
        createSubfolderUseCase(parentId, name, color, icon)

    suspend fun updateFolder(id: FolderId, name: String, color: String?, icon: String?): FolderSaveResult =
        updateFolderUseCase(id, name, color, icon)

    fun setTagAppearance(name: String, color: String?, icon: String?) {
        viewModelScope.launch { setTagAppearanceUseCase(name, color, icon) }
    }

    fun moveFolder(folderId: FolderId, newParentId: FolderId?) {
        viewModelScope.launch {
            val result = moveFolderUseCase(folderId, newParentId)
            if (result.isFailure) {
                eventsChannel.send(NotesListEvent.ShowSnackbar("Can't move folder here"))
            }
        }
    }

    fun deleteFolder(id: FolderId) {
        viewModelScope.launch {
            if (selectedFolderId.value == id) selectedFolderId.value = null
            deleteFolderUseCase(id)
        }
    }

    fun moveNoteToFolder(noteId: NoteId, folderId: FolderId?) {
        viewModelScope.launch { moveNoteToFolderUseCase(noteId, folderId) }
    }

    /** Promotes a private note to shared from the list; surfaces a snackbar with the outcome. */
    fun shareNote(id: NoteId) {
        viewModelScope.launch {
            val message =
                shareNoteUseCase(id)
                    .fold(
                        onSuccess = { "Note is now shared" },
                        onFailure = { e -> e.message ?: "Couldn't share this note" },
                    )
            eventsChannel.send(NotesListEvent.ShowSnackbar(message))
        }
    }
}

/** Notes of the current list and whether their cards take the folder colour (not in a single folder's list). */
private data class NotesSource(val notes: List<Note>, val tinted: Boolean)

private data class FolderInputs(
    val folders: List<Folder>,
    val folderTree: List<FolderTreeNode>,
    val assignments: Map<NoteId, FolderId>,
)

private data class StateInputs(
    val query: String,
    val tags: Set<String>,
    val selection: Set<NoteId>,
    val selectionMode: Boolean,
    val viewMode: NotesViewMode,
)

private enum class SystemView { Notes, Archive, Trash, Shared, Pinned }

private data class ViewMeta(
    val view: SystemView,
    val hasSharedNotes: Boolean,
    val currentUserId: UserId?,
)

private data class SearchExtras(
    val folderId: FolderId?,
    val searchExpanded: Boolean,
    val viewMeta: ViewMeta,
    val datePeriod: DatePeriod,
    val recentSearches: List<String>,
)

private fun List<Note>.toUiState(
    query: String,
    selectedTags: Set<String>,
    selection: Set<NoteId>,
    selectionMode: Boolean,
    viewMode: NotesViewMode,
    folders: List<Folder>,
    folderTree: List<FolderTreeNode>,
    noteTints: Map<NoteId, String>,
    selectedFolderId: FolderId?,
    searchExpanded: Boolean,
    isArchiveView: Boolean,
    isTrashView: Boolean,
    isSharedView: Boolean,
    isPinnedView: Boolean,
    hasSharedNotes: Boolean,
    currentUserId: UserId?,
    datePeriod: DatePeriod,
    recentSearches: List<String>,
): NotesListState {
    val scoped = when {
        isSharedView -> filter(Note::isShared)
        isPinnedView -> filter(Note::isPinned)
        else -> this
    }
    val availableTags = scoped.flatMap { note -> note.tags.map { tag -> tag.name } }.distinct().sorted()
    val periodFiltered = datePeriod.windowMillis?.let { window ->
        val cutoff = System.currentTimeMillis() - window
        scoped.filter { note -> note.updatedAt >= cutoff }
    } ?: scoped
    val visibleNotes =
        if (selectedTags.isEmpty()) {
            periodFiltered
        } else {
            periodFiltered.filter { note ->
                val noteTags = note.tags.map { tag -> tag.name }.toSet()
                selectedTags.all(noteTags::contains)
            }
        }

    if (visibleNotes.isEmpty()) {
        return NotesListState.Empty(
            query = query,
            availableTags = availableTags,
            selectedTags = selectedTags,
            viewMode = viewMode,
            folders = folders,
            folderTree = folderTree,
            selectedFolderId = selectedFolderId,
            searchExpanded = searchExpanded,
            isArchiveView = isArchiveView,
            isTrashView = isTrashView,
            isSharedView = isSharedView,
            isPinnedView = isPinnedView,
            hasSharedNotes = hasSharedNotes,
            recentSearches = recentSearches,
            datePeriod = datePeriod,
        )
    }

    return NotesListState.Content(
        notes = visibleNotes,
        noteTints = noteTints,
        pinnedCount = visibleNotes.count(Note::isPinned),
        query = query,
        selection = selection,
        selectionMode = selectionMode,
        availableTags = availableTags,
        selectedTags = selectedTags,
        viewMode = viewMode,
        currentUserId = currentUserId,
        folders = folders,
        folderTree = folderTree,
        selectedFolderId = selectedFolderId,
        searchExpanded = searchExpanded,
        isArchiveView = isArchiveView,
        isTrashView = isTrashView,
        isSharedView = isSharedView,
        isPinnedView = isPinnedView,
        hasSharedNotes = hasSharedNotes,
        recentSearches = recentSearches,
        datePeriod = datePeriod,
    )
}
