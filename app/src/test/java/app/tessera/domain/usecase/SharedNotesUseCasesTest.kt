package app.tessera.domain.usecase

import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareAccessDeniedException
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.ShareState
import app.tessera.domain.model.UserId
import app.tessera.domain.model.UserProfile
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SharedNotesUseCasesTest {
    private val noteId = NoteId("note-1")

    // --- ShareNoteUseCase ---

    @Test
    fun `share succeeds when current user owns the note`() =
        runTest {
            val repo = FakeRepo(note = privateNote(owner = "me"))
            val auth = FakeAuth(uid = "me")

            val result = ShareNoteUseCase(repo, auth)(noteId)

            assertTrue(result.isSuccess)
            assertEquals(noteId, repo.sharedId)
        }

    @Test
    fun `share succeeds for a null-owner note when signed in`() =
        runTest {
            val repo = FakeRepo(note = privateNote(owner = null))
            val auth = FakeAuth(uid = "me")

            val result = ShareNoteUseCase(repo, auth)(noteId)

            assertTrue(result.isSuccess)
            assertEquals(noteId, repo.sharedId)
        }

    @Test
    fun `share is denied when signed out`() =
        runTest {
            val repo = FakeRepo(note = privateNote(owner = null))
            val auth = FakeAuth(uid = null)

            val result = ShareNoteUseCase(repo, auth)(noteId)

            assertTrue(result.exceptionOrNull() is ShareAccessDeniedException)
            assertNull(repo.sharedId)
        }

    @Test
    fun `share is denied when current user is not the owner`() =
        runTest {
            val repo = FakeRepo(note = privateNote(owner = "someone-else"))
            val auth = FakeAuth(uid = "me")

            val result = ShareNoteUseCase(repo, auth)(noteId)

            assertTrue(result.exceptionOrNull() is ShareAccessDeniedException)
            assertNull(repo.sharedId)
        }

    @Test
    fun `share fails when the note does not exist`() =
        runTest {
            val repo = FakeRepo(note = null)
            val auth = FakeAuth(uid = "me")

            val result = ShareNoteUseCase(repo, auth)(noteId)

            assertTrue(result.isFailure)
            assertNull(repo.sharedId)
        }

    // --- InviteMemberUseCase ---

    @Test
    fun `invite is allowed for a manager and forwards the result`() =
        runTest {
            val repo = FakeRepo(note = sharedNote(ShareRole.MANAGER))

            val result = InviteMemberUseCase(repo)(noteId, "x@y.com", ShareRole.EDITOR)

            assertEquals(InviteResult.Added, result.getOrNull())
            assertEquals(Triple(noteId, "x@y.com", ShareRole.EDITOR), repo.invited)
        }

    @Test
    fun `invite is denied for an editor`() =
        runTest {
            val repo = FakeRepo(note = sharedNote(ShareRole.EDITOR))

            val result = InviteMemberUseCase(repo)(noteId, "x@y.com", ShareRole.VIEWER)

            assertTrue(result.exceptionOrNull() is ShareAccessDeniedException)
            assertNull(repo.invited)
        }

    @Test
    fun `invite is denied on a private note`() =
        runTest {
            val repo = FakeRepo(note = privateNote(owner = "me"))

            val result = InviteMemberUseCase(repo)(noteId, "x@y.com", ShareRole.VIEWER)

            assertTrue(result.exceptionOrNull() is ShareAccessDeniedException)
        }

    // --- Change role / remove member ---

    @Test
    fun `change role allowed for owner, denied for viewer`() =
        runTest {
            val owner = FakeRepo(note = sharedNote(ShareRole.OWNER))
            assertTrue(ChangeMemberRoleUseCase(owner)(noteId, UserId("u"), ShareRole.MANAGER).isSuccess)

            val viewer = FakeRepo(note = sharedNote(ShareRole.VIEWER))
            assertTrue(
                ChangeMemberRoleUseCase(viewer)(noteId, UserId("u"), ShareRole.MANAGER)
                    .exceptionOrNull() is ShareAccessDeniedException,
            )
        }

    @Test
    fun `remove member denied for editor`() =
        runTest {
            val repo = FakeRepo(note = sharedNote(ShareRole.EDITOR))

            val result = RemoveMemberUseCase(repo)(noteId, UserId("u"))

            assertTrue(result.exceptionOrNull() is ShareAccessDeniedException)
            assertNull(repo.removedUid)
        }

    // --- Leave ---

    @Test
    fun `leave allowed for editor, denied for owner`() =
        runTest {
            val editor = FakeRepo(note = sharedNote(ShareRole.EDITOR))
            assertTrue(LeaveSharedNoteUseCase(editor)(noteId).isSuccess)
            assertEquals(noteId, editor.leftId)

            val owner = FakeRepo(note = sharedNote(ShareRole.OWNER))
            assertTrue(
                LeaveSharedNoteUseCase(owner)(noteId).exceptionOrNull() is ShareAccessDeniedException,
            )
            assertNull(owner.leftId)
        }

    // --- Unshare ---

    @Test
    fun `unshare allowed for owner only`() =
        runTest {
            val owner = FakeRepo(note = sharedNote(ShareRole.OWNER))
            assertTrue(UnshareNoteUseCase(owner)(noteId).isSuccess)
            assertEquals(noteId, owner.unsharedId)

            val manager = FakeRepo(note = sharedNote(ShareRole.MANAGER))
            assertTrue(
                UnshareNoteUseCase(manager)(noteId).exceptionOrNull() is ShareAccessDeniedException,
            )
            assertNull(manager.unsharedId)
        }

    // --- helpers ---

    private fun privateNote(owner: String?) =
        baseNote().copy(ownerId = owner?.let(::UserId), shareState = ShareState.Private)

    private fun sharedNote(role: ShareRole) =
        baseNote().copy(
            ownerId = UserId("owner"),
            shareState = ShareState.Shared(members = emptyList(), myRole = role),
        )

    private fun baseNote() =
        Note(
            id = noteId,
            title = "T",
            content = "B",
            tags = emptyList(),
            isPinned = false,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
            syncedAt = null,
            ownerId = null,
        )
}

