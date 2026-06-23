package app.tessera.domain.usecase

import app.tessera.domain.model.NoteId
import app.tessera.domain.model.NoteNotFoundException
import app.tessera.domain.model.ShareAccessDeniedException
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject

/**
 * Makes a private note shared. Only the note's owner may do this; the check is fail-fast so the UI
 * gets a clear error without a round-trip the security rules would reject anyway.
 */
class ShareNoteUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(id: NoteId): Result<Unit> {
        val note =
            notesRepository.getNote(id)
                ?: return Result.failure(NoteNotFoundException(id))
        val me = authRepository.currentUser.value
            ?: return Result.failure(ShareAccessDeniedException("Not signed in"))
        if (note.ownerId != null && note.ownerId != me) {
            return Result.failure(ShareAccessDeniedException("Only the owner can share a note"))
        }
        return notesRepository.shareNote(id)
    }
}
