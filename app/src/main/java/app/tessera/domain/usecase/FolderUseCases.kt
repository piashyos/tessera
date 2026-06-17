package app.tessera.domain.usecase

import app.tessera.domain.model.FolderId
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.FolderRepository
import javax.inject.Inject

class ObserveFoldersUseCase @Inject constructor(private val repo: FolderRepository) {
    operator fun invoke() = repo.observeFolders()
}

class ObserveNotesInFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    operator fun invoke(folderId: FolderId) = repo.observeNotesInFolder(folderId)
}

class ObserveFolderForNoteUseCase @Inject constructor(private val repo: FolderRepository) {
    operator fun invoke(noteId: NoteId) = repo.observeFolderForNote(noteId)
}

class CreateFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(name: String) = repo.createFolder(name)
}

class RenameFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(id: FolderId, name: String) = repo.renameFolder(id, name)
}

class DeleteFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(id: FolderId) = repo.deleteFolder(id)
}

class MoveNoteToFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(noteId: NoteId, folderId: FolderId?) = repo.assignNote(noteId, folderId)
}
