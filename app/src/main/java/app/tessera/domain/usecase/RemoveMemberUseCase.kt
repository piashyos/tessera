package app.tessera.domain.usecase

import app.tessera.domain.model.NoteId
import app.tessera.domain.model.NoteNotFoundException
import app.tessera.domain.model.ShareAccessDeniedException
import app.tessera.domain.model.UserId
import app.tessera.domain.model.canManageAccess
import app.tessera.domain.model.myRole
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject

/** Removes a member from a shared note. Only owners/managers may do this. */
class RemoveMemberUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(id: NoteId, uid: UserId): Result<Unit> {
        val note =
            notesRepository.getNote(id)
                ?: return Result.failure(NoteNotFoundException(id))
        val myRole =
            note.myRole
                ?: return Result.failure(ShareAccessDeniedException("Note is not shared"))
        if (!myRole.canManageAccess) {
            return Result.failure(ShareAccessDeniedException("Only owners/managers can remove members"))
        }
        return notesRepository.removeMember(id, uid)
    }
}
