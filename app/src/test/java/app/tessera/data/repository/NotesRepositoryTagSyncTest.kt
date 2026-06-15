package app.tessera.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import app.tessera.core.util.Clock
import app.tessera.data.local.NotesDatabase
import app.tessera.data.mapper.NoteMapper
import app.tessera.domain.model.Tag
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotesRepositoryTagSyncTest {
    private lateinit var database: NotesDatabase
    private lateinit var repository: NotesRepositoryImpl

    private class IncrementingClock : Clock {
        private var current = 1_000L

        override fun nowMs(): Long {
            current += 1
            return current
        }
    }

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                NotesDatabase::class.java,
            ).allowMainThreadQueries().build()
        repository =
            NotesRepositoryImpl(
                database.noteDao(),
                database.tagDao(),
                NoteMapper(),
                IncrementingClock(),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observeNotes re-emits when a tag is added to an existing note`() =
        runTest {
            val created = repository.createNote()
            val note =
                repository.updateNote(
                    created.copy(title = "Title", content = "Body"),
                )

            repository.observeNotes().test {
                val initial = awaitItem()
                assertEquals(1, initial.size)
                assertTrue(initial.single().tags.isEmpty())

                repository.updateNote(note.copy(tags = listOf(Tag("work"))))

                val updated = awaitItem()
                assertEquals(listOf(Tag("work")), updated.single().tags)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
