package app.tessera.domain.usecase

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

abstract class FakeFolderRepo : FolderRepository {
    override fun observeFolders(): Flow<List<Folder>> = flowOf(emptyList())
    override fun observeFolderTree(): Flow<List<FolderTreeNode>> = flowOf(emptyList())
    override fun observeFolderForNote(noteId: NoteId): Flow<FolderId?> = flowOf(null)
    override fun observeNotesInFolder(folderId: FolderId): Flow<List<Note>> = flowOf(emptyList())
    override suspend fun createFolder(name: String): Folder = error("not stubbed")
    override suspend fun createSubfolder(parentId: FolderId, name: String): Folder = error("not stubbed")
    override suspend fun renameFolder(id: FolderId, name: String) = Unit
    override suspend fun moveFolder(id: FolderId, newParentId: FolderId?) = Unit
    override suspend fun deleteFolder(id: FolderId) = Unit
    override suspend fun assignNote(noteId: NoteId, folderId: FolderId?) = Unit
}
