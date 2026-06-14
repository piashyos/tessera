package app.tessera.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.tessera.data.local.entity.NoteEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {
    private lateinit var database: NotesDatabase
    private val dao get() = database.noteDao()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun note(
        id: String,
        title: String = "title",
        content: String = "content",
        pinned: Boolean = false,
        ts: Long = 1L,
    ) = NoteEntity(
        id = id,
        title = title,
        content = content,
        isPinned = pinned,
        createdAt = ts,
        updatedAt = ts,
        deletedAt = null,
        syncedAt = null,
        ownerId = null,
    )

    @Test
    fun observeActive_returnsPinnedFirstThenByUpdatedAtDesc() = runTest {
        dao.upsert(note(id = "a", title = "A", pinned = false, ts = 10))
        dao.upsert(note(id = "b", title = "B", pinned = true, ts = 5))
        dao.upsert(note(id = "c", title = "C", pinned = false, ts = 20))

        dao.observeActive().test {
            assertEquals(listOf("b", "c", "a"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun softDelete_excludesFromObserveActive() = runTest {
        dao.upsert(note(id = "x"))
        dao.softDelete(id = "x", ts = 99)

        dao.observeActive().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        val raw = dao.getById("x")
        assertEquals(99L, raw?.deletedAt)
        assertNull(raw?.syncedAt)
    }

    @Test
    fun fts_searchMatchesTitleAndContent() = runTest {
        dao.upsert(note(id = "a", title = "Kotlin tips"))
        dao.upsert(note(id = "b", title = "Other", content = "Includes kotlin examples"))
        dao.upsert(note(id = "c", title = "Unrelated", content = "python"))

        dao.search("kotlin").test {
            assertEquals(setOf("a", "b"), awaitItem().map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun adoptLocalNotes_assignsOwnerIdAndMarksUnsynced() = runTest {
        dao.upsert(note(id = "a").copy(syncedAt = 1L))
        dao.upsert(note(id = "b").copy(ownerId = "other", syncedAt = 1L))

        dao.adoptLocalNotes("uid-1")

        val adopted = dao.getById("a")
        val untouched = dao.getById("b")
        assertEquals("uid-1", adopted?.ownerId)
        assertNull(adopted?.syncedAt)
        assertEquals("other", untouched?.ownerId)
    }

    @Test
    fun purgeOldTombstones_deletesEntriesOlderThanThreshold() = runTest {
        dao.upsert(note(id = "old").copy(deletedAt = 100))
        dao.upsert(note(id = "recent").copy(deletedAt = 500))

        val purged = dao.purgeOldTombstones(threshold = 200)

        assertEquals(1, purged)
        assertNull(dao.getById("old"))
        assertEquals("recent", dao.getById("recent")?.id)
    }
}
