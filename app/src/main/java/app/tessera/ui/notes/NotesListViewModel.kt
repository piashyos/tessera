package app.tessera.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tessera.core.preferences.UserPreferences
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.usecase.CreateFolderUseCase
import app.tessera.domain.usecase.CreateNoteUseCase
import app.tessera.domain.usecase.CreateSubfolderUseCase
import app.tessera.domain.usecase.DeleteFolderUseCase
import app.tessera.domain.usecase.DeleteNoteUseCase
import app.tessera.domain.usecase.MoveFolderUseCase
import app.tessera.domain.usecase.MoveNoteToFolderUseCase
import app.tessera.domain.usecase.ObserveFolderTreeUseCase
import app.tessera.domain.usecase.ObserveFoldersUseCase
import app.tessera.domain.usecase.ObserveNotesInFolderUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
import app.tessera.domain.usecase.RenameFolderUseCase
import app.tessera.domain.usecase.RestoreNoteUseCase
import app.tessera.domain.usecase.SearchNotesUseCase
import app.tessera.domain.usecase.TogglePinUseCase
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
    private val restoreNoteUseCase: RestoreNoteUseCase,
    private val togglePinUseCase: TogglePinUseCase,
    private val observeFoldersUseCase: ObserveFoldersUseCase,
    private val observeFolderTreeUseCase: ObserveFolderTreeUseCase,
    private val observeNotesInFolderUseCase: ObserveNotesInFolderUseCase,
    private val createFolderUseCase: CreateFolderUseCase,
    private val createSubfolderUseCase: CreateSubfolderUseCase,
    private val renameFolderUseCase: RenameFolderUseCase,
    private val moveFolderUseCase: MoveFolderUseCase,
    private val deleteFolderUseCase: DeleteFolderUseCase,
    private val moveNoteToFolderUseCase: MoveNoteToFolderUseCase,
    private val userPreferences: UserPreferences,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val selection = MutableStateFlow<Set<NoteId>>(emptySet())
    private val selectionMode = MutableStateFlow(false)
    private val selectedFolderId = MutableStateFlow<FolderId?>(null)
    private val searchExpanded = MutableStateFlow(false)

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
        combine(effectiveQuery, selectedFolderId) { value, folderId -> value to folderId }
            .flatMapLatest { (value, folderId) ->
                when {
                    value.isNotBlank() -> searchNotesUseCase(value)
                    folderId != null -> observeNotesInFolderUseCase(folderId)
                    else -> observeNotesUseCase()
                }
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
            observeFoldersUseCase(),
            observeFolderTreeUseCase(),
            combine(selectedFolderId, searchExpanded) { fid, expanded -> fid to expanded },
        ) { inputs, notes, folders, folderTree, folderAndSearch ->
            val (folderId, expanded) = folderAndSearch
            notes.toUiState(
                query = inputs.query,
                selectedTags = inputs.tags,
                selection = inputs.selection,
                selectionMode = inputs.selectionMode,
                viewMode = inputs.viewMode,
                folders = folders,
                folderTree = folderTree,
                selectedFolderId = folderId,
                searchExpanded = expanded,
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
        val note = createNoteUseCase()
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

    fun restoreNote(id: NoteId) {
        viewModelScope.launch {
            restoreNoteUseCase(id)
        }
    }

    fun selectFolder(folderId: FolderId?) {
        selectedFolderId.value = folderId
    }

    fun createFolder(name: String) {
        viewModelScope.launch { createFolderUseCase(name) }
    }

    fun createSubfolder(parentId: FolderId, name: String) {
        viewModelScope.launch {
            val result = createSubfolderUseCase(parentId, name)
            if (result.isFailure) {
                eventsChannel.send(NotesListEvent.ShowSnackbar("Can't create subfolder here"))
            }
        }
    }

    fun renameFolder(id: FolderId, name: String) {
        viewModelScope.launch { renameFolderUseCase(id, name) }
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
}

private data class StateInputs(
    val query: String,
    val tags: Set<String>,
    val selection: Set<NoteId>,
    val selectionMode: Boolean,
    val viewMode: NotesViewMode,
)

private fun List<Note>.toUiState(
    query: String,
    selectedTags: Set<String>,
    selection: Set<NoteId>,
    selectionMode: Boolean,
    viewMode: NotesViewMode,
    folders: List<Folder>,
    folderTree: List<FolderTreeNode>,
    selectedFolderId: FolderId?,
    searchExpanded: Boolean,
): NotesListState {
    val availableTags = flatMap { note -> note.tags.map { tag -> tag.name } }.distinct().sorted()
    val visibleNotes =
        if (selectedTags.isEmpty()) {
            this
        } else {
            filter { note ->
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
        )
    }

    return NotesListState.Content(
        notes = visibleNotes,
        pinnedCount = visibleNotes.count(Note::isPinned),
        query = query,
        selection = selection,
        selectionMode = selectionMode,
        availableTags = availableTags,
        selectedTags = selectedTags,
        viewMode = viewMode,
        folders = folders,
        folderTree = folderTree,
        selectedFolderId = selectedFolderId,
        searchExpanded = searchExpanded,
    )
}
