package app.tessera.domain.usecase

import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotesUseCasesTest {
    @Test
    fun `observe use case forwards repository flow`() =
        runTest {
            val repository = FakeNotesRepository()
            val useCase = ObserveNotesUseCase(repository)

            assertEquals(repository.notes.first(), useCase().first())
        }

    @Test
    fun `get use case forwards id`() =
        runTest {
            val repository = FakeNotesRepository()
            val useCase = GetNoteUseCase(repository)

            val result = useCase(repository.sampleNote.id)

            assertEquals(repository.sampleNote, result)
            assertEquals(repository.sampleNote.id, repository.lastRequestedId)
        }

    @Test
    fun `create use case forwards owner id`() =
        runTest {
            val repository = FakeNotesRepository()
            val useCase = CreateNoteUseCase(repository)
            val ownerId = UserId("user-1")

            val result = useCase(ownerId)

            assertEquals(repository.createdNote, result)
            assertEquals(ownerId, repository.lastCreateOwnerId)
        }

    @Test
    fun `update use case forwards note`() =
        runTest {
            val repository = FakeNotesRepository()
            val useCase = UpdateNoteUseCase(repository)

            val result = useCase(repository.sampleNote)

            assertEquals(repository.sampleNote, result)
            assertEquals(repository.sampleNote, repository.lastUpdatedNote)
        }

    @Test
    fun `delete use case forwards note id`() =
        runTest {
            val repository = FakeNotesRepository()
            val useCase = DeleteNoteUseCase(repository)

            useCase(repository.sampleNote.id)

            assertEquals(repository.sampleNote.id, repository.lastDeletedId)
        }

    @Test
    fun `restore use case forwards note id`() =
        runTest {
            val repository = FakeNotesRepository()
            val useCase = RestoreNoteUseCase(repository)

            useCase(repository.sampleNote.id)

            assertEquals(repository.sampleNote.id, repository.lastRestoredId)
        }

    @Test
    fun `toggle pin use case forwards note id`() =
        runTest {
            val repository = FakeNotesRepository()
            val useCase = TogglePinUseCase(repository)

            useCase(repository.sampleNote.id)

            assertEquals(repository.sampleNote.id, repository.lastToggledId)
        }

    @Test
    fun `search use case forwards query`() =
        runTest {
            val repository = FakeNotesRepository()
            val useCase = SearchNotesUseCase(repository)

            val result = useCase("kotlin").first()

            assertEquals(repository.searchResults.first(), result)
            assertEquals("kotlin", repository.lastSearchQuery)
        }
}

private class FakeNotesRepository : NotesRepository {
    val sampleNote =
        Note(
            id = NoteId("note-1"),
            title = "Sample",
            content = "Body",
            tags = listOf(Tag("android")),
            isPinned = false,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
            syncedAt = null,
            ownerId = null,
        )
    val createdNote = sampleNote.copy(id = NoteId("created"))
    val notes = MutableStateFlow(listOf(sampleNote))
    val searchResults = MutableStateFlow(listOf(sampleNote.copy(title = "Kotlin")))

    var lastRequestedId: NoteId? = null
    var lastCreateOwnerId: UserId? = null
    var lastUpdatedNote: Note? = null
    var lastDeletedId: NoteId? = null
    var lastRestoredId: NoteId? = null
    var lastToggledId: NoteId? = null
    var lastSearchQuery: String? = null

    override fun observeNotes(): Flow<List<Note>> = notes

    override fun observeNote(id: NoteId): Flow<Note?> = MutableStateFlow(sampleNote.takeIf { it.id == id })

    override fun searchNotes(query: String): Flow<List<Note>> {
        lastSearchQuery = query
        return searchResults
    }

    override suspend fun getNote(id: NoteId): Note? {
        lastRequestedId = id
        return sampleNote.takeIf { it.id == id }
    }

    override suspend fun createNote(ownerId: UserId?): Note {
        lastCreateOwnerId = ownerId
        return createdNote
    }

    override suspend fun updateNote(note: Note): Note {
        lastUpdatedNote = note
        return note
    }

    override suspend fun deleteNote(id: NoteId) {
        lastDeletedId = id
    }

    override suspend fun restoreNote(id: NoteId) {
        lastRestoredId = id
    }

    override suspend fun togglePin(id: NoteId) {
        lastToggledId = id
    }
}
