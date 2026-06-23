package app.tessera.domain.usecase

import app.tessera.domain.model.NoteId
import app.tessera.domain.model.NoteNotFoundException
import app.tessera.domain.model.ShareAccessDeniedException
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.myRole
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject

/**
 * Returns a shared note to the owner's private subtree. Only the owner may unshare — managers can
 * manage members but cannot pull the note out of sharing.
 */
class UnshareNoteUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(id: NoteId): Result<Unit> {
        val note =
            notesRepository.getNote(id)
                ?: return Result.failure(NoteNotFoundException(id))
        if (note.myRole != ShareRole.OWNER) {
            return Result.failure(ShareAccessDeniedException("Only the owner can unshare a note"))
        }
        return notesRepository.unshareNote(id)
    }
}
