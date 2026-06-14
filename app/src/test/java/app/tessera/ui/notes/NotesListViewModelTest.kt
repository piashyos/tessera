package app.tessera.ui.notes

import app.cash.turbine.test
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.NotesRepository
import app.tessera.domain.usecase.CreateNoteUseCase
import app.tessera.domain.usecase.DeleteNoteUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
import app.tessera.domain.usecase.RestoreNoteUseCase
import app.tessera.domain.usecase.SearchNotesUseCase
import app.tessera.domain.usecase.TogglePinUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesListViewModelTest {
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
    fun `state transitions from loading to content`() =
        runTest(dispatcher.scheduler) {
            val repository = FakeNotesRepository(initialNotes = listOf(sampleNote("note-1")))
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                assertEquals(NotesListState.Loading, awaitItem())

                val content = awaitItem() as NotesListState.Content
                assertEquals("note-1", content.notes.single().id.value)
                assertEquals(0, content.pinnedCount)
                assertEquals("", content.query)
            }
        }

    @Test
    fun `state transitions from loading to empty when repository has no notes`() =
        runTest(dispatcher.scheduler) {
            val repository = FakeNotesRepository(initialNotes = emptyList())
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                assertEquals(NotesListState.Loading, awaitItem())

                val empty = awaitItem() as NotesListState.Empty
                assertEquals("", empty.query)
                assertTrue(empty.availableTags.isEmpty())
            }
        }

    @Test
    fun `query updates immediately while search waits for debounce`() =
        runTest(dispatcher.scheduler) {
            val repository =
                FakeNotesRepository(
                    initialNotes = listOf(sampleNote("local-1", title = "Draft")),
                    searchResults =
                        mapOf(
                            "kotlin" to listOf(sampleNote("search-1", title = "Kotlin tips", tags = listOf("kotlin"))),
                        ),
                )
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                awaitItem()
                awaitItem()

                viewModel.onQueryChange("kotlin")

                val optimistic = awaitItem() as NotesListState.Content
                assertEquals("kotlin", optimistic.query)
                assertEquals("local-1", optimistic.notes.single().id.value)
                assertTrue(repository.searchQueries.isEmpty())

                advanceTimeBy(299)
                assertTrue(repository.searchQueries.isEmpty())

                advanceTimeBy(1)

                val searched = awaitItem() as NotesListState.Content
                assertEquals(listOf("kotlin"), repository.searchQueries)
                assertEquals("search-1", searched.notes.single().id.value)
                assertEquals(listOf("kotlin"), searched.availableTags)
            }
        }

    @Test
    fun `clearing query returns to observe flow without debounce`() =
        runTest(dispatcher.scheduler) {
            val repository =
                FakeNotesRepository(
                    initialNotes = listOf(sampleNote("local-1", title = "Local")),
                    searchResults = mapOf("jetpack" to listOf(sampleNote("search-1", title = "Jetpack"))),
                )
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                awaitItem()
                awaitItem()

                viewModel.onQueryChange("jetpack")
                awaitItem()
                advanceTimeBy(300)
                awaitItem()

                viewModel.onQueryChange("")

                val firstCleared = awaitItem() as NotesListState.Content
                val cleared =
                    if (firstCleared.notes.single().id.value == "local-1") {
                        firstCleared
                    } else {
                        awaitItem() as NotesListState.Content
                    }
                assertEquals("", cleared.query)
                assertEquals("local-1", cleared.notes.single().id.value)
                assertTrue(repository.observeSubscriptions >= 2)
            }
        }
}

private class FakeNotesRepository(
    initialNotes: List<Note>,
    private val searchResults: Map<String, List<Note>> = emptyMap(),
) : NotesRepository {
    private val notesFlow = MutableStateFlow(initialNotes)

    val searchQueries = mutableListOf<String>()
    var observeSubscriptions = 0

    override fun observeNotes(): Flow<List<Note>> {
        observeSubscriptions += 1
        return notesFlow
    }

    override fun observeNote(id: NoteId): Flow<Note?> = flowOf(notesFlow.value.firstOrNull { it.id == id })

    override fun searchNotes(query: String): Flow<List<Note>> {
        searchQueries += query
        return flowOf(searchResults[query].orEmpty())
    }

    override suspend fun getNote(id: NoteId): Note? = notesFlow.value.firstOrNull { it.id == id }

    override suspend fun createNote(ownerId: UserId?): Note = sampleNote("created")

    override suspend fun updateNote(note: Note): Note = note

    override suspend fun deleteNote(id: NoteId) {}

    override suspend fun restoreNote(id: NoteId) {}

    override suspend fun togglePin(id: NoteId) {}

    fun createViewModel(): NotesListViewModel =
        NotesListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(this),
            searchNotesUseCase = SearchNotesUseCase(this),
            createNoteUseCase = CreateNoteUseCase(this),
            deleteNoteUseCase = DeleteNoteUseCase(this),
            restoreNoteUseCase = RestoreNoteUseCase(this),
            togglePinUseCase = TogglePinUseCase(this),
        )
}

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
