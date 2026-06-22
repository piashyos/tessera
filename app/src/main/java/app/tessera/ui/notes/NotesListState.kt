package app.tessera.ui.notes

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId

sealed interface NotesListState {
    val folders: List<Folder>
    val folderTree: List<FolderTreeNode>
    val selectedFolderId: FolderId?
    val searchExpanded: Boolean

    data object Loading : NotesListState {
        override val folders: List<Folder> = emptyList()
        override val folderTree: List<FolderTreeNode> = emptyList()
        override val selectedFolderId: FolderId? = null
        override val searchExpanded: Boolean = false
    }

    data class Empty(
        val query: String,
        val availableTags: List<String>,
        val selectedTags: Set<String>,
        val viewMode: NotesViewMode,
        override val folders: List<Folder> = emptyList(),
        override val folderTree: List<FolderTreeNode> = emptyList(),
        override val selectedFolderId: FolderId? = null,
        override val searchExpanded: Boolean = false,
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
        override val folderTree: List<FolderTreeNode> = emptyList(),
        override val selectedFolderId: FolderId? = null,
        override val searchExpanded: Boolean = false,
    ) : NotesListState
}
