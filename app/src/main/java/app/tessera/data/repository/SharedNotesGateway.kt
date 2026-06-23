package app.tessera.data.repository

import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/**
 * Orchestrates the share/membership operations against `sharedNotes` and the local mirror. Kept
 * separate from [NotesRepositoryImpl] so the latter stays focused on the local-first note CRUD and
 * gains a single collaborator instead of half a dozen.
 *
 * Permission checks live in the domain UseCases (fail-fast); this layer performs the work assuming
 * the caller is authorized, and the Firestore rules are the final authority.
 */
interface SharedNotesGateway {
    suspend fun shareNote(id: NoteId): Result<Unit>

    suspend fun inviteByEmail(id: NoteId, email: String, role: ShareRole): Result<InviteResult>

    suspend fun changeRole(id: NoteId, uid: UserId, role: ShareRole): Result<Unit>

    suspend fun removeMember(id: NoteId, uid: UserId): Result<Unit>

    suspend fun leaveSharedNote(id: NoteId): Result<Unit>

    suspend fun unshareNote(id: NoteId): Result<Unit>

    fun observeMembers(id: NoteId): Flow<List<ShareMember>>

    /** Snapshot of a note's mirrored members, used when projecting a note to the domain model. */
    suspend fun membersForNote(id: NoteId): List<ShareMember>
}
