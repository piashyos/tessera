package app.tessera.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.domain.usecase.GetNoteUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
import app.tessera.domain.usecase.TogglePinUseCase
import app.tessera.domain.usecase.UpdateNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val AutosaveDebounceMs = 500L

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val getNoteUseCase: GetNoteUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    private val observeNotesUseCase: ObserveNotesUseCase,
    private val togglePinUseCase: TogglePinUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val noteId: NoteId = NoteId(
        savedStateHandle.get<String>("noteId")
            ?: error("NoteEditorViewModel requires 'noteId' in SavedStateHandle"),
    )

    private val _state = MutableStateFlow(NoteEditorState())
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    // The baseline Note preserves immutable fields (id, createdAt, ownerId, deletedAt, syncedAt)
    // across saves. Starts null until the initial load completes.
    private var baselineNote: Note? = null

    private var autosaveJob: Job? = null

    init {
        viewModelScope.launch {
            val note = getNoteUseCase(noteId)
            if (note != null) {
                baselineNote = note
                _state.update { current ->
                    current.copy(
                        title = note.title,
                        content = note.content,
                        tags = note.tags.map { it.name },
                        isPinned = note.isPinned,
                        isLoading = false,
                    )
                }
            } else {
                _state.update { it.copy(isLoading = false) }
            }
        }

        viewModelScope.launch {
            observeNotesUseCase().collect { allNotes ->
                val allTagNames = allNotes
                    .flatMap { it.tags }
                    .map { it.name }
                    .distinct()
                _state.update { current ->
                    val noteTags = current.tags.toSet()
                    current.copy(availableTags = allTagNames.filter { it !in noteTags })
                }
            }
        }
    }

    fun onTitleChange(title: String) {
        _state.update { it.copy(title = title) }
        scheduleAutosave()
    }

    fun onContentChange(content: String) {
        _state.update { it.copy(content = content) }
        scheduleAutosave()
    }

    fun onToggleChecklist(lineIndex: Int) {
        val current = _state.value.content
        val updated = toggleChecklistLine(current, lineIndex)
        _state.update { it.copy(content = updated) }
        scheduleAutosave()
    }

    fun onAddTag(tag: String) {
        _state.update { current ->
            val newTags = (current.tags + tag).distinct()
            current.copy(
                tags = newTags,
                availableTags = current.availableTags.filter { it != tag },
            )
        }
        scheduleAutosave()
    }

    fun onRemoveTag(tag: String) {
        _state.update { current ->
            val newTags = current.tags.filter { it != tag }
            current.copy(tags = newTags)
        }
        scheduleAutosave()
    }

    fun onTogglePin() {
        _state.update { it.copy(isPinned = !it.isPinned) }
        scheduleAutosave()
    }

    fun flush() {
        autosaveJob?.cancel()
        autosaveJob = null
        viewModelScope.launch { persist() }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AutosaveDebounceMs)
            persist()
        }
    }

    private suspend fun persist() {
        val current = _state.value
        val baseline = baselineNote ?: return
        val updated = baseline.copy(
            title = current.title,
            content = current.content,
            tags = current.tags.map { Tag(it) },
            isPinned = current.isPinned,
        )
        val saved = updateNoteUseCase(updated)
        baselineNote = saved
    }
}
