package app.tessera.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.usecase.CreateFolderUseCase
import app.tessera.domain.usecase.CreateNoteUseCase
import app.tessera.domain.usecase.DeleteFolderUseCase
import app.tessera.domain.usecase.DeleteNoteUseCase
import app.tessera.domain.usecase.MoveNoteToFolderUseCase
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SearchDebounceMs = 300L

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
    private val observeNotesInFolderUseCase: ObserveNotesInFolderUseCase,
    private val createFolderUseCase: CreateFolderUseCase,
    private val renameFolderUseCase: RenameFolderUseCase,
    private val deleteFolderUseCase: DeleteFolderUseCase,
    private val moveNoteToFolderUseCase: MoveNoteToFolderUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val selection = MutableStateFlow<Set<NoteId>>(emptySet())
    private val viewMode = MutableStateFlow(NotesViewMode.List)
    private val selectedFolderId = MutableStateFlow<FolderId?>(null)

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
            combine(query, selectedTags, selection, viewMode) { q, t, sel, vm -> StateInputs(q, t, sel, vm) },
            notesFlow,
            observeFoldersUseCase(),
            selectedFolderId,
        ) { inputs, notes, folders, folderId ->
            notes.toUiState(
                query = inputs.query,
                selectedTags = inputs.tags,
                selection = inputs.selection,
                viewMode = inputs.viewMode,
                folders = folders,
                selectedFolderId = folderId,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = NotesListState.Loading,
        )

    fun onQueryChange(value: String) {
        query.value = value
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

    fun onToggleViewMode() {
        viewMode.update { if (it == NotesViewMode.Tiles) NotesViewMode.List else NotesViewMode.Tiles }
    }

    suspend fun createNoteDraft(): NoteId {
        val note = createNoteUseCase()
        // If a folder is the active filter, the new draft must land there — otherwise
        // it would silently appear in "All Notes" and surprise the user.
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

    fun renameFolder(id: FolderId, name: String) {
        viewModelScope.launch { renameFolderUseCase(id, name) }
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
    val viewMode: NotesViewMode,
)

private fun List<Note>.toUiState(
    query: String,
    selectedTags: Set<String>,
    selection: Set<NoteId>,
    viewMode: NotesViewMode,
    folders: List<Folder>,
    selectedFolderId: FolderId?,
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
            selectedFolderId = selectedFolderId,
        )
    }

    return NotesListState.Content(
        notes = visibleNotes,
        pinnedCount = visibleNotes.count(Note::isPinned),
        query = query,
        selection = selection,
        availableTags = availableTags,
        selectedTags = selectedTags,
        viewMode = viewMode,
        folders = folders,
        selectedFolderId = selectedFolderId,
    )
}
