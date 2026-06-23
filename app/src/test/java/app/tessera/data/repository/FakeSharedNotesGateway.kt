package app.tessera.data.repository

import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * No-op [SharedNotesGateway] for [NotesRepositoryImpl] tests that exercise private-note CRUD and
 * don't care about sharing. Records the last delegated call for the few tests that do.
 */
class FakeSharedNotesGateway(
    private val members: List<ShareMember> = emptyList(),
) : SharedNotesGateway {
    var lastSharedId: NoteId? = null
    var lastUnsharedId: NoteId? = null

    override suspend fun shareNote(id: NoteId): Result<Unit> {
        lastSharedId = id
        return Result.success(Unit)
    }

    override suspend fun inviteByEmail(
        id: NoteId,
        email: String,
        role: ShareRole,
    ): Result<InviteResult> = Result.success(InviteResult.Added)

    override suspend fun changeRole(id: NoteId, uid: UserId, role: ShareRole): Result<Unit> =
        Result.success(Unit)

    override suspend fun removeMember(id: NoteId, uid: UserId): Result<Unit> = Result.success(Unit)

    override suspend fun leaveSharedNote(id: NoteId): Result<Unit> = Result.success(Unit)

    override suspend fun unshareNote(id: NoteId): Result<Unit> {
        lastUnsharedId = id
        return Result.success(Unit)
    }

    override fun observeMembers(id: NoteId): Flow<List<ShareMember>> = MutableStateFlow(members)

    override suspend fun membersForNote(id: NoteId): List<ShareMember> = members
}
