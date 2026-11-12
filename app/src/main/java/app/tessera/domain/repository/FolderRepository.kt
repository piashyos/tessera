package app.tessera.domain.repository

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderSaveResult
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>
    fun observeFolderTree(): Flow<List<FolderTreeNode>>
    fun observeFolderForNote(noteId: NoteId): Flow<FolderId?>
    fun observeNotesInFolder(folderId: FolderId): Flow<List<Note>>
    fun observeFolderAssignments(): Flow<Map<NoteId, FolderId>>
    suspend fun createFolder(name: String, color: String?, icon: String?): FolderSaveResult
    suspend fun createSubfolder(parentId: FolderId, name: String, color: String?, icon: String?): FolderSaveResult
    suspend fun updateFolder(id: FolderId, name: String, color: String?, icon: String?): FolderSaveResult
    suspend fun moveFolder(id: FolderId, newParentId: FolderId?)
    suspend fun deleteFolder(id: FolderId)
    suspend fun assignNote(noteId: NoteId, folderId: FolderId?)
}
