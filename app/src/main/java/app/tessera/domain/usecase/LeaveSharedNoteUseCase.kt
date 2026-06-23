package app.tessera.domain.usecase

import app.tessera.domain.model.NoteId
import app.tessera.domain.model.NoteNotFoundException
import app.tessera.domain.model.ShareAccessDeniedException
import app.tessera.domain.model.canLeave
import app.tessera.domain.model.myRole
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject

/**
 * The current user leaves a shared note. The owner cannot leave (they unshare or delete instead),
 * so the role is checked first.
 */
class LeaveSharedNoteUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(id: NoteId): Result<Unit> {
        val note =
            notesRepository.getNote(id)
                ?: return Result.failure(NoteNotFoundException(id))
        val myRole =
            note.myRole
                ?: return Result.failure(ShareAccessDeniedException("Note is not shared"))
        if (!myRole.canLeave) {
            return Result.failure(ShareAccessDeniedException("The owner cannot leave their own note"))
        }
        return notesRepository.leaveSharedNote(id)
    }
}
