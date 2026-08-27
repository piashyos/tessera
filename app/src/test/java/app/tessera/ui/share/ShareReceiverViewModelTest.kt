package app.tessera.ui.share

import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.NotesRepository
import app.tessera.domain.usecase.CreateNoteUseCase
import app.tessera.domain.usecase.GetNoteUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
import app.tessera.domain.usecase.UpdateNoteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareReceiverViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(repository: FakeShareNotesRepository) =
        ShareReceiverViewModel(
            createNote = CreateNoteUseCase(repository),
            updateNote = UpdateNoteUseCase(repository),
            getNote = GetNoteUseCase(repository),
            observeNotes = ObserveNotesUseCase(repository),
        )

    @Test
    fun `shared text is seeded once and later intents cannot overwrite edits`() {
        val viewModel = createViewModel(FakeShareNotesRepository())

        viewModel.setSharedText("first")
        viewModel.onTextChange("edited")
        viewModel.setSharedText("second")

        assertEquals("edited", viewModel.uiState.value.text)
    }

    @Test
    fun `save with default target creates a new note with text and tags`() =
        runTest(dispatcher.scheduler) {
            val repository = FakeShareNotesRepository()
            val viewModel = createViewModel(repository)

            viewModel.setSharedText("shared body")
            viewModel.addTag("#Ideas ")
            viewModel.save()
            advanceUntilIdle()

            val saved = repository.updatedNotes.single()
            assertEquals("shared body", saved.content)
            assertEquals(listOf("Ideas"), saved.tags.map(Tag::name))
            assertTrue(viewModel.uiState.value.saved)
        }

    @Test
    fun `save into existing note appends text and merges tags`() =
        runTest(dispatcher.scheduler) {
            val existing = sampleNote("n1", content = "old content\n", tags = listOf("work"))
            val repository = FakeShareNotesRepository(listOf(existing))
            val viewModel = createViewModel(repository)

            viewModel.setSharedText("shared body")
            viewModel.selectExistingNote(existing)
            viewModel.addTag("work") // duplicate of an existing tag
            viewModel.addTag("new")
            viewModel.save()
            advanceUntilIdle()

            val saved = repository.updatedNotes.single()
            assertEquals("n1", saved.id.value)
            assertEquals("old content\n\nshared body", saved.content)
            assertEquals(listOf("work", "new"), saved.tags.map(Tag::name))
        }

    @Test
    fun `blank text cannot be saved`() =
        runTest(dispatcher.scheduler) {
            val repository = FakeShareNotesRepository()
            val viewModel = createViewModel(repository)

            viewModel.setSharedText("   ")
            assertFalse(viewModel.uiState.value.canSave)
            viewModel.save()
            advanceUntilIdle()

            assertTrue(repository.updatedNotes.isEmpty())
            assertFalse(viewModel.uiState.value.saved)
        }

    @Test
    fun `tags dedupe case-insensitively and strip hash prefix`() {
        val viewModel = createViewModel(FakeShareNotesRepository())

        viewModel.addTag("Home")
        viewModel.addTag("#home")
        viewModel.addTag("  ")

        assertEquals(listOf("Home"), viewModel.uiState.value.tags)
    }

    private fun sampleNote(
        id: String,
        content: String = "",
        tags: List<String> = emptyList(),
    ) = Note(
        id = NoteId(id),
        title = "title-$id",
        content = content,
        tags = tags.map(::Tag),
        isPinned = false,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        syncedAt = null,
        ownerId = null,
    )
}

/** Minimal in-memory [NotesRepository] for the share flow; share-access methods are unused. */
private class FakeShareNotesRepository(
    initialNotes: List<Note> = emptyList(),
) : NotesRepository {
    private val notes = MutableStateFlow(initialNotes)
    val updatedNotes = mutableListOf<Note>()
    private var nextId = 0

    override fun observeNotes(): Flow<List<Note>> = notes

    override fun observeNote(id: NoteId): Flow<Note?> = flowOf(find(id))

    override fun searchNotes(query: String): Flow<List<Note>> = notes

    override fun observeArchivedNotes(): Flow<List<Note>> = flowOf(emptyList())

    override fun observeDeletedNotes(): Flow<List<Note>> = flowOf(emptyList())

    override suspend fun getNote(id: NoteId): Note? = find(id)

    override suspend fun createNote(ownerId: UserId?): Note =
        Note(
            id = NoteId("created-${nextId++}"),
            title = "",
            content = "",
            tags = emptyList(),
            isPinned = false,
            createdAt = 0L,
            updatedAt = 0L,
            deletedAt = null,
            syncedAt = null,
            ownerId = ownerId,
        ).also { created -> notes.value = notes.value + created }

    override suspend fun updateNote(note: Note): Note {
        updatedNotes += note
        notes.value = notes.value.map { if (it.id == note.id) note else it }
        return note
    }

    override suspend fun deleteNote(id: NoteId) = error("unused")

    override suspend fun permanentlyDeleteNote(id: NoteId) = error("unused")

    override suspend fun restoreNote(id: NoteId) = error("unused")

    override suspend fun togglePin(id: NoteId) = error("unused")

    override suspend fun setArchived(id: NoteId, archived: Boolean) = error("unused")

    override suspend fun shareNote(id: NoteId): Result<Unit> = error("unused")

    override suspend fun inviteByEmail(id: NoteId, email: String, role: ShareRole): Result<InviteResult> =
        error("unused")

    override suspend fun changeRole(id: NoteId, uid: UserId, role: ShareRole): Result<Unit> = error("unused")

    override suspend fun removeMember(id: NoteId, uid: UserId): Result<Unit> = error("unused")

    override suspend fun leaveSharedNote(id: NoteId): Result<Unit> = error("unused")

    override suspend fun unshareNote(id: NoteId): Result<Unit> = error("unused")

    override fun observeMembers(id: NoteId): Flow<List<ShareMember>> = flowOf(emptyList())

    private fun find(id: NoteId): Note? = notes.value.firstOrNull { it.id == id }
}
