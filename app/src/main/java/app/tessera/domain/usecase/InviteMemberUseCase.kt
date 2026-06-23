package app.tessera.domain.usecase

import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.NoteNotFoundException
import app.tessera.domain.model.ShareAccessDeniedException
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.canManageAccess
import app.tessera.domain.model.myRole
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject

/**
 * Invites a registered user to a shared note by email. Only owners/managers may invite; the role
 * is checked up front before any directory/Firestore call.
 */
class InviteMemberUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(
        id: NoteId,
        email: String,
        role: ShareRole,
    ): Result<InviteResult> {
        val note =
            notesRepository.getNote(id)
                ?: return Result.failure(NoteNotFoundException(id))
        val myRole =
            note.myRole
                ?: return Result.failure(ShareAccessDeniedException("Note is not shared"))
        if (!myRole.canManageAccess) {
            return Result.failure(ShareAccessDeniedException("Only owners/managers can invite"))
        }
        return notesRepository.inviteByEmail(id, email, role)
    }
}
