package app.tessera.ui.notes

import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId

sealed interface NotesListState {
    data object Loading : NotesListState

    data class Empty(
        val query: String,
        val availableTags: List<String>,
        val selectedTags: Set<String>,
    ) : NotesListState

    data class Content(
        val notes: List<Note>,
        val pinnedCount: Int,
        val query: String,
        val selection: Set<NoteId>,
        val availableTags: List<String>,
        val selectedTags: Set<String>,
    ) : NotesListState
}
