package app.tessera.ui.editor

import androidx.lifecycle.SavedStateHandle
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.NotesRepository
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

    // -------------------------------------------------------------------------
    // 1. VM loads the note from SavedStateHandle into state
    // -------------------------------------------------------------------------

    @Test
    fun `loads note from savedStateHandle into state`() = runTest(dispatcher.scheduler) {
        val note = sampleNote("note-1", title = "Hello", content = "World", tags = listOf("kotlin"))
        val repo = FakeEditorRepository(notes = listOf(note))
        val vm = repo.createViewModel(noteId = "note-1")

        runCurrent()

        val state = vm.state.value
        assertFalse(state.isLoading, "isLoading should be false after load")
        assertEquals("Hello", state.title)
        assertEquals("World", state.content)
        assertEquals(listOf("kotlin"), state.tags)
        assertTrue(state.isPinned == note.isPinned)
    }

    // -------------------------------------------------------------------------
    // 2. Editing title then content schedules autosave with 500 ms debounce
    // -------------------------------------------------------------------------

    @Test
    fun `no updateNote before 499ms, exactly one after 500ms with latest values`() =
        runTest(dispatcher.scheduler) {
            val note = sampleNote("note-2", title = "Old", content = "Old")
            val repo = FakeEditorRepository(notes = listOf(note))
            val vm = repo.createViewModel(noteId = "note-2")
            runCurrent() // finish loading

            vm.onTitleChange("New Title")
            vm.onContentChange("New Content")

            advanceTimeBy(499)
            assertTrue(repo.updateCalls.isEmpty(), "No updateNote expected before debounce fires")

            advanceTimeBy(1) // cross the 500 ms mark
            runCurrent()
            assertEquals(1, repo.updateCalls.size, "Exactly one updateNote expected after debounce")
            val saved = repo.updateCalls.first()
            assertEquals("New Title", saved.title)
            assertEquals("New Content", saved.content)
        }

    // -------------------------------------------------------------------------
    // 3. Rapid onContentChange calls collapse to a single debounced updateNote
    // -------------------------------------------------------------------------

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

            // Still within debounce window after last change
            advanceTimeBy(499)
            assertTrue(repo.updateCalls.isEmpty(), "Debounce should still be pending")

            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, repo.updateCalls.size, "Only one updateNote for all rapid changes")
            assertEquals("v3", repo.updateCalls.first().content)
        }

    // -------------------------------------------------------------------------
    // 4. onToggleChecklist rewrites content and the rewritten value is persisted
    // -------------------------------------------------------------------------

    @Test
    fun `onToggleChecklist rewrites content and debounced save uses rewritten content`() =
        runTest(dispatcher.scheduler) {
            val checklistContent = "- [ ] item one\n- [x] item two"
            val note = sampleNote("note-4", content = checklistContent)
            val repo = FakeEditorRepository(notes = listOf(note))
            val vm = repo.createViewModel(noteId = "note-4")
            runCurrent()

            vm.onToggleChecklist(0) // toggle first line: unchecked -> checked

            val expectedContent = toggleChecklistLine(checklistContent, 0)
            assertEquals(expectedContent, vm.state.value.content, "State content should reflect toggle")

            advanceTimeBy(500)
            runCurrent()
            assertEquals(1, repo.updateCalls.size)
            assertEquals(expectedContent, repo.updateCalls.first().content)
        }

    // -------------------------------------------------------------------------
    // 5. flush() cancels pending debounce and persists immediately
    //    When title+content blank -> isBlankDraft() == true
    // -------------------------------------------------------------------------

    @Test
    fun `flush cancels debounce and persists immediately`() = runTest(dispatcher.scheduler) {
        val note = sampleNote("note-5", title = "Draft", content = "Body")
        val repo = FakeEditorRepository(notes = listOf(note))
        val vm = repo.createViewModel(noteId = "note-5")
        runCurrent()

        vm.onTitleChange("Updated")
        vm.onContentChange("Updated content")
        // Only 100 ms have passed — debounce not fired yet
        advanceTimeBy(100)
        assertTrue(repo.updateCalls.isEmpty())

        vm.flush()
        runCurrent()

        assertEquals(1, repo.updateCalls.size, "flush should persist immediately")
        assertEquals("Updated", repo.updateCalls.first().title)

        // No second save fires from the cancelled debounce
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, repo.updateCalls.size, "No additional save after flush cancels debounce")
    }

    @Test
    fun `flush with blank title and content saves a blank-draft note`() =
        runTest(dispatcher.scheduler) {
            val note = sampleNote("note-5b", title = "Will Clear", content = "Also clear")
            val repo = FakeEditorRepository(notes = listOf(note))
            val vm = repo.createViewModel(noteId = "note-5b")
            runCurrent()

            vm.onTitleChange("")
            vm.onContentChange("")
            vm.flush()
            runCurrent()

            assertTrue(repo.updateCalls.isNotEmpty(), "flush should have called updateNote")
            val saved = repo.updateCalls.last()
            assertTrue(saved.isBlankDraft(), "Saved note should be a blank draft (repo handles deletion)")
        }

    // -------------------------------------------------------------------------
    // 6. availableTags = distinct tags across all notes MINUS tags on loaded note
    // -------------------------------------------------------------------------

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
            // All distinct tags: kotlin, android, compose, hilt
            // Minus loaded note tags (kotlin, android) => compose, hilt
            assertFalse("kotlin" in available, "kotlin is on loaded note, should not be available")
            assertFalse("android" in available, "android is on loaded note, should not be available")
            assertTrue("compose" in available, "compose should be available")
            assertTrue("hilt" in available, "hilt should be available")
        }
}

// =============================================================================
// Fake repository
// =============================================================================

private class FakeEditorRepository(
    notes: List<Note> = emptyList(),
) : NotesRepository {

    private val notesFlow = MutableStateFlow(notes)
    val updateCalls = mutableListOf<Note>()

    override fun observeNotes(): Flow<List<Note>> = notesFlow
    override fun observeNote(id: NoteId): Flow<Note?> =
        flowOf(notesFlow.value.firstOrNull { it.id == id })

    override fun searchNotes(query: String): Flow<List<Note>> = flowOf(emptyList())
    override suspend fun getNote(id: NoteId): Note? = notesFlow.value.firstOrNull { it.id == id }
    override suspend fun createNote(ownerId: UserId?): Note = sampleNote("created")
    override suspend fun updateNote(note: Note): Note {
        updateCalls += note
        // Update the flow so observeNotes reflects the saved note
        val updated = notesFlow.value.map { if (it.id == note.id) note else it }
        notesFlow.value = updated
        return note
    }
    override suspend fun deleteNote(id: NoteId) {}
    override suspend fun restoreNote(id: NoteId) {}
    override suspend fun togglePin(id: NoteId) {}

    fun createViewModel(noteId: String): NoteEditorViewModel {
        val savedState = SavedStateHandle(mapOf("noteId" to noteId))
        return NoteEditorViewModel(
            getNoteUseCase = GetNoteUseCase(this),
            updateNoteUseCase = UpdateNoteUseCase(this),
            observeNotesUseCase = ObserveNotesUseCase(this),
            savedStateHandle = savedState,
        )
    }
}

// =============================================================================
// Helpers
// =============================================================================

private fun sampleNote(
    id: String,
    title: String = "Sample",
    content: String = "Body",
    pinned: Boolean = false,
    tags: List<String> = emptyList(),
): Note =
    Note(
        id = NoteId(id),
        title = title,
        content = content,
        tags = tags.map(::Tag),
        isPinned = pinned,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncedAt = null,
        ownerId = null,
    )
