package app.tessera.ui.editor

import androidx.lifecycle.SavedStateHandle
import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.ShareState
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import app.tessera.domain.model.UserProfile
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.NotesRepository
import app.tessera.domain.usecase.ChangeMemberRoleUseCase
import app.tessera.domain.usecase.DeleteNoteUseCase
import app.tessera.domain.usecase.GetNoteUseCase
import app.tessera.domain.usecase.InviteMemberUseCase
import app.tessera.domain.usecase.LeaveSharedNoteUseCase
import app.tessera.domain.usecase.ObserveMembersUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
import app.tessera.domain.usecase.RemoveMemberUseCase
import app.tessera.domain.usecase.ShareNoteUseCase
import app.tessera.domain.usecase.UnshareNoteUseCase
import app.tessera.domain.usecase.UpdateNoteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads note from savedStateHandle into state`() = runTest(dispatcher.scheduler) {
        val note = sampleNote("note-1", content = "World", tags = listOf("kotlin"))
        val repo = FakeEditorRepository(notes = listOf(note))
        val vm = repo.createViewModel(noteId = "note-1")

        runCurrent()

        val state = vm.state.value
        assertFalse(state.isLoading, "isLoading should be false after load")
        assertEquals("World", state.content)
        assertEquals(listOf("kotlin"), state.tags)
        assertTrue(state.isPinned == note.isPinned)
    }

    @Test
    fun `content change schedules autosave with 500ms debounce`() =
        runTest(dispatcher.scheduler) {
            val note = sampleNote("note-2", content = "Old")
            val repo = FakeEditorRepository(notes = listOf(note))
            val vm = repo.createViewModel(noteId = "note-2")
            runCurrent()

            vm.onContentChange("New Content")

            advanceTimeBy(499)
            assertTrue(repo.updateCalls.isEmpty(), "No updateNote expected before debounce fires")

            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, repo.updateCalls.size, "Exactly one updateNote expected after debounce")
            assertEquals("New Content", repo.updateCalls.first().content)
        }

    @Test
    fun `rapid content changes collapse to single updateNote with latest value`() =
        runTest(dispatcher.scheduler) {
            val note = sampleNote("note-3")
            val repo = FakeEditorRepository(notes = listOf(note))
            val vm = repo.createViewModel(noteId = "note-3")
            runCurrent()

            vm.onContentChange("v1")
            advanceTimeBy(100)
            vm.onContentChange("v2")
            advanceTimeBy(100)
            vm.onContentChange("v3")

            advanceTimeBy(499)
            assertTrue(repo.updateCalls.isEmpty(), "Debounce should still be pending")

            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, repo.updateCalls.size, "Only one updateNote for all rapid changes")
            assertEquals("v3", repo.updateCalls.first().content)
        }

    @Test
    fun `flush cancels debounce and persists immediately`() = runTest(dispatcher.scheduler) {
        val note = sampleNote("note-5", content = "Body")
        val repo = FakeEditorRepository(notes = listOf(note))
        val vm = repo.createViewModel(noteId = "note-5")
        runCurrent()

        vm.onContentChange("Updated content")
        advanceTimeBy(100)
        assertTrue(repo.updateCalls.isEmpty())

        vm.flush()
        runCurrent()

        assertEquals(1, repo.updateCalls.size, "flush should persist immediately")
        assertEquals("Updated content", repo.updateCalls.first().content)

        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, repo.updateCalls.size, "No additional save after flush cancels debounce")
    }

    @Test
    fun `flush with blank content saves a blank-draft note`() =
        runTest(dispatcher.scheduler) {
            val note = sampleNote("note-5b", content = "Also clear")
            val repo = FakeEditorRepository(notes = listOf(note))
            val vm = repo.createViewModel(noteId = "note-5b")
            runCurrent()

            vm.onContentChange("")
            vm.flush()
            runCurrent()

            assertTrue(repo.updateCalls.isNotEmpty(), "flush should have called updateNote")
            val saved = repo.updateCalls.last()
            assertTrue(saved.isBlankDraft(), "Saved note should be a blank draft (repo handles deletion)")
        }

    @Test
    fun `availableTags is all note tags minus loaded note tags`() =
        runTest(dispatcher.scheduler) {
            val loadedNote = sampleNote("note-6", tags = listOf("kotlin", "android"))
            val otherNote1 = sampleNote("other-1", tags = listOf("kotlin", "compose"))
            val otherNote2 = sampleNote("other-2", tags = listOf("hilt", "android"))
            val repo = FakeEditorRepository(notes = listOf(loadedNote, otherNote1, otherNote2))
            val vm = repo.createViewModel(noteId = "note-6")

            runCurrent()

            val available = vm.state.value.availableTags
            assertFalse("kotlin" in available, "kotlin is on loaded note, should not be available")
            assertFalse("android" in available, "android is on loaded note, should not be available")
            assertTrue("compose" in available, "compose should be available")
            assertTrue("hilt" in available, "hilt should be available")
        }

    @Test
    fun `delete cancels pending autosave and deletes the note without updateNote`() =
        runTest(dispatcher.scheduler) {
            val note = sampleNote("note-7", content = "Body")
            val repo = FakeEditorRepository(notes = listOf(note))
            val vm = repo.createViewModel(noteId = "note-7")
            runCurrent()

            vm.onContentChange("edited")
            advanceTimeBy(100)
            assertTrue(repo.updateCalls.isEmpty())

            vm.delete()
            runCurrent()

            assertEquals(listOf(NoteId("note-7")), repo.deleteCalls)

            advanceTimeBy(500)
            runCurrent()
            assertTrue(repo.updateCalls.isEmpty(), "delete must cancel the pending autosave")
        }

    // --- Sharing (Phase 4) ---

    @Test
    fun `members flow derives isShared and myRole for the current user`() =
        runTest(dispatcher.scheduler) {
            val note = sampleNote("s-1")
            val repo = FakeEditorRepository(notes = listOf(note))
            repo.membersFlow.value = listOf(
                member("me", ShareRole.OWNER),
                member("bob", ShareRole.EDITOR),
            )
            val vm = repo.createViewModel(noteId = "s-1", currentUser = "me")
            runCurrent()

            val state = vm.state.value
            assertTrue(state.isShared, "note with members is shared")
            assertEquals(ShareRole.OWNER, state.myRole)
            assertTrue(state.canManageAccess)
            assertFalse(state.canLeave, "owner cannot leave")
            assertFalse(state.isReadOnly)
        }

    @Test
    fun `viewer role makes the editor read-only`() = runTest(dispatcher.scheduler) {
        val note = sampleNote("s-2")
        val repo = FakeEditorRepository(notes = listOf(note))
        repo.membersFlow.value = listOf(
            member("alice", ShareRole.OWNER),
            member("me", ShareRole.VIEWER),
        )
        val vm = repo.createViewModel(noteId = "s-2", currentUser = "me")
        runCurrent()

        val state = vm.state.value
        assertEquals(ShareRole.VIEWER, state.myRole)
        assertTrue(state.isReadOnly, "viewer sees a read-only editor")
        assertFalse(state.canEditContent)
        assertTrue(state.canLeave, "viewer may leave")
        assertFalse(state.canManageAccess)
        assertFalse(state.canDeleteForAll)
    }

    @Test
    fun `private note owned by current user can be made shared`() =
        runTest(dispatcher.scheduler) {
            val note = sampleNote("p-1", owner = "me")
            val repo = FakeEditorRepository(notes = listOf(note))
            val vm = repo.createViewModel(noteId = "p-1", currentUser = "me")
            runCurrent()

            val state = vm.state.value
            assertFalse(state.isShared)
            assertTrue(state.isOwner)
            assertTrue(state.canMakeShared)
        }

    @Test
    fun `makeShared shares the note and opens the members sheet`() =
        runTest(dispatcher.scheduler) {
            val note = sampleNote("p-2", owner = "me")
            val repo = FakeEditorRepository(notes = listOf(note))
            val vm = repo.createViewModel(noteId = "p-2", currentUser = "me")
            runCurrent()

            vm.makeShared()
            runCurrent()

            assertEquals(listOf(NoteId("p-2")), repo.shareCalls)
            assertTrue(vm.state.value.membersSheetVisible)
        }

    @Test
    fun `invite maps Added result to Added state`() = runTest(dispatcher.scheduler) {
        val state = inviteWith(InviteResult.Added)
        assertEquals(InviteUiState.Added, state.inviteState)
    }

    @Test
    fun `invite maps UserNotFound result to UserNotFound state`() =
        runTest(dispatcher.scheduler) {
            val state = inviteWith(InviteResult.UserNotFound)
            assertEquals(InviteUiState.UserNotFound, state.inviteState)
        }

    @Test
    fun `invite maps AlreadyMember result to AlreadyMember state`() =
        runTest(dispatcher.scheduler) {
            val state = inviteWith(InviteResult.AlreadyMember)
            assertEquals(InviteUiState.AlreadyMember, state.inviteState)
        }

    @Test
    fun `invite failure surfaces Error state`() = runTest(dispatcher.scheduler) {
        val note = sharedNote("i-err", ShareRole.OWNER, owner = "me")
        val repo = FakeEditorRepository(notes = listOf(note))
        repo.membersFlow.value = listOf(member("me", ShareRole.OWNER))
        repo.inviteResult = Result.failure(IllegalStateException("offline"))
        val vm = repo.createViewModel(noteId = "i-err", currentUser = "me")
        runCurrent()

        vm.invite("x@y.z", ShareRole.EDITOR)
        runCurrent()

        assertEquals(InviteUiState.Error, vm.state.value.inviteState)
    }

    @Test
    fun `onInviteInputChanged resets a settled invite result to Idle`() =
        runTest(dispatcher.scheduler) {
            val note = sharedNote("i-reset", ShareRole.OWNER, owner = "me")
            val repo = FakeEditorRepository(notes = listOf(note))
            repo.membersFlow.value = listOf(member("me", ShareRole.OWNER))
            repo.inviteResult = Result.success(InviteResult.UserNotFound)
            val vm = repo.createViewModel(noteId = "i-reset", currentUser = "me")
            runCurrent()

            vm.invite("ghost@x.z", ShareRole.EDITOR)
            runCurrent()
            assertEquals(InviteUiState.UserNotFound, vm.state.value.inviteState)

            vm.onInviteInputChanged()
            assertEquals(InviteUiState.Idle, vm.state.value.inviteState)
        }

    @Test
    fun `changeRole and removeMember delegate to the repository`() =
        runTest(dispatcher.scheduler) {
            val note = sharedNote("m-1", ShareRole.OWNER, owner = "me")
            val repo = FakeEditorRepository(notes = listOf(note))
            repo.membersFlow.value = listOf(
                member("me", ShareRole.OWNER),
                member("bob", ShareRole.VIEWER),
            )
            val vm = repo.createViewModel(noteId = "m-1", currentUser = "me")
            runCurrent()

            vm.changeRole(UserId("bob"), ShareRole.EDITOR)
            vm.removeMember(UserId("bob"))
            runCurrent()

            assertEquals(listOf(UserId("bob") to ShareRole.EDITOR), repo.roleCalls)
            assertEquals(listOf(UserId("bob")), repo.removeCalls)
        }

    @Test
    fun `leave delegates to the repository`() = runTest(dispatcher.scheduler) {
        val note = sharedNote("l-1", ShareRole.EDITOR)
        val repo = FakeEditorRepository(notes = listOf(note))
        repo.membersFlow.value = listOf(
            member("alice", ShareRole.OWNER),
            member("me", ShareRole.EDITOR),
        )
        val vm = repo.createViewModel(noteId = "l-1", currentUser = "me")
        runCurrent()

        vm.leave()
        runCurrent()

        assertEquals(listOf(NoteId("l-1")), repo.leaveCalls)
    }

    @Test
    fun `unshare returns the note to private and closes the sheet`() =
        runTest(dispatcher.scheduler) {
            val note = sharedNote("u-1", ShareRole.OWNER, owner = "me")
            val repo = FakeEditorRepository(notes = listOf(note))
            repo.membersFlow.value = listOf(member("me", ShareRole.OWNER))
            val vm = repo.createViewModel(noteId = "u-1", currentUser = "me")
            runCurrent()
            vm.openMembersSheet()

            vm.unshare()
            runCurrent()

            assertEquals(listOf(NoteId("u-1")), repo.unshareCalls)
            val state = vm.state.value
            assertFalse(state.isShared)
            assertFalse(state.membersSheetVisible)
            assertEquals(null, state.myRole)
        }

    private fun kotlinx.coroutines.test.TestScope.inviteWith(result: InviteResult): NoteEditorState {
        val note = sharedNote("inv", ShareRole.OWNER, owner = "me")
        val repo = FakeEditorRepository(notes = listOf(note))
        repo.membersFlow.value = listOf(member("me", ShareRole.OWNER))
        repo.inviteResult = Result.success(result)
        val vm = repo.createViewModel(noteId = "inv", currentUser = "me")
        runCurrent()
        vm.invite("someone@example.com", ShareRole.EDITOR)
        runCurrent()
        return vm.state.value
    }
}

private fun member(uid: String, role: ShareRole): ShareMember =
    ShareMember(
        uid = UserId(uid),
        displayName = uid.replaceFirstChar { it.uppercase() },
        email = "$uid@example.com",
        photoUrl = null,
        role = role,
        joinedAt = 1L,
    )

private class FakeEditorRepository(
    notes: List<Note> = emptyList(),
) : NotesRepository {

    private val notesFlow = MutableStateFlow(notes)
    val updateCalls = mutableListOf<Note>()
    val deleteCalls = mutableListOf<NoteId>()

    // Share collaborators recorded/configured for Phase 4 tests.
    val membersFlow = MutableStateFlow<List<ShareMember>>(emptyList())
    val shareCalls = mutableListOf<NoteId>()
    val unshareCalls = mutableListOf<NoteId>()
    val leaveCalls = mutableListOf<NoteId>()
    val roleCalls = mutableListOf<Pair<UserId, ShareRole>>()
    val removeCalls = mutableListOf<UserId>()
    var inviteResult: Result<InviteResult> = Result.success(InviteResult.Added)

    override fun observeNotes(): Flow<List<Note>> = notesFlow
    override fun observeNote(id: NoteId): Flow<Note?> =
        flowOf(notesFlow.value.firstOrNull { it.id == id })

    override fun searchNotes(query: String): Flow<List<Note>> = flowOf(emptyList())
    override suspend fun getNote(id: NoteId): Note? = notesFlow.value.firstOrNull { it.id == id }
    override suspend fun createNote(ownerId: UserId?): Note = sampleNote("created")
    override suspend fun updateNote(note: Note): Note {
        updateCalls += note
        val updated = notesFlow.value.map { if (it.id == note.id) note else it }
        notesFlow.value = updated
        return note
    }
    override suspend fun deleteNote(id: NoteId) {
        deleteCalls += id
    }
    override suspend fun permanentlyDeleteNote(id: NoteId) {
        deleteCalls += id
    }
    override suspend fun restoreNote(id: NoteId) {}
    override suspend fun togglePin(id: NoteId) {}
    override fun observeArchivedNotes(): Flow<List<Note>> = flowOf(emptyList())
    override fun observeDeletedNotes(): Flow<List<Note>> = flowOf(emptyList())
    override suspend fun setArchived(id: NoteId, archived: Boolean) {}
    override suspend fun shareNote(id: NoteId): Result<Unit> {
        shareCalls += id
        return Result.success(Unit)
    }
    override suspend fun inviteByEmail(
        id: NoteId,
        email: String,
        role: ShareRole,
    ): Result<InviteResult> = inviteResult
    override suspend fun changeRole(
        id: NoteId,
        uid: UserId,
        role: ShareRole,
    ): Result<Unit> {
        roleCalls += uid to role
        return Result.success(Unit)
    }
    override suspend fun removeMember(id: NoteId, uid: UserId): Result<Unit> {
        removeCalls += uid
        return Result.success(Unit)
    }
    override suspend fun leaveSharedNote(id: NoteId): Result<Unit> {
        leaveCalls += id
        return Result.success(Unit)
    }
    override suspend fun unshareNote(id: NoteId): Result<Unit> {
        unshareCalls += id
        return Result.success(Unit)
    }
    override fun observeMembers(id: NoteId): Flow<List<ShareMember>> = membersFlow

    fun createViewModel(noteId: String, currentUser: String? = null): NoteEditorViewModel {
        val savedState = SavedStateHandle(mapOf("noteId" to noteId))
        val auth = FakeAuthRepository(currentUser?.let(::UserId))
        return NoteEditorViewModel(
            getNoteUseCase = GetNoteUseCase(this),
            updateNoteUseCase = UpdateNoteUseCase(this),
            observeNotesUseCase = ObserveNotesUseCase(this),
            deleteNoteUseCase = DeleteNoteUseCase(this),
            observeMembersUseCase = ObserveMembersUseCase(this),
            shareNoteUseCase = ShareNoteUseCase(this, auth),
            inviteMemberUseCase = InviteMemberUseCase(this),
            changeMemberRoleUseCase = ChangeMemberRoleUseCase(this),
            removeMemberUseCase = RemoveMemberUseCase(this),
            leaveSharedNoteUseCase = LeaveSharedNoteUseCase(this),
            unshareNoteUseCase = UnshareNoteUseCase(this),
            authRepository = auth,
            savedStateHandle = savedState,
        )
    }
}

private class FakeAuthRepository(user: UserId?) : AuthRepository {
    override val currentUser: StateFlow<UserId?> = MutableStateFlow(user)
    override val currentProfile: StateFlow<UserProfile?> = MutableStateFlow(null)
    override suspend fun signIn(): Result<UserId> = Result.failure(UnsupportedOperationException())
    override suspend fun signOut() {}
}

private fun sampleNote(
    id: String,
    content: String = "Body",
    pinned: Boolean = false,
    tags: List<String> = emptyList(),
    owner: String? = null,
    shareState: ShareState = ShareState.Private,
): Note =
    Note(
        id = NoteId(id),
        title = "",
        content = content,
        tags = tags.map(::Tag),
        isPinned = pinned,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncedAt = null,
        ownerId = owner?.let(::UserId),
        shareState = shareState,
    )

/** A shared note whose `shareState` carries the current user's role, mirroring what `getNote` returns. */
private fun sharedNote(id: String, myRole: ShareRole, owner: String? = null): Note =
    sampleNote(
        id = id,
        owner = owner,
        shareState = ShareState.Shared(members = emptyList(), myRole = myRole),
    )
