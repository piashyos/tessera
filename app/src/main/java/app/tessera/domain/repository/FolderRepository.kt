package app.tessera.domain.repository

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>
    fun observeFolderForNote(noteId: NoteId): Flow<FolderId?>
    fun observeNotesInFolder(folderId: FolderId): Flow<List<Note>>
    suspend fun createFolder(name: String): Folder
    suspend fun renameFolder(id: FolderId, name: String)
    suspend fun deleteFolder(id: FolderId)
    suspend fun assignNote(noteId: NoteId, folderId: FolderId?)
}