private class FakeAuth(uid: String?) : AuthRepository {
    override val currentUser: StateFlow<UserId?> = MutableStateFlow(uid?.let(::UserId))
    override val currentProfile: StateFlow<UserProfile?> =
        MutableStateFlow(uid?.let { UserProfile(UserId(it), "Name", "$it@x.com", null) })

    override suspend fun signIn(): Result<UserId> = Result.success(currentUser.value!!)

    override suspend fun signOut() {}
}

private class FakeRepo(private val note: Note?) : NotesRepository {
    var sharedId: NoteId? = null
    var invited: Triple<NoteId, String, ShareRole>? = null
    var removedUid: UserId? = null
    var leftId: NoteId? = null
    var unsharedId: NoteId? = null

    override suspend fun getNote(id: NoteId): Note? = note

    override suspend fun shareNote(id: NoteId): Result<Unit> {
        sharedId = id
        return Result.success(Unit)
    }

    override suspend fun inviteByEmail(id: NoteId, email: String, role: ShareRole): Result<InviteResult> {
        invited = Triple(id, email, role)
        return Result.success(InviteResult.Added)
    }

    override suspend fun changeRole(id: NoteId, uid: UserId, role: ShareRole): Result<Unit> =
        Result.success(Unit)

    override suspend fun removeMember(id: NoteId, uid: UserId): Result<Unit> {
        removedUid = uid
        return Result.success(Unit)
    }

    override suspend fun leaveSharedNote(id: NoteId): Result<Unit> {
        leftId = id
        return Result.success(Unit)
    }

    override suspend fun unshareNote(id: NoteId): Result<Unit> {
        unsharedId = id
        return Result.success(Unit)
    }

    override fun observeMembers(id: NoteId): Flow<List<ShareMember>> = MutableStateFlow(emptyList())

    // Unused by these tests.
    override fun observeNotes(): Flow<List<Note>> = MutableStateFlow(emptyList())

    override fun observeNote(id: NoteId): Flow<Note?> = MutableStateFlow(note)

    override fun searchNotes(query: String): Flow<List<Note>> = MutableStateFlow(emptyList())

    override fun observeArchivedNotes(): Flow<List<Note>> = MutableStateFlow(emptyList())

    override fun observeDeletedNotes(): Flow<List<Note>> = MutableStateFlow(emptyList())

    override suspend fun createNote(ownerId: UserId?): Note = error("unused")

    override suspend fun updateNote(note: Note): Note = note

    override suspend fun deleteNote(id: NoteId) {}

    override suspend fun permanentlyDeleteNote(id: NoteId) {}

    override suspend fun restoreNote(id: NoteId) {}

    override suspend fun togglePin(id: NoteId) {}

    override suspend fun setArchived(id: NoteId, archived: Boolean) {}
}
