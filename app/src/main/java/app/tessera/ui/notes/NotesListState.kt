package app.tessera.ui.notes

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId

sealed interface NotesListState {
    val folders: List<Folder>
    val selectedFolderId: FolderId?

    data object Loading : NotesListState {
        override val folders: List<Folder> = emptyList()
        override val selectedFolderId: FolderId? = null
    }

    data class Empty(
        val query: String,
        val availableTags: List<String>,
        val selectedTags: Set<String>,
        val viewMode: NotesViewMode,
        override val folders: List<Folder> = emptyList(),
        override val selectedFolderId: FolderId? = null,
    ) : NotesListState

    data class Content(
        val notes: List<Note>,
        val pinnedCount: Int,
        val query: String,
        val selection: Set<NoteId>,
        val selectionMode: Boolean,
        val availableTags: List<String>,
        val selectedTags: Set<String>,
        val viewMode: NotesViewMode,
        override val folders: List<Folder> = emptyList(),
        override val selectedFolderId: FolderId? = null,
    ) : NotesListState
}
