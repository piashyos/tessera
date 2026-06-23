package app.tessera.ui.editor

import androidx.lifecycle.SavedStateHandle
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.NotesRepository
import app.tessera.domain.usecase.DeleteNoteUseCase
import app.tessera.domain.usecase.GetNoteUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
import app.tessera.domain.usecase.UpdateNoteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
}

private class FakeEditorRepository(
    notes: List<Note> = emptyList(),
) : NotesRepository {

    private val notesFlow = MutableStateFlow(notes)
    val updateCalls = mutableListOf<Note>()
    val deleteCalls = mutableListOf<NoteId>()

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
    override suspend fun shareNote(id: NoteId): Result<Unit> = Result.success(Unit)
    override suspend fun inviteByEmail(
        id: NoteId,
        email: String,
        role: app.tessera.domain.model.ShareRole,
    ): Result<app.tessera.domain.model.InviteResult> =
        Result.success(app.tessera.domain.model.InviteResult.Added)
    override suspend fun changeRole(
        id: NoteId,
        uid: app.tessera.domain.model.UserId,
        role: app.tessera.domain.model.ShareRole,
    ): Result<Unit> = Result.success(Unit)
    override suspend fun removeMember(id: NoteId, uid: app.tessera.domain.model.UserId): Result<Unit> =
        Result.success(Unit)
    override suspend fun leaveSharedNote(id: NoteId): Result<Unit> = Result.success(Unit)
    override suspend fun unshareNote(id: NoteId): Result<Unit> = Result.success(Unit)
    override fun observeMembers(id: NoteId): Flow<List<app.tessera.domain.model.ShareMember>> =
        flowOf(emptyList())

    fun createViewModel(noteId: String): NoteEditorViewModel {
        val savedState = SavedStateHandle(mapOf("noteId" to noteId))
        return NoteEditorViewModel(
            getNoteUseCase = GetNoteUseCase(this),
            updateNoteUseCase = UpdateNoteUseCase(this),
            observeNotesUseCase = ObserveNotesUseCase(this),
            deleteNoteUseCase = DeleteNoteUseCase(this),
            savedStateHandle = savedState,
        )
    }
}

private fun sampleNote(
    id: String,
    content: String = "Body",
    pinned: Boolean = false,
    tags: List<String> = emptyList(),
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
        ownerId = null,
    )
