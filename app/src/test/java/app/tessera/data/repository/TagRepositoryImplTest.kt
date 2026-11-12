package app.tessera.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tessera.core.util.Clock
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity
import app.tessera.domain.model.TagAppearance
import app.tessera.domain.repository.SyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagRepositoryImplTest {
    private lateinit var db: NotesDatabase
    private lateinit var repo: TagRepositoryImpl
    private var syncRequests = 0
    private var now = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotesDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = TagRepositoryImpl(
            db.tagDao(),
            object : Clock { override fun nowMs() = ++now },
            object : SyncScheduler { override fun requestSync() { syncRequests++ } },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun row(name: String) = db.tagDao().findByNames(listOf(name)).singleOrNull()

    @Test
    fun `setAppearance creates the row for a tag that has none`() = runTest {
        repo.setAppearance("idea", "blue", "lucide:star")

        val tag = row("idea")!!
        assertEquals("blue", tag.color)
        assertEquals("lucide:star", tag.icon)
        assertTrue(tag.updatedAt > 0)
        assertNull(tag.syncedAt)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `setAppearance on an existing tag keeps its id and note links`() = runTest {
        db.tagDao().insertTags(listOf(TagEntity(id = "t1", name = "idea")))
        db.tagDao().linkTagsToNote(listOf(NoteTagCrossRef(noteId = "n1", tagId = "t1")))

        repo.setAppearance("idea", "red", null)

        assertEquals("t1", row("idea")!!.id)
        assertEquals(listOf("idea"), db.tagDao().tagsForNote("n1"))
    }

    @Test
    fun `reset writes nulls with a newer updatedAt and drops out of observeAppearances`() = runTest {
        repo.setAppearance("idea", "red", "emoji:💡")
        val first = row("idea")!!
        assertEquals(
            mapOf("idea" to TagAppearance("idea", "red", "emoji:💡")),
            repo.observeAppearances().first(),
        )

        repo.setAppearance("idea", null, null)

        val reset = row("idea")!!
        assertNull(reset.color)
        assertNull(reset.icon)
        assertTrue(reset.updatedAt > first.updatedAt)
        assertNull(reset.syncedAt)
        assertEquals(emptyMap<String, TagAppearance>(), repo.observeAppearances().first())
        assertEquals(2, syncRequests)
    }

    @Test
    fun `pending sync is only tags whose appearance was set locally`() = runTest {
        db.tagDao().insertTags(listOf(TagEntity(id = "t1", name = "plain")))
        repo.setAppearance("idea", "red", null)

        assertEquals(listOf("idea"), db.tagDao().getPendingSync().map { it.name })
    }

    @Test
    fun `markSynced marks only the version that was pushed`() = runTest {
        repo.setAppearance("idea", "red", null)
        val pushed = row("idea")!!
        repo.setAppearance("idea", "blue", null) // edited while the push was in flight

        db.tagDao().markSynced("idea", ts = 9_000L, updatedAt = pushed.updatedAt)

        assertNull(row("idea")!!.syncedAt)
        assertEquals(listOf("idea"), db.tagDao().getPendingSync().map { it.name })
    }

    @Test
    fun `applyRemote keeps the id and note links of an existing tag and marks it synced`() = runTest {
        db.tagDao().insertTags(listOf(TagEntity(id = "t1", name = "idea")))
        db.tagDao().linkTagsToNote(listOf(NoteTagCrossRef(noteId = "n1", tagId = "t1")))

        db.tagDao().applyRemote("idea", "green", "lucide:book", updatedAt = 500L, syncedAt = 600L)

        val tag = row("idea")!!
        assertEquals("t1", tag.id)
        assertEquals("green", tag.color)
        assertEquals(500L, tag.updatedAt)
        assertEquals(600L, tag.syncedAt)
        assertEquals(listOf("idea"), db.tagDao().tagsForNote("n1"))
    }

    @Test
    fun `applyRemote creates a missing tag`() = runTest {
        db.tagDao().applyRemote("new", "teal", null, updatedAt = 500L, syncedAt = 600L)

        assertEquals("teal", row("new")!!.color)
    }
}
