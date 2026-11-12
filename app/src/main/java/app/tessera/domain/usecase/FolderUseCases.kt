package app.tessera.domain.usecase

import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderSaveResult
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.FolderRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ObserveFoldersUseCase @Inject constructor(private val repo: FolderRepository) {
    operator fun invoke() = repo.observeFolders()
}

class ObserveFolderTreeUseCase @Inject constructor(private val repo: FolderRepository) {
    operator fun invoke() = repo.observeFolderTree()
}

class ObserveNotesInFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    operator fun invoke(folderId: FolderId) = repo.observeNotesInFolder(folderId)
}

class ObserveFolderForNoteUseCase @Inject constructor(private val repo: FolderRepository) {
    operator fun invoke(noteId: NoteId) = repo.observeFolderForNote(noteId)
}

class ObserveFolderAssignmentsUseCase @Inject constructor(private val repo: FolderRepository) {
    operator fun invoke() = repo.observeFolderAssignments()
}

class CreateFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(name: String, color: String? = null, icon: String? = null) =
        repo.createFolder(name, color, icon)
}

class CreateSubfolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(
        parentId: FolderId,
        name: String,
        color: String? = null,
        icon: String? = null,
    ): FolderSaveResult {
        val folders = repo.observeFolders().first()
        val parent = folders.firstOrNull { it.id == parentId }
            ?: return FolderSaveResult.Invalid("parent not found")
        if (parent.parentId != null) {
            return FolderSaveResult.Invalid("depth")
        }
        return repo.createSubfolder(parentId, name, color, icon)
    }
}

class UpdateFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(id: FolderId, name: String, color: String?, icon: String?) =
        repo.updateFolder(id, name, color, icon)
}

class MoveFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(folderId: FolderId, newParentId: FolderId?): Result<Unit> {
        val folders = repo.observeFolders().first()
        if (wouldCreateCycle(folders, folderId, newParentId)) {
            return Result.failure(FolderValidationException("cycle"))
        }
        if (wouldExceedMaxDepth(folders, folderId, newParentId)) {
            return Result.failure(FolderValidationException("depth"))
        }
        return runCatching { repo.moveFolder(folderId, newParentId) }
    }
}

class DeleteFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(id: FolderId) = repo.deleteFolder(id)
}

class MoveNoteToFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(noteId: NoteId, folderId: FolderId?) = repo.assignNote(noteId, folderId)
}

class FolderValidationException(message: String) : RuntimeException(message)
