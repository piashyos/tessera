package app.tessera.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tessera.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reproduces the post-`clearAllTables()` insert path. The `notes` table has an external-content
 * FTS4 mirror (`notes_fts`); clearing every table and then inserting must not corrupt the FTS index
 * or crash. This guards both the account-isolation wipe (sign-in) and the sign-out cache wipe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotesDatabaseClearTest {

    private lateinit var db: NotesDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotesDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun note(id: String, title: String, content: String) =
        NoteEntity(
            id = id,
            title = title,
            content = content,
            isPinned = false,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
            syncedAt = null,
            ownerId = null,
        )

    @Test
    fun `insert after clearAllTables keeps the note and FTS searchable`() = runTest {
        val dao = db.noteDao()

        dao.upsert(note("a", "First", "hello world"))
        assertEquals(1, dao.observeActive().first().size)

        // The destructive wipe used on sign-out and account isolation.
        db.clearAllTables()
        assertEquals(0, dao.observeActive().first().size)

        // Creating a note immediately after the wipe — the exact step that crashes for the user.
        dao.upsert(note("b", "Second", "searchable body"))
        assertEquals(1, dao.observeActive().first().size)

        // FTS mirror must still be consistent with the content table.
        val hits = dao.search("searchable").first()
        assertEquals(listOf("b"), hits.map { it.id })
    }
}
