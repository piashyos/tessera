package app.tessera.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.domain.usecase.CreateNoteUseCase
import app.tessera.domain.usecase.GetNoteUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
import app.tessera.domain.usecase.UpdateNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the shared text lands on save. */
sealed interface ShareTarget {
    data object NewNote : ShareTarget

    data class ExistingNote(val id: NoteId, val title: String) : ShareTarget
}

data class ShareUiState(
    /** The editable field: for a new note it's the shared text; for an existing note it's the
     *  shared text followed by that note's current content (so the field shows the final result). */
    val text: String = "",
    /** The original text from the share intent, kept so switching target can re-derive [text]. */
    val sharedText: String = "",
    val target: ShareTarget = ShareTarget.NewNote,
    val tags: List<String> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = text.isNotBlank() && !saving && !saved
}

/** Combines the shared text (kept at the START) with an existing note's content. */
private fun combineSharedWithExisting(sharedText: String, existingContent: String): String {
    val shared = sharedText.trim()
    val existing = existingContent.trim()
    return when {
        existing.isEmpty() -> shared
        shared.isEmpty() -> existing
        else -> "$shared\n\n$existing"
    }
}

@HiltViewModel
class ShareReceiverViewModel @Inject constructor(
    private val createNote: CreateNoteUseCase,
    private val updateNote: UpdateNoteUseCase,
    private val getNote: GetNoteUseCase,
    observeNotes: ObserveNotesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private val pickerQuery = MutableStateFlow("")
    val query: StateFlow<String> = pickerQuery.asStateFlow()

    /** Existing notes for the target picker, newest first, filtered by the picker query. */
    val pickerNotes: StateFlow<List<Note>> =
        combine(observeNotes(), pickerQuery) { notes, query ->
            val sorted = notes.sortedByDescending { it.updatedAt }
            if (query.isBlank()) {
                sorted
            } else {
                sorted.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Seeds the sheet with the text from the incoming share intent; later calls are no-ops. */
    fun setSharedText(text: String) {
        _uiState.update { state ->
            if (state.sharedText.isEmpty()) state.copy(sharedText = text, text = text) else state
        }
    }

    fun onTextChange(text: String) = _uiState.update { it.copy(text = text) }

    fun onQueryChange(value: String) {
        pickerQuery.value = value
    }

    /** Back to a new note: the field is just the shared text again. */
    fun selectNewNote() = _uiState.update {
        it.copy(target = ShareTarget.NewNote, text = it.sharedText)
    }

    /** Adopt an existing note: prefill the field with the shared text (at the start) followed by
     *  the note's current content with all its markup, so the user sees/edits the final result. */
    fun selectExistingNote(note: Note) = _uiState.update {
        it.copy(
            target = ShareTarget.ExistingNote(note.id, note.title),
            text = combineSharedWithExisting(it.sharedText, note.content),
        )
    }

    fun addTag(raw: String) {
        val tag = raw.trim().removePrefix("#").trim()
        if (tag.isEmpty()) return
        _uiState.update { state ->
            if (state.tags.any { it.equals(tag, ignoreCase = true) }) state
            else state.copy(tags = state.tags + tag)
        }
    }

    fun removeTag(tag: String) = _uiState.update { state -> state.copy(tags = state.tags - tag) }

    /** Saves into the chosen target; flips [ShareUiState.saved] so the activity can finish. */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val newTags = state.tags.map(::Tag)
            when (val target = state.target) {
                is ShareTarget.NewNote -> {
                    val draft = createNote()
                    updateNote(draft.copy(content = state.text.trim(), tags = newTags))
                }
                is ShareTarget.ExistingNote -> {
                    val existing = getNote(target.id)
                    if (existing == null) {
                        // The picked note vanished (deleted on another device) — fall back
                        // to a new note rather than silently dropping the shared text.
                        val draft = createNote()
                        updateNote(draft.copy(content = state.text.trim(), tags = newTags))
                    } else {
                        // [state.text] already holds the shared text (at the start) plus the note's
                        // content — as prefilled on selection and possibly edited — so it is the
                        // full new content, not something to append again.
                        val mergedTags = (existing.tags + newTags).distinctBy { it.name.lowercase() }
                        updateNote(existing.copy(content = state.text.trim(), tags = mergedTags))
                    }
                }
            }
            _uiState.update { it.copy(saving = false, saved = true) }
        }
    }
}
