package app.tessera.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.usecase.CreateNoteUseCase
import app.tessera.domain.usecase.DeleteNoteUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
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
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val selection = MutableStateFlow<Set<NoteId>>(emptySet())

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

    val state: StateFlow<NotesListState> =
        combine(
            query,
            selectedTags,
            selection,
            effectiveQuery.flatMapLatest { value ->
                if (value.isBlank()) {
                    observeNotesUseCase()
                } else {
                    searchNotesUseCase(value)
                }
            },
        ) { currentQuery, activeTags, selectedNotes, notes ->
            notes.toUiState(
                query = currentQuery,
                selectedTags = activeTags,
                selection = selectedNotes,
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

    suspend fun createNoteDraft(): NoteId = createNoteUseCase().id

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
}

private fun List<Note>.toUiState(
    query: String,
    selectedTags: Set<String>,
    selection: Set<NoteId>,
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
        )
    }

    return NotesListState.Content(
        notes = visibleNotes,
        pinnedCount = visibleNotes.count(Note::isPinned),
        query = query,
        selection = selection,
        availableTags = availableTags,
        selectedTags = selectedTags,
    )
}
