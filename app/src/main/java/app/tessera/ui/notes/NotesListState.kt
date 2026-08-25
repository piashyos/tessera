package app.tessera.ui.notes

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.UserId

sealed interface NotesListState {
    val folders: List<Folder>
    val folderTree: List<FolderTreeNode>
    val selectedFolderId: FolderId?
    val searchExpanded: Boolean
    val isArchiveView: Boolean
    val isTrashView: Boolean
    val isSharedView: Boolean
    val isPinnedView: Boolean
    val hasSharedNotes: Boolean
    val recentSearches: List<String>
    val datePeriod: DatePeriod

    data object Loading : NotesListState {
        override val folders: List<Folder> = emptyList()
        override val folderTree: List<FolderTreeNode> = emptyList()
        override val selectedFolderId: FolderId? = null
        override val searchExpanded: Boolean = false
        override val isArchiveView: Boolean = false
        override val isTrashView: Boolean = false
        override val isSharedView: Boolean = false
        override val isPinnedView: Boolean = false
        override val hasSharedNotes: Boolean = false
        override val recentSearches: List<String> = emptyList()
        override val datePeriod: DatePeriod = DatePeriod.All
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
        override val isArchiveView: Boolean = false,
        override val isTrashView: Boolean = false,
        override val isSharedView: Boolean = false,
        override val isPinnedView: Boolean = false,
        override val hasSharedNotes: Boolean = false,
        override val recentSearches: List<String> = emptyList(),
        override val datePeriod: DatePeriod = DatePeriod.All,
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
        val currentUserId: UserId? = null,
        override val folders: List<Folder> = emptyList(),
        override val folderTree: List<FolderTreeNode> = emptyList(),
        override val selectedFolderId: FolderId? = null,
        override val searchExpanded: Boolean = false,
        override val isArchiveView: Boolean = false,
        override val isTrashView: Boolean = false,
        override val isSharedView: Boolean = false,
        override val isPinnedView: Boolean = false,
        override val hasSharedNotes: Boolean = false,
        override val recentSearches: List<String> = emptyList(),
        override val datePeriod: DatePeriod = DatePeriod.All,
    ) : NotesListState
}
